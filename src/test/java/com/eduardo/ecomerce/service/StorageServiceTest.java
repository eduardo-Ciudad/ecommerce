package com.eduardo.ecomerce.service;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

    @Mock
    private S3Client s3Client;

    @InjectMocks
    private StorageService storageService;


    private void setFields() {
        ReflectionTestUtils.setField(storageService, "bucket", "minimoda-images");
        ReflectionTestUtils.setField(storageService, "publicUrl", "https://pub-test.r2.dev");
    }

    private byte[] createJpegBytes() throws IOException {
        BufferedImage image = new BufferedImage(
                10,
                10,
                BufferedImage.TYPE_INT_RGB
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ImageIO.write(image, "jpg", output);

        return output.toByteArray();
    }

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(
                10,
                10,
                BufferedImage.TYPE_INT_ARGB
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        ImageIO.write(image, "png", output);

        return output.toByteArray();
    }

    @Test
    @DisplayName("Deve fazer upload de imagem JPEG com sucesso")
    void uploadSuccess() throws IOException {

        setFields();

        byte[] jpegBytes = createJpegBytes();

        var file = new MockMultipartFile(
                "file",
                "roupa.jpg",
                "image/jpeg",
                jpegBytes
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(
                PutObjectResponse.builder().build()
        );

        String url = storageService.upload(file, "products");

        assertThat(url)
                .startsWith("https://pub-test.r2.dev/products/");

        assertThat(url)
                .endsWith(".jpg");

        verify(s3Client).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve fazer upload de imagem PNG com sucesso")
    void uploadPngSuccess() throws IOException {

        setFields();

        byte[] pngBytes = createPngBytes();

        var file = new MockMultipartFile(
                "file",
                "foto.png",
                "image/png",
                pngBytes
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(
                PutObjectResponse.builder().build()
        );

        String url = storageService.upload(file, "products");

        assertThat(url)
                .endsWith(".png");

        verify(s3Client).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve rejeitar arquivo vazio")
    void rejectEmptyFile() {

        setFields();

        var file = new MockMultipartFile(
                "file",
                "vazio.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThatThrownBy(() ->
                storageService.upload(file, "products")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arquivo não pode ser vazio");

        verify(
                s3Client,
                never()
        ).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve rejeitar arquivo acima de 5MB")
    void rejectLargeFile() {

        setFields();

        byte[] bigFile = new byte[6 * 1024 * 1024];

        var file = new MockMultipartFile(
                "file",
                "grande.jpg",
                "image/jpeg",
                bigFile
        );

        assertThatThrownBy(() ->
                storageService.upload(file, "products")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Arquivo excede o tamanho máximo de 5MB");

        verify(
                s3Client,
                never()
        ).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve rejeitar tipo de arquivo não permitido")
    void rejectInvalidContentType() {

        setFields();

        var file = new MockMultipartFile(
                "file",
                "documento.pdf",
                "application/pdf",
                new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() ->
                storageService.upload(file, "products")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Tipo de arquivo não permitido. Use: JPEG, PNG ou WebP"
                );

        verify(
                s3Client,
                never()
        ).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve gerar nome único para cada upload")
    void uniqueFilenames() throws IOException {

        setFields();

        byte[] jpegBytes = createJpegBytes();

        var file = new MockMultipartFile(
                "file",
                "foto.jpg",
                "image/jpeg",
                jpegBytes
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(
                PutObjectResponse.builder().build()
        );

        String url1 = storageService.upload(file, "products");

        String url2 = storageService.upload(file, "products");

        assertThat(url1)
                .isNotEqualTo(url2);
    }

    @Test
    @DisplayName("Deve enviar para o bucket correto com content type correto")
    void correctBucketAndContentType() throws IOException {

        setFields();

        byte[] jpegBytes = createJpegBytes();

        var file = new MockMultipartFile(
                "file",
                "foto.jpg",
                "image/jpeg",
                jpegBytes
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(
                PutObjectResponse.builder().build()
        );

        storageService.upload(file, "products");

        ArgumentCaptor<PutObjectRequest> captor =
                ArgumentCaptor.forClass(PutObjectRequest.class);

        verify(s3Client).putObject(
                captor.capture(),
                any(RequestBody.class)
        );

        PutObjectRequest captured = captor.getValue();

        assertThat(captured.bucket())
                .isEqualTo("minimoda-images");

        assertThat(captured.contentType())
                .isEqualTo("image/jpeg");

        assertThat(captured.key())
                .startsWith("products/");
    }

    @Test
    @DisplayName("Deve usar o folder correto na key do S3")
    void usesCorrectFolder() throws IOException {

        setFields();

        byte[] jpegBytes = createJpegBytes();

        var file = new MockMultipartFile(
                "file",
                "foto.jpg",
                "image/jpeg",
                jpegBytes
        );

        when(s3Client.putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        )).thenReturn(
                PutObjectResponse.builder().build()
        );

        String url = storageService.upload(
                file,
                "categories"
        );

        assertThat(url)
                .startsWith(
                        "https://pub-test.r2.dev/categories/"
                );
    }

    @Test
    @DisplayName("Deve rejeitar arquivo com magic bytes inválidos mesmo com content-type correto")
    void rejectInvalidMagicBytes() {

        setFields();

        byte[] pdfBytes = {
                0x25,
                0x50,
                0x44,
                0x46
        };

        var file = new MockMultipartFile(
                "file",
                "fake.jpg",
                "image/jpeg",
                pdfBytes
        );

        assertThatThrownBy(() ->
                storageService.upload(file, "products")
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Conteúdo do arquivo não corresponde a uma imagem válida"
                );

        verify(
                s3Client,
                never()
        ).putObject(
                any(PutObjectRequest.class),
                any(RequestBody.class)
        );
    }

    @Test
    @DisplayName("Deve deletar arquivo do R2 com sucesso")
    void deleteSuccess() {

        setFields();

        storageService.delete(
                "https://pub-test.r2.dev/products/abc-123.jpg"
        );

        ArgumentCaptor<DeleteObjectRequest> captor =
                ArgumentCaptor.forClass(DeleteObjectRequest.class);

        verify(s3Client).deleteObject(
                captor.capture()
        );

        DeleteObjectRequest captured = captor.getValue();

        assertThat(captured.bucket())
                .isEqualTo("minimoda-images");

        assertThat(captured.key())
                .isEqualTo("products/abc-123.jpg");
    }

    @Test
    @DisplayName("Delete não deve propagar exceção em caso de erro")
    void deleteSwallowsException() {

        setFields();

        when(s3Client.deleteObject(
                any(DeleteObjectRequest.class)
        )).thenThrow(
                new RuntimeException("S3 error")
        );

        storageService.delete(
                "https://pub-test.r2.dev/products/abc.jpg"
        );

        verify(s3Client).deleteObject(
                any(DeleteObjectRequest.class)
        );
    }
}
