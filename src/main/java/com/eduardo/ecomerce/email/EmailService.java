package com.eduardo.ecomerce.email;

import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.dto.output.orderitem.OrderItemOutput;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String sender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.admin-email}")
    private String adminEmail;

    @Async
    public void sendPasswordChangeEmail(String recipient, String token) {
        try {
            String link = frontendUrl + "/confirmar-alteracao-senha.html?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient);
            message.setSubject("Confirme a alteração de senha - GabiKids");
            message.setText(
                    "Olá!\n\n" +
                            "Recebemos uma solicitação para alterar a senha da sua conta na GabiKids.\n\n" +
                            "Para confirmar a alteração, clique no link abaixo:\n\n" +
                            link + "\n\n" +
                            "Este link expira em 1 hora.\n\n" +
                            "Se você não solicitou essa alteração, ignore este email e sua senha permanecerá a mesma."
            );

            mailSender.send(message);
            log.info("Email de alteração de senha enviado para {}", recipient);
        } catch (Exception e) {
            log.error("Erro ao enviar email de alteração de senha para {}: {}", recipient, e.getMessage());
        }
    }

    @Async
    public void sendPasswordResetEmail(String recipient, String token) {
        try {
            String link = frontendUrl + "/resetar-senha.html?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient);
            message.setSubject("Recuperação de senha - GabiKids");
            message.setText(
                    "Olá!\n\n" +
                            "Recebemos uma solicitação para recuperar a senha da sua conta na GabiKids.\n\n" +
                            "Para criar uma nova senha, clique no link abaixo:\n\n" +
                            link + "\n\n" +
                            "Este link expira em 1 hora.\n\n" +
                            "Se você não solicitou essa recuperação, ignore este email."
            );

            mailSender.send(message);
            log.info("Email de reset de senha enviado para {}", recipient);
        } catch (Exception e) {
            log.error("Erro ao enviar email de reset de senha para {}: {}", recipient, e.getMessage());
        }
    }

    @Async
    public void sendVerificationEmail(String recipient, String token) {
        try {
            String link = frontendUrl + "/verificar-email.html?token=" + token;

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(recipient);
            message.setSubject("Confirme seu email - GabiKids");
            message.setText(
                    "Olá!\n\n" +
                            "Obrigado por se cadastrar na GabiKids.\n\n" +
                            "Para ativar sua conta, clique no link abaixo:\n\n" +
                            link + "\n\n" +
                            "Este link expira em 1 hora.\n\n" +
                            "Se você não criou esta conta, ignore este email."
            );

            mailSender.send(message);
            log.info("Email de verificação enviado para {}", recipient);
        } catch (Exception e) {
            log.error("Erro ao enviar email de verificação para {}: {}", recipient, e.getMessage());
        }
    }

    @Async
    public void sendNewOrderNotification(OrderOutput order, String customerEmail) {
        try {
            StringBuilder itemsText = new StringBuilder();
            for (OrderItemOutput item : order.items()) {
                itemsText.append("- ")
                        .append(item.productName())
                        .append(" (tam. ").append(item.size()).append(") x")
                        .append(item.quantity())
                        .append(" — R$ ").append(item.unitPrice())
                        .append("\n");
        }
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(sender);
            message.setTo(adminEmail);
            message.setSubject("Novo pedido recebido — GabiKids");
            message.setText(
                    "Um novo pedido foi realizado!\n\n" +
                            "Pedido: " + order.id() + "\n" +
                            "Cliente: " + order.recipientName() + " (" + customerEmail + ")\n" +
                            "Total: R$ " + order.total() + "\n\n" +
                            "Itens:\n" + itemsText +
                            "\nEndereço de entrega:\n" +
                            order.recipientStreet() + ", " + order.recipientNumber() +
                            " - " + order.recipientNeighborhood() + "\n" +
                            order.recipientCity() + "/" + order.recipientState() +
                            " - CEP " + order.recipientCep()
            );

            mailSender.send(message);
            log.info("Email de novo pedido enviado — orderId: {}", order.id());
        } catch (Exception e) {
            log.error("Erro ao enviar email de novo pedido — orderId: {}: {}", order.id(), e.getMessage());
        }
    }

}