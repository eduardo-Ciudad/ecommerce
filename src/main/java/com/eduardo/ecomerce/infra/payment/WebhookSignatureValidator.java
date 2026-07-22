package com.eduardo.ecomerce.infra.payment;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebhookSignatureValidator {

    @Value("${mercadopago.webhook-secret}")
    private String webhookSecret;

    public boolean isValid(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || xRequestId == null || dataId == null) {
            return false;
        }

        Map<String, String> parts = parseSignatureHeader(xSignature);
        String ts = parts.get("ts");
        String receivedHash = parts.get("v1");

        if (ts == null || receivedHash == null) {
            return false;
    }
        String manifest = "id:" + dataId.toLowerCase() + ";request-id:" + xRequestId + ";ts:" + ts + ";";

        String calculatedHash = hmacSha256(manifest, webhookSecret);

        return calculatedHash.equals(receivedHash);


}private Map<String, String> parseSignatureHeader(String xSignature) {
        Map<String, String> parts = new HashMap<>();
        for (String pair : xSignature.split(",")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2) {
                parts.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return parts;
    }

    private String hmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao calcular HMAC do webhook", e);
        }
    }
}
