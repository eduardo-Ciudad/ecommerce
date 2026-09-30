package com.eduardo.ecomerce.service;


import com.eduardo.ecomerce.infra.bling.BlingRequestThrottler;
import com.eduardo.ecomerce.infra.http.AppRestClientFactory;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;


@Slf4j
@Service
public class BlingImageSyncService {

    private static final int MAIN_MAX_DIMENSION = 1200;
    private static final float MAIN_QUALITY = 0.85f;
    private static final int THUMB_MAX_DIMENSION = 400;
    private static final float THUMB_QUALITY = 0.8f;
    private static final String KEY_PREFIX = "products";

    private final RestClient downloadClient;
    private final StorageService storageService;
    private final BlingRequestThrottler throttler;

    public BlingImageSyncService(AppRestClientFactory restClientFactory, StorageService storageService, BlingRequestThrottler throttler) {
        this.downloadClient = restClientFactory.create("");
        this.storageService = storageService;
        this.throttler = throttler;
    }


    public record SourceImage(String url, int displayOrder) {}

    public record UploadedImage(String url, String thumbnailUrl, int displayOrder) {}


    public List<UploadedImage> syncImages(Long blingProductId, String color, List<SourceImage> images) {
        List<UploadedImage> result = new ArrayList<>();

        for (SourceImage image : images) {
            try {
                byte[] original = download(image.url());

                byte[] main = compress(original, MAIN_MAX_DIMENSION, MAIN_QUALITY);
                byte[] thumb = compress(original, THUMB_MAX_DIMENSION, THUMB_QUALITY);

                String mainKey = objectKey(blingProductId, color, image.displayOrder(), false);
                String thumbKey = objectKey(blingProductId, color, image.displayOrder(), true);

                String mainUrl = storageService.uploadBytes(main, "image/jpeg", mainKey);
                String thumbUrl = storageService.uploadBytes(thumb, "image/jpeg", thumbKey);

                result.add(new UploadedImage(mainUrl, thumbUrl, image.displayOrder()));
            } catch (Exception e) {
                log.warn(
                        "Falha ao migrar imagem do Bling pro R2 (blingProductId={}, cor={}, displayOrder={}): {}",
                        blingProductId, color, image.displayOrder(), e.getMessage()
                );
            }
        }

        return result;
    }

    private byte[] download(String url) {
        throttler.throttle();
        return downloadClient.get()
                .uri(URI.create(url))
                .retrieve()
                .body(byte[].class);
    }

    private byte[] compress(byte[] original, int maxDimension, float quality) throws IOException {
        BufferedImage sourceImage = ImageIO.read(new ByteArrayInputStream(original));
        if (sourceImage == null) {
            String hex = original.length >= 12
                    ? bytesToHex(Arrays.copyOfRange(original, 0, 12))
                    : bytesToHex(original);
            throw new IOException(
                    "Não foi possível decodificar a imagem (tamanho=%d bytes, primeiros bytes=%s)"
                            .formatted(original.length, hex)
            );
        }

        // não faz upscale: se a imagem original já é menor que o alvo, só recomprime no tamanho dela
        int targetWidth = Math.min(sourceImage.getWidth(), maxDimension);
        int targetHeight = Math.min(sourceImage.getHeight(), maxDimension);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thumbnails.of(sourceImage)
                .size(targetWidth, targetHeight)
                .outputFormat("jpg")
                .outputQuality(quality)
                .toOutputStream(output);

        return output.toByteArray();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    static String objectKey(Long blingProductId, String color, int displayOrder, boolean thumbnail) {
        String suffix = thumbnail ? "_thumb" : "";
        String colorSlug = slugify(color);
        return colorSlug == null
                ? "%s/%d/%d%s.jpg".formatted(KEY_PREFIX, blingProductId, displayOrder, suffix)
                : "%s/%d/%s/%d%s.jpg".formatted(KEY_PREFIX, blingProductId, colorSlug, displayOrder, suffix);
    }

    private static String slugify(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String slug = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? null : slug;
    }
}
