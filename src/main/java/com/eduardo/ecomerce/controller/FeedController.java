package com.eduardo.ecomerce.controller;

import com.eduardo.ecomerce.service.feed.GoogleFeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/feeds")
@RequiredArgsConstructor
@Tag(name = "Feeds", description = "Feeds de catálogo para plataformas externas")
public class FeedController {

    private final GoogleFeedService googleFeedService;

    @GetMapping(value = "/google.xml", produces = MediaType.APPLICATION_XML_VALUE)
    @Operation(summary = "Feed do Google Merchant Center",
            description = "RSS 2.0 com uma entrada por variante de produto ativo. Público.")
    public ResponseEntity<String> googleFeed() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(googleFeedService.buildGoogleFeed());
    }
}