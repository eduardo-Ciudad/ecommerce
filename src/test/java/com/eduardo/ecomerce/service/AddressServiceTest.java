package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.address.Address;
import com.eduardo.ecomerce.domain.address.AddressRepository;
import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.dto.input.address.AddressInput;
import com.eduardo.ecomerce.dto.output.address.AddressOutput;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock
    private AddressRepository addressRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AddressService addressService;

    private UUID userId;
    private User user;
    private AddressInput input;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = new User();
        user.setId(userId);

        input = new AddressInput(
                "Casa",
                "15046-806",
                "Rua das Flores",
                "123",
                "Apto 4",
                "Centro",
                "Rio Preto",
                "sp",
                false
        );
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create — deve salvar endereço com CEP limpo e UF maiúscula")
    void create_success() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressOutput output = addressService.create(userId, input);

        assertThat(output.cep()).isEqualTo("15046806");
        assertThat(output.state()).isEqualTo("SP");
        assertThat(output.label()).isEqualTo("Casa");
        assertThat(output.isDefault()).isFalse();

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        verify(addressRepository).save(captor.capture());
        assertThat(captor.getValue().getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("create — deve lançar ResourceNotFoundException quando usuário não existe")
    void create_userNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.create(userId, input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("create — deve desmarcar o endereço padrão anterior quando isDefault=true")
    void create_asDefault_clearsPreviousDefault() {
        AddressInput defaultInput = new AddressInput(
                "Trabalho", "15046-806", "Rua X", "10", null, "Centro", "Rio Preto", "sp", true
        );

        Address currentDefault = new Address();
        currentDefault.setId(UUID.randomUUID());
        currentDefault.setIsDefault(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.findByUserIdAndIsDefaultTrue(userId)).thenReturn(Optional.of(currentDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressOutput output = addressService.create(userId, defaultInput);

        assertThat(output.isDefault()).isTrue();
        assertThat(currentDefault.getIsDefault()).isFalse();
        verify(addressRepository).save(currentDefault);
    }

    @Test
    @DisplayName("create — não deve consultar endereço padrão quando isDefault=false")
    void create_notDefault_doesNotTouchExistingDefault() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        addressService.create(userId, input);

        verify(addressRepository, never()).findByUserIdAndIsDefaultTrue(any());
    }

    // -------------------------------------------------------------------------
    // findByUserId
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findByUserId — deve retornar lista de endereços do usuário")
    void findByUserId_success() {
        Address a1 = new Address();
        a1.setId(UUID.randomUUID());
        a1.setLabel("Casa");
        a1.setCep("15046806");
        a1.setStreet("Rua A");
        a1.setNumber("1");
        a1.setNeighborhood("Centro");
        a1.setCity("Rio Preto");
        a1.setState("SP");
        a1.setIsDefault(true);

        when(addressRepository.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(a1));

        List<AddressOutput> result = addressService.findByUserId(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).label()).isEqualTo("Casa");
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("update — deve atualizar campos do endereço existente")
    void update_success() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address();
        existing.setId(addressId);
        existing.setLabel("Antigo");
        existing.setCep("00000000");
        existing.setStreet("Rua Velha");
        existing.setNumber("1");
        existing.setNeighborhood("Bairro");
        existing.setCity("Cidade");
        existing.setState("MG");

        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(existing));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressOutput output = addressService.update(userId, addressId, input);

        assertThat(output.label()).isEqualTo("Casa");
        assertThat(output.cep()).isEqualTo("15046806");
        assertThat(output.state()).isEqualTo("SP");
    }

    @Test
    @DisplayName("update — deve lançar ResourceNotFoundException quando endereço não pertence ao usuário")
    void update_addressNotFound() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.update(userId, addressId, input))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Endereço não encontrado");

        verify(addressRepository, never()).save(any());
    }

    @Test
    @DisplayName("update — deve desmarcar endereço padrão anterior ao promover outro para padrão")
    void update_promotesToDefault_clearsPrevious() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address();
        existing.setId(addressId);
        existing.setIsDefault(false);

        Address currentDefault = new Address();
        currentDefault.setId(UUID.randomUUID());
        currentDefault.setIsDefault(true);

        AddressInput asDefault = new AddressInput(
                "Casa", "15046-806", "Rua das Flores", "123", null, "Centro", "Rio Preto", "sp", true
        );

        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(existing));
        when(addressRepository.findByUserIdAndIsDefaultTrue(userId)).thenReturn(Optional.of(currentDefault));
        when(addressRepository.save(any(Address.class))).thenAnswer(inv -> inv.getArgument(0));

        AddressOutput output = addressService.update(userId, addressId, asDefault);

        assertThat(output.isDefault()).isTrue();
        assertThat(currentDefault.getIsDefault()).isFalse();
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("delete — deve remover endereço do usuário")
    void delete_success() {
        UUID addressId = UUID.randomUUID();
        Address existing = new Address();
        existing.setId(addressId);

        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.of(existing));

        addressService.delete(userId, addressId);

        verify(addressRepository).delete(existing);
    }

    @Test
    @DisplayName("delete — deve lançar ResourceNotFoundException quando endereço não pertence ao usuário (proteção IDOR)")
    void delete_addressNotFound() {
        UUID addressId = UUID.randomUUID();
        when(addressRepository.findByIdAndUserId(addressId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.delete(userId, addressId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Endereço não encontrado");

        verify(addressRepository, never()).delete(any());
    }
}