package com.eduardo.ecomerce.infra.infinitepay;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Resposta do POST /links. A documentação só garante a URL; o restante é ignorado até ser confirmado. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateLinkResponse(String url, String slug) {
}
