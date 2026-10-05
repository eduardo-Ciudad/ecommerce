package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.coupon.Coupon;
import com.eduardo.ecomerce.domain.coupon.CouponRepository;
import com.eduardo.ecomerce.dto.input.coupon.CouponInput;
import com.eduardo.ecomerce.dto.output.coupon.CouponOutput;
import com.eduardo.ecomerce.dto.output.coupon.CouponValidationOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock
    private CouponRepository couponRepository;

    @InjectMocks
    private CouponService couponService;

    private Coupon buildCoupon(UUID id, String code, String percent, boolean active) {
        Coupon coupon = new Coupon();
        coupon.setId(id);
        coupon.setCode(code);
        coupon.setDiscountPercent(new BigDecimal(percent));
        coupon.setActive(active);
        return coupon;
    }


    @Test
    void shouldCreateCouponWithNormalizedCodeAndActiveByDefault() {
        CouponInput input = new CouponInput("  teste10 ", new BigDecimal("10.00"), null);
        when(couponRepository.existsByCode("TESTE10")).thenReturn(false);

        CouponOutput output = couponService.create(input);

        ArgumentCaptor<Coupon> captor = ArgumentCaptor.forClass(Coupon.class);
        verify(couponRepository).save(captor.capture());
        Coupon saved = captor.getValue();

        assertThat(saved.getCode()).isEqualTo("TESTE10");
        assertThat(saved.getDiscountPercent()).isEqualByComparingTo("10.00");
        assertThat(saved.getActive()).isTrue();
        assertThat(output.code()).isEqualTo("TESTE10");
    }

    @Test
    void shouldCreateInactiveCouponWhenRequested() {
        CouponInput input = new CouponInput("BLACK", new BigDecimal("20"), false);
        when(couponRepository.existsByCode("BLACK")).thenReturn(false);

        CouponOutput output = couponService.create(input);

        assertThat(output.active()).isFalse();
    }

    @Test
    void shouldRejectDuplicateCodeOnCreate() {
        CouponInput input = new CouponInput("teste10", new BigDecimal("10"), null);
        when(couponRepository.existsByCode("TESTE10")).thenReturn(true);

        BusinessException ex = assertThrows(BusinessException.class, () -> couponService.create(input));

        assertThat(ex.getMessage()).contains("TESTE10");
        verify(couponRepository, never()).save(any());
    }


    @Test
    void shouldUpdateCouponKeepingActiveWhenNull() {
        UUID id = UUID.randomUUID();
        Coupon coupon = buildCoupon(id, "TESTE10", "10", false);
        when(couponRepository.findById(id)).thenReturn(Optional.of(coupon));
        when(couponRepository.existsByCodeAndIdNot("TESTE15", id)).thenReturn(false);

        CouponOutput output = couponService.update(id, new CouponInput("teste15", new BigDecimal("15"), null));

        assertThat(output.code()).isEqualTo("TESTE15");
        assertThat(output.discountPercent()).isEqualByComparingTo("15");
        assertThat(output.active()).isFalse();
    }

    @Test
    void shouldReactivateCouponOnUpdate() {
        UUID id = UUID.randomUUID();
        Coupon coupon = buildCoupon(id, "TESTE10", "10", false);
        when(couponRepository.findById(id)).thenReturn(Optional.of(coupon));
        when(couponRepository.existsByCodeAndIdNot("TESTE10", id)).thenReturn(false);

        CouponOutput output = couponService.update(id, new CouponInput("TESTE10", new BigDecimal("10"), true));

        assertThat(output.active()).isTrue();
    }

    @Test
    void shouldRejectCodeOfAnotherCouponOnUpdate() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.of(buildCoupon(id, "TESTE10", "10", true)));
        when(couponRepository.existsByCodeAndIdNot("OUTRO", id)).thenReturn(true);

        assertThrows(BusinessException.class,
                () -> couponService.update(id, new CouponInput("outro", new BigDecimal("10"), null)));
        verify(couponRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingMissingCoupon() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> couponService.update(id, new CouponInput("TESTE10", new BigDecimal("10"), null)));
    }


    @Test
    void shouldDeactivateInsteadOfDeleting() {
        UUID id = UUID.randomUUID();
        Coupon coupon = buildCoupon(id, "TESTE10", "10", true);
        when(couponRepository.findById(id)).thenReturn(Optional.of(coupon));

        couponService.deactivate(id);

        assertThat(coupon.getActive()).isFalse();
        verify(couponRepository).save(coupon);
        verify(couponRepository, never()).delete(any());
        verify(couponRepository, never()).deleteById(any());
    }

    @Test
    void shouldThrowNotFoundWhenCouponDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(couponRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> couponService.findById(id));
    }


    @Test
    void shouldValidateActiveCouponWithNormalizedCode() {
        when(couponRepository.findByCodeAndActiveTrue("TESTE10"))
                .thenReturn(Optional.of(buildCoupon(UUID.randomUUID(), "TESTE10", "10", true)));

        CouponValidationOutput output = couponService.validateForCheckout("  teste10 ");

        assertThat(output.code()).isEqualTo("TESTE10");
        assertThat(output.discountPercent()).isEqualByComparingTo("10");
    }

    @Test
    void shouldRejectInactiveOrMissingCoupon() {
        when(couponRepository.findByCodeAndActiveTrue("NAOEXISTE")).thenReturn(Optional.empty());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> couponService.resolveActiveCoupon("naoexiste"));

        assertThat(ex.getMessage()).isEqualTo("Cupom inválido ou expirado");
    }

    @Test
    void shouldRejectBlankCodeWithoutQueryingDatabase() {
        assertThrows(BusinessException.class, () -> couponService.resolveActiveCoupon("   "));
        assertThrows(BusinessException.class, () -> couponService.resolveActiveCoupon(null));

        verify(couponRepository, never()).findByCodeAndActiveTrue(anyString());
    }


    @ParameterizedTest(name = "{0} com {1}% = {2}")
    @CsvSource({
            "150.00, 10, 15.00",
            "89.90,  10, 8.99",
            "89.90,  15, 13.49",
            "59.80,  12.5, 7.48",
            "0.10,   33.33, 0.03",
            "100.00, 90, 90.00"
    })
    void shouldCalculateDiscountRoundingHalfUp(String subtotal, String percent, String expected) {
        BigDecimal discount = couponService.calculateDiscount(new BigDecimal(subtotal), new BigDecimal(percent));

        assertThat(discount).isEqualByComparingTo(expected);
        assertThat(discount.scale()).isEqualTo(2);
    }
}