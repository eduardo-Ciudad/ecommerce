package com.eduardo.ecomerce.service;


import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.dto.output.orderitem.OrderItemOutput;
import com.eduardo.ecomerce.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    private OrderOutput order;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(emailService, "sender", "no-reply@gabikids.com");
        ReflectionTestUtils.setField(emailService, "adminEmail", "contato.gabikids@gmail.com");

        OrderItemOutput item = new OrderItemOutput(
                UUID.randomUUID(), UUID.randomUUID(), "Vestido Rosa", "P", 2, new BigDecimal("29.90"));

        order = new OrderOutput(
                UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("74.80"),
                OrderStatus.PENDING, null, null,
                "PAC", new BigDecimal("15.00"), 7,
                "Maria Silva", "15046-806", "Rua Teste", "100", null,
                "Centro", "Rio Preto", "SP",
                List.of(item), LocalDateTime.now()
        );
    }

    @Test
    void shouldSendEmailWithOrderDetails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        emailService.sendNewOrderNotification(order, "maria@example.com");

        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();

        assertThat(sent.getTo()).containsExactly("contato.gabikids@gmail.com");
        assertThat(sent.getSubject()).isEqualTo("Novo pedido recebido — GabiKids");
        assertThat(sent.getText())
                .contains(order.id().toString())
                .contains("Maria Silva")
                .contains("maria@example.com")
                .contains("Vestido Rosa")
                .contains("74.80");
    }

    @Test
    void shouldNotThrowWhenMailSenderFails() {
        doThrow(new MailSendException("SMTP indisponível") {})
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() -> emailService.sendNewOrderNotification(order, "maria@example.com"));
    }

}
