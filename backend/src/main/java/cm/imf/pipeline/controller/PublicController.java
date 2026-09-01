package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.SseEventDto;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.TicketSupport;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.TicketSupportRepository;
import cm.imf.pipeline.service.EmailService;
import cm.imf.pipeline.service.R2StorageService;
import cm.imf.pipeline.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Endpoints publics (sans authentification) pour les ressources partagées.
 * Accessible via /api/v1/public/** (voir SecurityConfig).
 */
@RestController
@RequestMapping("/public")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Public", description = "Ressources et contact support sans authentification")
public class PublicController {

    private final ImfRepository           imfRepository;
    private final R2StorageService        r2;
    private final TicketSupportRepository ticketRepo;
    private final SseEmitterRegistry      sseRegistry;
    private final EmailService            emailService;

    // ── DTO contact support ───────────────────────────────────────────────────

    record ContactSupportRequest(
            @NotBlank @Size(max = 100) String nom,
            @NotBlank @Email           String email,
            @NotBlank @Size(max = 200) String sujet,
            @NotBlank @Size(max = 2000) String message,
            String categorie
    ) {}

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
        if (data == null && imf.getLogoUrl() != null
                && (imf.getLogoUrl().startsWith("/api/uploads/")
                    || imf.getLogoUrl().startsWith("/api/v1/uploads/"))) {
            String relativePath = imf.getLogoUrl().contains("/uploads/")
                    ? imf.getLogoUrl().substring(imf.getLogoUrl().indexOf("/uploads/") + "/uploads/".length())
                    : imf.getLogoUrl().substring("/api/uploads/".length());
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
            log.warn("profile.png manquant dans le classpath static/ — fallback PNG minimal");
            byte[] fallback = java.util.Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic());
            headers.setContentLength(fallback.length);
            return new ResponseEntity<>(fallback, headers, HttpStatus.OK);
        }
    }

    /**
     * Proxy avatar R2 lorsque aucun domaine public n'est configuré.
     * Clé attendue : avatars/{userId}/{uuid}.ext
     */
    @GetMapping("/avatar/{*key}")
    public ResponseEntity<byte[]> getStoredAvatar(@PathVariable String key) {
        String clean = key.startsWith("/") ? key.substring(1) : key;
        if (!clean.startsWith("avatars/") || clean.contains("..")) {
            return ResponseEntity.notFound().build();
        }
        byte[] data = r2.download(clean);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(detectMediaType(clean));
        headers.setCacheControl(CacheControl.maxAge(Duration.ofHours(12)).cachePublic());
        headers.setContentLength(data.length);
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    // ── POST /api/v1/public/contact-support ──────────────────────────────────

    @Operation(summary = "Contacter le support depuis la page de connexion (sans authentification)")
    @PostMapping("/contact-support")
    public ResponseEntity<ApiResponse<Map<String, String>>> contactSupport(
            @Valid @RequestBody ContactSupportRequest req) {

        String cat   = req.categorie() != null ? req.categorie() : "AUTRE";
        String titre = "[PUBLIC] " + req.sujet();
        String desc  = "De : " + req.nom() + " <" + req.email() + ">\n\n" + req.message();

        TicketSupport ticket = TicketSupport.builder()
                .imfId(null)
                .auteurId(null)
                .auteurUsername(req.nom())
                .auteurRole("PUBLIC")
                .titre(titre)
                .description(desc)
                .categorie(cat)
                .priorite("NORMALE")
                .statut("OUVERT")
                .build();
        ticketRepo.save(ticket);

        String msg = "Contact public de " + req.nom() + " — " + req.sujet();
        sseRegistry.broadcastToRole("SUPPORT", new SseEventDto(
                "NOUVEAU_TICKET", "SUPPORT", msg,
                Map.of(
                        "ticketId",  ticket.getId(),
                        "uid",       ticket.getUid().toString(),
                        "categorie", cat,
                        "priorite",  "NORMALE",
                        "auteur",    req.nom() + " <" + req.email() + ">"
                ),
                OffsetDateTime.now()
        ));

        emailService.sendContactSupportConfirmation(
                req.email(), req.nom(), req.sujet(), ticket.getUid().toString());

        log.info("Contact public de {} <{}> — sujet: {}", req.nom(), req.email(), req.sujet());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Votre message a été transmis au support.",
                        Map.of("reference", ticket.getUid().toString())));
    }

    // ── Helpers médias ────────────────────────────────────────────────────────

    private MediaType detectMediaType(String key) {
        if (key.endsWith(".jpg") || key.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (key.endsWith(".png"))  return MediaType.IMAGE_PNG;
        if (key.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (key.endsWith(".gif"))  return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_PNG;
    }
}
