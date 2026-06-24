package cm.imf.pipeline.controller;

import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

/**
 * Endpoints publics (sans authentification) pour les ressources partagées.
 * Accessible via /api/v1/public/** (voir SecurityConfig).
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Slf4j
public class PublicController {

    private final ImfRepository    imfRepository;
    private final R2StorageService r2;

    @Value("${app.upload.dir:/uploads}")
    private String uploadDir;

    /**
     * Sert le logo d'une IMF par son code.
     * Cherche d'abord dans R2, puis en local.
     * Cache-Control : 24h côté navigateur pour réduire les requêtes.
     */
    @GetMapping("/imf/{code}/logo")
    public ResponseEntity<byte[]> getImfLogo(@PathVariable String code) {
        Imf imf = imfRepository.findByCode(code.toUpperCase()).orElse(null);
        if (imf == null || imf.getLogoUrl() == null) {
            return ResponseEntity.notFound().build();
        }

        byte[]      data        = null;
        MediaType   contentType = MediaType.IMAGE_PNG;

        // 1. Essayer R2
        if (r2.isAvailable() && imf.getLogoR2Key() != null) {
            data = r2.download(imf.getLogoR2Key());
            if (data != null) {
                contentType = detectMediaType(imf.getLogoR2Key());
            }
        }

        // 2. Fallback : fichier local
        if (data == null && imf.getLogoUrl() != null && imf.getLogoUrl().startsWith("/api/uploads/")) {
            String relativePath = imf.getLogoUrl().substring("/api/uploads/".length());
            try {
                data        = Files.readAllBytes(Paths.get(uploadDir, relativePath));
                contentType = detectMediaType(relativePath);
            } catch (IOException e) {
                log.warn("Logo local introuvable pour IMF {} : {}", code, relativePath);
            }
        }

        if (data == null) {
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(24)).cachePublic());
        headers.setContentLength(data.length);

        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    /**
     * Sert l'image de profil par défaut (profile.png embarqué dans le jar).
     * Retournée quand un utilisateur n'a pas encore défini son avatar.
     * Cache-Control : 7 jours — l'image ne change qu'avec un déploiement.
     */
    @GetMapping("/default-avatar")
    public ResponseEntity<byte[]> getDefaultAvatar() {
        try {
            ClassPathResource res = new ClassPathResource("static/profile.png");
            byte[] data = res.getInputStream().readAllBytes();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
            headers.setContentLength(data.length);
            return new ResponseEntity<>(data, headers, HttpStatus.OK);
        } catch (IOException e) {
            log.error("profile.png manquant dans le classpath static/");
            return ResponseEntity.notFound().build();
        }
    }

    private MediaType detectMediaType(String key) {
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (key.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (key.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (key.endsWith(".gif"))  return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_PNG;
    }
}
