package com.eduardo.ecomerce.service;

import com.eduardo.ecomerce.domain.category.Category;
import com.eduardo.ecomerce.domain.category.CategoryRepository;
import com.eduardo.ecomerce.domain.product.ProductRepository;
import com.eduardo.ecomerce.dto.input.category.CategoryInput;
import com.eduardo.ecomerce.dto.output.category.CategoryOutput;
import com.eduardo.ecomerce.infra.exception.BusinessException;
import com.eduardo.ecomerce.infra.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {


    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    private Category category;
    private UUID categoryId;

    @BeforeEach
    void setUp() {
        categoryId = UUID.randomUUID();
        category = new Category();
        category.setId(categoryId);
        category.setName("Camisetas");
        category.setCreatedAt(LocalDateTime.now());
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("create — deve criar categoria e retornar output")
    void create_success() {
        CategoryInput input = new CategoryInput("Camisetas");

        when(categoryRepository.existsByName(input.name())).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryOutput output = categoryService.create(input);

        assertThat(output.name()).isEqualTo("Camisetas");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("create — deve lançar BusinessException quando nome já existe")
    void create_duplicateName() {
        CategoryInput input = new CategoryInput("Camisetas");

        when(categoryRepository.existsByName(input.name())).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Categoria já cadastrada");

        verify(categoryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // findAll
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findAll — deve retornar lista de categorias")
    void findAll_success() {
        when(categoryRepository.findAll()).thenReturn(List.of(category));

        List<CategoryOutput> result = categoryService.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Camisetas");
    }

    @Test
    @DisplayName("findAll — deve retornar lista vazia quando não há categorias")
    void findAll_empty() {
        when(categoryRepository.findAll()).thenReturn(List.of());

        List<CategoryOutput> result = categoryService.findAll();

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findById
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("findById — deve retornar categoria quando encontrada")
    void findById_success() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        CategoryOutput output = categoryService.findById(categoryId);

        assertThat(output.name()).isEqualTo("Camisetas");
    }

    @Test
    @DisplayName("findById — deve lançar ResourceNotFoundException quando não encontrada")
    void findById_notFound() {
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.findById(categoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Categoria não encontrada");
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("delete — deve deletar categoria quando encontrada")
    void delete_success() {
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(productRepository.existsByCategoryId(categoryId)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.delete(categoryId))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Categoria possui produtos vinculados e não pode ser removida");

        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("delete — deve lançar ResourceNotFoundException quando não encontrada")
    void delete_notFound() {
        when(categoryRepository.existsById(categoryId)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.delete(categoryId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Categoria não encontrada");

        verify(categoryRepository, never()).deleteById(any());
    }
}
