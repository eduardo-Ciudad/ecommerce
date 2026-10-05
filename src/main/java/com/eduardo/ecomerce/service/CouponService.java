package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.coupon.Coupon;
import com.eduardo.ecomerce.domain.coupon.CouponRepository;
import com.eduardo.ecomerce.dto.input.coupon.CouponInput;
import com.eduardo.ecomerce.dto.output.common.PageResponse;
import com.eduardo.ecomerce.dto.output.coupon.CouponOutput;
import com.eduardo.ecomerce.dto.output.coupon.CouponValidationOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final CouponRepository couponRepository;


    @Transactional
    public CouponOutput create(CouponInput input) {
        String code = normalizeCode(input.code());

        if (couponRepository.existsByCode(code)) {
            throw new BusinessException("Já existe um cupom com o código " + code);
        }

        Coupon coupon = new Coupon();
        coupon.setCode(code);
        coupon.setDiscountPercent(input.discountPercent());
        coupon.setActive(input.active() == null || input.active());

        couponRepository.save(coupon);
        log.info("Cupom criado — code: {}, desconto: {}%", code, coupon.getDiscountPercent());
        return toOutput(coupon);
    }

    @Transactional
    public CouponOutput update(UUID id, CouponInput input) {
        Coupon coupon = findEntityById(id);
        String code = normalizeCode(input.code());

        if (couponRepository.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("Já existe um cupom com o código " + code);
        }

        coupon.setCode(code);
        coupon.setDiscountPercent(input.discountPercent());
        if (input.active() != null) {
            coupon.setActive(input.active());
        }

        couponRepository.save(coupon);
        log.info("Cupom atualizado — id: {}, code: {}, desconto: {}%, ativo: {}",
                id, code, coupon.getDiscountPercent(), coupon.getActive());
        return toOutput(coupon);
    }

    @Transactional(readOnly = true)
    public PageResponse<CouponOutput> findAll(Pageable pageable) {
        return PageResponse.from(couponRepository.findAllByOrderByCreatedAtDesc(pageable).map(this::toOutput));
    }

    @Transactional(readOnly = true)
    public CouponOutput findById(UUID id) {
        return toOutput(findEntityById(id));
    }

    @Transactional
    public void deactivate(UUID id) {
        Coupon coupon = findEntityById(id);
        coupon.setActive(false);
        couponRepository.save(coupon);
        log.info("Cupom desativado — id: {}, code: {}", id, coupon.getCode());
    }


    @Transactional(readOnly = true)
    public CouponValidationOutput validateForCheckout(String rawCode) {
        Coupon coupon = resolveActiveCoupon(rawCode);
        return new CouponValidationOutput(coupon.getCode(), coupon.getDiscountPercent());
    }


    public Coupon resolveActiveCoupon(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new BusinessException("Informe o código do cupom");
        }
        String code = normalizeCode(rawCode);
        return couponRepository.findByCodeAndActiveTrue(code)
                .orElseThrow(() -> new BusinessException("Cupom inválido ou expirado"));
    }


    public BigDecimal calculateDiscount(BigDecimal subtotal, BigDecimal discountPercent) {
        BigDecimal discount = subtotal
                .multiply(discountPercent)
                .divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);

        return discount.min(subtotal);
    }


    private Coupon findEntityById(UUID id) {
        return couponRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cupom não encontrado"));
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private CouponOutput toOutput(Coupon coupon) {
        return new CouponOutput(
                coupon.getId(),
                coupon.getCode(),
                coupon.getDiscountPercent(),
                coupon.getActive(),
                coupon.getCreatedAt()
        );
    }
}