package com.eduardo.ecomerce.service;


import com.eduardo.ecomerce.domain.user.User;
import com.eduardo.ecomerce.domain.user.UserRepository;
import com.eduardo.ecomerce.domain.user.UserRole;
import com.eduardo.ecomerce.dto.input.user.UserInput;
import com.eduardo.ecomerce.dto.output.user.UserOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public UserOutput create(UserInput input) {
        if (userRepository.existsByEmail(input.email())) {
            throw new BusinessException("Email já encontrado");
        }

        User user = new User();
        user.setName(input.name());
        user.setEmail(input.email());
        user.setPassword(input.password());
        user.setRole(UserRole.CLIENT);

        userRepository.save(user);
        return toOutput(user);
    }

    public List<UserOutput> findAll() {
        return  userRepository.findAll()
                .stream()
                .map(this::toOutput)
                .toList();
    }


    public UserOutput findById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));
        return toOutput(user);
    }

    public void delete(UUID id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("Usuário não encontrado");
        }
        userRepository.deleteById(id);
    }

    private UserOutput toOutput(User user) {
        return new UserOutput(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }

}
