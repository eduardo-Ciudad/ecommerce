package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.address.AddressInput;
import com.eduardo.ecomerce.dto.output.address.AddressOutput;
import com.eduardo.ecomerce.infra.security.SecurityUtils;

import com.eduardo.ecomerce.service.AddressService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;
    private final SecurityUtils securityUtils;

    @PostMapping
    public ResponseEntity<AddressOutput> create(@RequestBody @Valid AddressInput input) {
        UUID userId = securityUtils.getAuthenticatedUserId();
        AddressOutput output = addressService.create(userId, input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping
    public ResponseEntity<List<AddressOutput>> findByUser() {
        UUID userId = securityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(addressService.findByUserId(userId));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AddressOutput> update(@PathVariable UUID id, @RequestBody @Valid AddressInput input) {
        UUID userId = securityUtils.getAuthenticatedUserId();
        return ResponseEntity.ok(addressService.update(userId, id, input));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        UUID userId = securityUtils.getAuthenticatedUserId();
        addressService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
