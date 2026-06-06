package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.domain.order.OrderStatus;
import com.eduardo.ecomerce.dto.output.order.OrderOutput;
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

    @PostMapping("/{userId}")
    public ResponseEntity<OrderOutput> create(@PathVariable UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orderService.create(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<List<OrderOutput>> findByUserId(@PathVariable UUID userId) {
        return ResponseEntity.ok(orderService.findByUserId(userId));
    }

    @GetMapping("/{userId}/{orderId}")
    public ResponseEntity<OrderOutput> findByUserIdAndOrderId(
            @PathVariable UUID userId,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(orderService.findByUserIdAndOrderId(userId, orderId));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<OrderOutput> updateStatus(@PathVariable UUID id, @RequestBody OrderStatus status) {
        return ResponseEntity.ok(orderService.updateStatus(id, status));
    }
}
