package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.Address;
import com.eduardo.ecomerce.domain.address.AddressRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.address.AddressInput;
import com.eduardo.ecomerce.dto.output.address.AddressOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional
    public AddressOutput create(UUID userId, AddressInput input) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Address address = new Address();
        address.setUser(user);
        applyInput(address, input);

        if (Boolean.TRUE.equals(input.isDefault())) {
            clearCurrentDefault(userId);
            address.setIsDefault(true);
        }

        addressRepository.save(address);
        log.info("Endereço criado — usuário: {}, addressId: {}", userId, address.getId());
        return toOutput(address);
    }

    @Transactional(readOnly = true)
    public List<AddressOutput> findByUserId(UUID userId) {
        return addressRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toOutput)
                .toList();
    }

    @Transactional
    public AddressOutput update(UUID userId, UUID addressId, AddressInput input) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado"));

        applyInput(address, input);

        if (Boolean.TRUE.equals(input.isDefault())) {
            clearCurrentDefault(userId);
            address.setIsDefault(true);
        }

        addressRepository.save(address);
        log.info("Endereço atualizado — usuário: {}, addressId: {}", userId, addressId);
        return toOutput(address);
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Endereço não encontrado"));
        addressRepository.delete(address);
        log.info("Endereço removido — usuário: {}, addressId: {}", userId, addressId);
    }

    private void clearCurrentDefault(UUID userId) {
        addressRepository.findByUserIdAndIsDefaultTrue(userId).ifPresent(current -> {
            current.setIsDefault(false);
            addressRepository.save(current);
        });
    }

    private void applyInput(Address address, AddressInput input) {
        address.setLabel(input.label());
        address.setCep(input.cep().replace("-", ""));
        address.setStreet(input.street());
        address.setNumber(input.number());
        address.setComplement(input.complement());
        address.setNeighborhood(input.neighborhood());
        address.setCity(input.city());
        address.setState(input.state().toUpperCase());
    }

    private AddressOutput toOutput(Address address) {
        return new AddressOutput(
                address.getId(),
                address.getLabel(),
                address.getCep(),
                address.getStreet(),
                address.getNumber(),
                address.getComplement(),
                address.getNeighborhood(),
                address.getCity(),
                address.getState(),
                address.getIsDefault()
        );
    }
}