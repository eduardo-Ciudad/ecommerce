package com.eduardo.ecomerce.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
public class StorageService {

    private final S3Client s3Client;

    @Value("${storage.bucket}")
    private String bucket;

    @Value("${storage.public-url}")
    private String publicUrl;

    private static final List<String> ALLOWED_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final long MAX_SIZE = 5 * 1024 * 1024; // 5MB

    public StorageService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public String upload(MultipartFile file, String folder) {
        validate(file);

        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".")
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".jpg";

        String key = folder + "/" + UUID.randomUUID() + extension;

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(file.getBytes()));

            return publicUrl + "/" + key;

        } catch (IOException e) {
            throw new RuntimeException("Erro ao fazer upload da imagem", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Arquivo não pode ser vazio");
        }

        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("Arquivo excede o tamanho máximo de 5MB");
        }

        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Tipo de arquivo não permitido. Use: JPEG, PNG ou WebP"
            );
        }
    }

}
