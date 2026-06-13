package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.cart.Cart;
import com.eduardo.ecomerce.domain.cart.CartRepository;
import com.eduardo.ecomerce.domain.cartitem.CartItem;
import com.eduardo.ecomerce.domain.cartitem.CartItemRepository;
import com.eduardo.ecomerce.domain.productvariant.ProductVariant;
import com.eduardo.ecomerce.domain.productvariant.ProductVariantRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.cartitem.CartItemInput;
import com.eduardo.ecomerce.dto.output.cart.CartOutput;
import com.eduardo.ecomerce.dto.output.cartitem.CartItemOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import com.eduardo.ecomerce.infra.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.rmi.server.UID;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final UserRepository userRepository;
    private final ProductVariantRepository productVariantRepository;

    @Transactional
    public CartOutput findByUserId(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createCart(userId));
        return toOutput(cart);
    }

    @Transactional
    public CartOutput addItem(UUID userId, CartItemInput input) {
        if (input.quantity() <= 0) {
            throw new BusinessException("Quantidade deve ser maior que zero");
        }

        ProductVariant variant = productVariantRepository.findById(input.variantId())
                .orElseThrow(() -> new ResourceNotFoundException("Variante não encontrada"));

        if (!variant.getProduct().getActive()) {
            throw new BusinessException("Produto inativo não pode ser adicionado ao carrinho");
        }

        if (variant.getStock() <= 0) {
            throw new BusinessException("Variante sem estoque");
        }

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> createCart(userId));

        Optional<CartItem> existingItemOpt = cartItemRepository.findByCartIdAndVariantId(cart.getId(), variant.getId());

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            int newQuantity = existingItem.getQuantity() + input.quantity();
            if (newQuantity > variant.getStock()) {
                throw new BusinessException("Quantidade solicitada indisponível em estoque");
            }
            existingItem.setQuantity(newQuantity);
            cartItemRepository.save(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setCart(cart);
            newItem.setVariant(variant);
            newItem.setQuantity(input.quantity());
            cartItemRepository.save(newItem);
        }

        return toOutput(cart);
    }

    @Transactional
    public CartOutput updateItem(UUID itemId, Integer quantity) {

        UUID userId = SecurityUtils.getAuthenticatedUserId();

        CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Item não encontrado"));

        if (quantity <= 0) {
            throw new BusinessException("Quantidade deve ser maior que zero");
        }

        if (quantity > item.getVariant().getStock()) {
            throw new BusinessException("Estoque insuficiente");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);

        return toOutput(item.getCart());
    }


    @Transactional
    public void removeItem(UUID itemId) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();

        CartItem item = cartItemRepository.findByIdAndCartUserId(itemId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Item do carrinho não encontrado"));

        cartItemRepository.delete(item);
    }

    private Cart createCart(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        Cart cart = new Cart();
        cart.setUser(user);
        return cartRepository.save(cart);
    }

    private CartOutput toOutput(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartId(cart.getId());
        List<CartItemOutput> itemOutputs = items.stream()
                .map(item -> new CartItemOutput(
                        item.getId(),
                        item.getVariant().getId(),
                        item.getVariant().getProduct().getName(),
                        item.getVariant().getSize(),
                        item.getVariant().getPrice(),
                        item.getQuantity()
                ))
                .toList();
        return new CartOutput(cart.getId(), cart.getUser().getId(), itemOutputs, cart.getCreatedAt());
    }
}
