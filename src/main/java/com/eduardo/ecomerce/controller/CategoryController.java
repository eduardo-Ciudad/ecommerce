package com.eduardo.ecomerce.controller;


import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import com.eduardo.ecomerce.dto.input.category.CategoryInput;
import com.eduardo.ecomerce.dto.output.category.CategoryOutput;
import com.eduardo.ecomerce.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/categories")
@RequiredArgsConstructor
@Tag(name = "Categorias", description = "Endpoints para gerenciamento de categorias de produtos")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Criar categoria", description = "Cria uma nova categoria. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Categoria criada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para criar categoria"),
            @ApiResponse(responseCode = "422", description = "Erro de regra de negócio")
    })
    public ResponseEntity<CategoryOutput> create(@RequestBody @Valid CategoryInput input) {
        CategoryOutput output = categoryService.create(input);
        return ResponseEntity.status(HttpStatus.CREATED).body(output);
    }

    @GetMapping
    @Operation(summary = "Listar categorias", description = "Retorna a lista de todas as categorias cadastradas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso")
    })
    public ResponseEntity<List<CategoryOutput>> findAll() {
        return ResponseEntity.ok(categoryService.findAll());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar categoria por ID", description = "Retorna os dados de uma categoria específica")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Categoria encontrada"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada")
    })
    public ResponseEntity<CategoryOutput> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(categoryService.findById(id));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload de imagem da categoria", description = "Faz upload de uma imagem (JPEG, PNG ou WebP, máx 5MB) e associa à categoria. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Imagem enviada com sucesso"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "400", description = "Arquivo inválido")
    })
    public ResponseEntity<Map<String, String>> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        String imageUrl = categoryService.uploadImage(id, file);

        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remover categoria", description = "Remove uma categoria pelo ID. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Categoria removida com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover categoria"),
            @ApiResponse(responseCode = "404", description = "Categoria não encontrada"),
            @ApiResponse(responseCode = "422", description = "Categoria possui produtos vinculados")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        categoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
