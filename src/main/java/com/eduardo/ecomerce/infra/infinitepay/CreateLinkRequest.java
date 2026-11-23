package com.eduardo.ecomerce.infra.infinitepay;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** Corpo do POST /links. Valores em centavos; campos nulos não são enviados. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CreateLinkRequest(
        String handle,
        List<Item> items,
        String orderNsu,
        String redirectUrl,
        String webhookUrl,
        Customer customer,
        Address address,
        Options options
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(String description, int price, int quantity) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Customer(String name, String email, String phoneNumber) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Address(String cep, String street, String number, String neighborhood,
                          String city, String state, String complement) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Options(Integer assuredInstallments, Boolean requireDeliveryAddress, Boolean singlePayment,
                          String validUntil, List<String> paymentMethodsEnabled) {
    }
}
