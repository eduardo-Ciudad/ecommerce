package com.eduardo.ecomerce.domain.order;

import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
