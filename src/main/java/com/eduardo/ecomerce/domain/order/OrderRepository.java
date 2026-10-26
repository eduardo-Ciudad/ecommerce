package com.eduardo.ecomerce.domain.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id AND o.user.id = :userId")
    Optional<Order> findByIdAndUserIdForUpdate(@Param("id") UUID id, @Param("userId") UUID userId);

    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, LocalDateTime cutoff);

    // ---------- Dashboard do admin ----------

    long countByStatus(OrderStatus status);

    long countByBlingSyncStatus(BlingSyncStatus status);

    List<Order> findByStatusInAndPaidAtGreaterThanEqualAndPaidAtLessThan(
            Collection<OrderStatus> statuses, LocalDateTime from, LocalDateTime to);

    // ---------- Sync de pedidos site -> Bling ----------

    /** Pedido com tudo que o payload do Bling usa já carregado (o envio roda fora de transação). */
    @EntityGraph(attributePaths = {"user", "items", "items.variant", "items.variant.product"})
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findWithItemsById(@Param("id") UUID id);
    // Os @Modifying têm @Transactional próprio: chamados fora de transação lançariam
    // TransactionRequiredException (mesmo bug já visto no reconcileRemovedProducts).

    /**
     * Claim atômico do envio: só um processo (listener, scheduler ou reenvio do admin) consegue pegar o pedido.
     * Retorna 1 se pegou, 0 se o pedido não está elegível ou outro processo já está enviando.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Order o
               SET o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.PROCESSING,
                   o.blingSyncStartedAt = :now
             WHERE o.id = :id
               AND o.status = com.eduardo.ecomerce.domain.order.OrderStatus.PAID
               AND (o.blingSyncStatus IN (com.eduardo.ecomerce.domain.order.BlingSyncStatus.PENDING,
                                          com.eduardo.ecomerce.domain.order.BlingSyncStatus.FAILED)
                    OR (o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.PROCESSING
                        AND o.blingSyncStartedAt < :staleBefore))
            """)
    int claimForBlingSync(@Param("id") UUID id,
                          @Param("now") LocalDateTime now,
                          @Param("staleBefore") LocalDateTime staleBefore);

    /** Grava o id do pedido no Bling logo após o POST, antes do lançamento de estoque. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE Order o SET o.blingOrderId = :blingOrderId WHERE o.id = :id")
    int saveBlingOrderId(@Param("id") UUID id, @Param("blingOrderId") Long blingOrderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Order o
               SET o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.SENT,
                   o.blingSyncedAt = :now,
                   o.blingSyncError = NULL
             WHERE o.id = :id
            """)
    int markBlingSent(@Param("id") UUID id, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Order o
               SET o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.FAILED,
                   o.blingSyncError = :error,
                   o.blingSyncAttempts = o.blingSyncAttempts + 1
             WHERE o.id = :id
            """)
    int markBlingFailed(@Param("id") UUID id, @Param("error") String error);

    /**
     * Reenvio manual pelo admin: volta o pedido para PENDING zerando tentativas e erro.
     * Permitido para FAILED, PENDING e nulo (pedido pago antes da feature); SENT e PROCESSING ficam de fora.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE Order o
               SET o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.PENDING,
                   o.blingSyncAttempts = 0,
                   o.blingSyncError = NULL
             WHERE o.id = :id
               AND o.status = com.eduardo.ecomerce.domain.order.OrderStatus.PAID
               AND (o.blingSyncStatus IS NULL
                    OR o.blingSyncStatus IN (com.eduardo.ecomerce.domain.order.BlingSyncStatus.PENDING,
                                             com.eduardo.ecomerce.domain.order.BlingSyncStatus.FAILED))
            """)
    int resetBlingSyncForResend(@Param("id") UUID id);

    /** Fila do scheduler: pendentes/falhas abaixo do limite de tentativas e PROCESSING travado. */
    @Query("""
            SELECT o.id FROM Order o
             WHERE o.status = com.eduardo.ecomerce.domain.order.OrderStatus.PAID
               AND ((o.blingSyncStatus IN (com.eduardo.ecomerce.domain.order.BlingSyncStatus.PENDING,
                                           com.eduardo.ecomerce.domain.order.BlingSyncStatus.FAILED)
                     AND o.blingSyncAttempts < :maxAttempts)
                    OR (o.blingSyncStatus = com.eduardo.ecomerce.domain.order.BlingSyncStatus.PROCESSING
                        AND o.blingSyncStartedAt < :staleBefore))
             ORDER BY o.paidAt ASC
            """)
    List<UUID> findIdsForBlingRetry(@Param("maxAttempts") int maxAttempts,
                                    @Param("staleBefore") LocalDateTime staleBefore);
}
