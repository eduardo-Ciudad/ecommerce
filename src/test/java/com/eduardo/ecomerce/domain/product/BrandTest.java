package com.eduardo.ecomerce.domain.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrandTest {

    @Test
    void fromParamResolvesExactEnumName() {
        assertThat(Brand.fromParam("FAKINI")).isEqualTo(Brand.FAKINI);
    }

    @Test
    void fromParamIsCaseInsensitive() {
        assertThat(Brand.fromParam("fakini")).isEqualTo(Brand.FAKINI);
    }

    @Test
    void fromParamAcceptsHyphenOrSpaceInPlaceOfUnderscore() {
        assertThat(Brand.fromParam("malwee-kids")).isEqualTo(Brand.MALWEE_KIDS);
        assertThat(Brand.fromParam("malwee kids")).isEqualTo(Brand.MALWEE_KIDS);
    }

    @Test
    void fromParamReturnsNullForUnknownOrBlankValue() {
        assertThat(Brand.fromParam("nike")).isNull();
        assertThat(Brand.fromParam("")).isNull();
        assertThat(Brand.fromParam(null)).isNull();
    }
}