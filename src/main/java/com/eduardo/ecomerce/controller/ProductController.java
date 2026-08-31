package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.dto.input.product.ProductInput;
import com.eduardo.ecomerce.dto.output.common.PageResponse;
import com.eduardo.ecomerce.dto.output.product.ProductOutput;
import com.eduardo.ecomerce.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@Tag(name = "Produtos", description = "Endpoints para gerenciamento de produtos")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @Operation(summary = "Criar produto", description = "Cria um novo produto. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Produto criado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para criar produto"),
            @ApiResponse(responseCode = "422", description = "Erro de regra de negócio")
    })
    public ResponseEntity<ProductOutput> create(@RequestBody @Valid ProductInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.create(input));
    }

    @GetMapping
    @Operation(summary = "Listar produtos ativos", description = "Retorna a lista paginada de produtos ativos cadastrados")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página retornada com sucesso")
    })
    public ResponseEntity<PageResponse<ProductOutput>> findAll(
            @PageableDefault(size = 20) Pageable pageable,
            @RequestParam(name = "includeWithoutImage", defaultValue = "false") boolean includeWithoutImage) {
        return ResponseEntity.ok(productService.findAllActive(pageable, includeWithoutImage));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar produto por ID", description = "Retorna os dados de um produto específico pelo seu ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto encontrado"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    })
    public ResponseEntity<ProductOutput> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(productService.findById(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Atualizar produto", description = "Atualiza os dados de um produto existente. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Produto atualizado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para atualizar produto"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    })
    public ResponseEntity<ProductOutput> update(@PathVariable UUID id, @RequestBody @Valid ProductInput input) {
        return ResponseEntity.ok(productService.update(id, input));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Desativar produto", description = "Realiza soft delete do produto, marcando-o como inativo. Requer perfil ADMIN.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Produto desativado com sucesso"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para desativar produto"),
            @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Upload de imagem do produto", description = "Faz upload de uma imagem (JPEG, PNG ou WebP, máx 5MB) e associa ao produto")
    @ApiResponse(responseCode = "200", description = "Imagem enviada com sucesso")
    @ApiResponse(responseCode = "404", description = "Produto não encontrado")
    @ApiResponse(responseCode = "400", description = "Arquivo inválido (tipo não permitido, vazio ou acima de 5MB)")
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        String imageUrl = productService.uploadImage(id, file);

        return ResponseEntity.ok(Map.of("imageUrl", imageUrl));
    }
}
