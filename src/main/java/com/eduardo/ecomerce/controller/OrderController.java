package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
import com.eduardo.ecomerce.infra.security.SecurityUtils;
import com.eduardo.ecomerce.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderOutput> create() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(userId));
    }

    @GetMapping
    public ResponseEntity<List<OrderOutput>> findAll() {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderOutput> findById(@PathVariable UUID id) {
        UUID userId = SecurityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(orderService.findByUserIdAndOrderId(userId, id));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderOutput> updateStatus(@PathVariable UUID id, @RequestBody OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }
}
