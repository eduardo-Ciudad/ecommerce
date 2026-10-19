package com.eduardo.ecomerce.domain.order;

import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "app.encryption-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
class OrderRepositoryTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void findByStatusAndCreatedAtBefore_retornaSomentePendingAnterioresAoCorte() {
        User user = persistUser();
        LocalDateTime cutoff = LocalDateTime.of(2026, 9, 2, 12, 0);
        Order oldPending = persistOrder(user, OrderStatus.PENDING);
        Order oldPaid = persistOrder(user, OrderStatus.PAID);
        Order recentPending = persistOrder(user, OrderStatus.PENDING);
        Order atCutoffPending = persistOrder(user, OrderStatus.PENDING);
        setCreatedAt(oldPending.getId(), cutoff.minusMinutes(1));
        setCreatedAt(oldPaid.getId(), cutoff.minusHours(1));
        setCreatedAt(recentPending.getId(), cutoff.plusMinutes(1));
        setCreatedAt(atCutoffPending.getId(), cutoff);
        entityManager.clear();

        List<Order> result = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING, cutoff);

        assertThat(result).extracting(Order::getId).containsExactly(oldPending.getId());
    }

    @Test
    void findByIdForUpdate_pedidoExistente_retornaPedido() {
        Order order = persistOrder(persistUser(), OrderStatus.PENDING);
        entityManager.clear();

        Order result = orderRepository.findByIdForUpdate(order.getId()).orElseThrow();

        assertThat(result.getId()).isEqualTo(order.getId());
    }

    @Test
    void findByIdForUpdate_pedidoInexistente_retornaOptionalVazio() {
        assertThat(orderRepository.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    // ---------- Sync de pedidos site -> Bling ----------

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 15, 0);
    private static final LocalDateTime STALE_BEFORE = NOW.minusMinutes(15);
    private static final int MAX_ATTEMPTS = 5;

    // ----- claimForBlingSync -----

    @Test
    void claimForBlingSync_pending_pegaEMarcaProcessing() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PENDING);

        int claimed = orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE);

        assertThat(claimed).isEqualTo(1);
        Order reloaded = reload(order);
        assertThat(reloaded.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.PROCESSING);
        assertThat(reloaded.getBlingSyncStartedAt()).isEqualTo(NOW);
    }

    @Test
    void claimForBlingSync_failed_pega() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.FAILED);

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isEqualTo(1);
    }

    @Test
    void claimForBlingSync_sent_naoPega() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.SENT);

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isZero();
        assertThat(reload(order).getBlingSyncStatus()).isEqualTo(BlingSyncStatus.SENT);
    }

    @Test
    void claimForBlingSync_statusNulo_naoPega() {
        Order order = persistBlingOrder(OrderStatus.PAID, null);

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isZero();
        assertThat(reload(order).getBlingSyncStatus()).isNull();
    }

    @Test
    void claimForBlingSync_processingRecente_naoPega() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);
        setBlingSyncStartedAt(order.getId(), NOW.minusMinutes(1));

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isZero();
    }

    @Test
    void claimForBlingSync_processingTravado_pegaDeNovo() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);
        setBlingSyncStartedAt(order.getId(), STALE_BEFORE.minusMinutes(1));

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isEqualTo(1);
        assertThat(reload(order).getBlingSyncStartedAt()).isEqualTo(NOW);
    }

    @Test
    void claimForBlingSync_pedidoNaoPaid_naoPega() {
        Order order = persistBlingOrder(OrderStatus.CANCELLED, BlingSyncStatus.PENDING);

        assertThat(orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE)).isZero();
    }

    @Test
    void claimForBlingSync_segundaChamadaSeguida_naoPega() {
        // simula listener e scheduler disputando o mesmo pedido
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PENDING);

        int first = orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE);
        int second = orderRepository.claimForBlingSync(order.getId(), NOW, STALE_BEFORE);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    // ----- saveBlingOrderId / markBlingSent / markBlingFailed -----

    @Test
    void saveBlingOrderId_gravaOId() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);

        int updated = orderRepository.saveBlingOrderId(order.getId(), 987654321L);

        assertThat(updated).isEqualTo(1);
        assertThat(reload(order).getBlingOrderId()).isEqualTo(987654321L);
    }

    @Test
    void markBlingSent_marcaSentComDataELimpaErro() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);
        orderRepository.markBlingFailed(order.getId(), "erro anterior");

        orderRepository.markBlingSent(order.getId(), NOW);

        Order reloaded = reload(order);
        assertThat(reloaded.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.SENT);
        assertThat(reloaded.getBlingSyncedAt()).isEqualTo(NOW);
        assertThat(reloaded.getBlingSyncError()).isNull();
    }

    @Test
    void markBlingFailed_marcaFailedGravaErroEIncrementaTentativas() {
        Order order = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);

        orderRepository.markBlingFailed(order.getId(), "HTTP 400: produto inválido");

        Order reloaded = reload(order);
        assertThat(reloaded.getBlingSyncStatus()).isEqualTo(BlingSyncStatus.FAILED);
        assertThat(reloaded.getBlingSyncError()).isEqualTo("HTTP 400: produto inválido");
        assertThat(reloaded.getBlingSyncAttempts()).isEqualTo(1);

        orderRepository.markBlingFailed(order.getId(), "HTTP 500");

        assertThat(reload(order).getBlingSyncAttempts()).isEqualTo(2);
        assertThat(reload(order).getBlingSyncError()).isEqualTo("HTTP 500");
    }

    // ----- findIdsForBlingRetry -----

    @Test
    void findIdsForBlingRetry_trazSomenteElegiveisOrdenadosPorPaidAt() {
        Order pending = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PENDING);
        Order failed = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.FAILED);
        Order stuck = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);
        Order recentProcessing = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.PROCESSING);
        Order exhausted = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.FAILED);
        Order sent = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.SENT);
        Order legacy = persistBlingOrder(OrderStatus.PAID, null);
        Order cancelled = persistBlingOrder(OrderStatus.CANCELLED, BlingSyncStatus.PENDING);

        setPaidAt(pending.getId(), NOW.minusMinutes(10));
        setPaidAt(failed.getId(), NOW.minusMinutes(30));
        setPaidAt(stuck.getId(), NOW.minusMinutes(20));
        setBlingSyncStartedAt(stuck.getId(), STALE_BEFORE.minusMinutes(1));
        setBlingSyncStartedAt(recentProcessing.getId(), NOW.minusMinutes(1));
        setBlingSyncAttempts(exhausted.getId(), MAX_ATTEMPTS);
        entityManager.clear();

        List<UUID> result = orderRepository.findIdsForBlingRetry(MAX_ATTEMPTS, STALE_BEFORE);

        assertThat(result).containsExactly(failed.getId(), stuck.getId(), pending.getId());
        assertThat(result).doesNotContain(recentProcessing.getId(), exhausted.getId(),
                sent.getId(), legacy.getId(), cancelled.getId());
    }

    // ----- constraint -----

    @Test
    void blingOrderIdDuplicado_violaConstraintUnique() {
        Order first = persistBlingOrder(OrderStatus.PAID, BlingSyncStatus.SENT);
        orderRepository.saveBlingOrderId(first.getId(), 111L);

        Order second = new Order();
        second.setUser(persistUser());
        second.setTotal(new BigDecimal("50.00"));
        second.setStatus(OrderStatus.PAID);
        second.setBlingOrderId(111L);

        assertThatThrownBy(() -> orderRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Order persistBlingOrder(OrderStatus status, BlingSyncStatus blingStatus) {
        Order order = new Order();
        order.setUser(persistUser());
        order.setTotal(new BigDecimal("100.00"));
        order.setStatus(status);
        order.setBlingSyncStatus(blingStatus);
        orderRepository.saveAndFlush(order);
        return order;
    }

    private Order reload(Order order) {
        entityManager.clear();
        return orderRepository.findById(order.getId()).orElseThrow();
    }

    private void setPaidAt(UUID orderId, LocalDateTime paidAt) {
        jdbcTemplate.update("UPDATE orders SET paid_at = ? WHERE id = ?", paidAt, orderId);
    }

    private void setBlingSyncStartedAt(UUID orderId, LocalDateTime startedAt) {
        jdbcTemplate.update("UPDATE orders SET bling_sync_started_at = ? WHERE id = ?", startedAt, orderId);
    }

    private void setBlingSyncAttempts(UUID orderId, int attempts) {
        jdbcTemplate.update("UPDATE orders SET bling_sync_attempts = ? WHERE id = ?", attempts, orderId);
    }

    private User persistUser() {
        User user = new User();
        user.setName("Cliente Teste");
        user.setEmail(UUID.randomUUID() + "@teste.com");
        user.setPassword("password");
        user.setRole(UserRole.CLIENT);
        entityManager.persist(user);
        return user;
    }

    private Order persistOrder(User user, OrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setTotal(new BigDecimal("100.00"));
        order.setStatus(status);
        orderRepository.saveAndFlush(order);
        return order;
    }

    private void setCreatedAt(UUID orderId, LocalDateTime createdAt) {
        jdbcTemplate.update("UPDATE orders SET created_at = ? WHERE id = ?", createdAt, orderId);
    }
}
