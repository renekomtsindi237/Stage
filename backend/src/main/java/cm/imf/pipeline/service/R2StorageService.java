package cm.imf.pipeline.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;

/**
 * Service de stockage Cloudflare R2 (compatible API AWS S3).
 *
 * Utilisé pour stocker les logos IMF de façon persistante sur le CDN Cloudflare.
 * Fallback transparent si R2 n'est pas configuré (utilise le stockage local existant).
 */
@Slf4j
@Service
public class R2StorageService {

    @Value("${app.r2.enabled:false}")
    private boolean enabled;

    @Value("${app.r2.endpoint:}")
    private String endpoint;

    @Value("${app.r2.bucket:imf-ml}")
    private String bucket;

    @Value("${app.r2.access-key:}")
    private String accessKey;

    @Value("${app.r2.secret-key:}")
    private String secretKey;

    @Value("${app.r2.public-url-base:}")
    private String publicUrlBase;

    private S3Client client;

    @PostConstruct
    void init() {
        if (!enabled || endpoint.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            log.info("R2 storage désactivé (R2_ENABLED=false ou credentials manquants) — stockage local utilisé");
            enabled = false;
            return;
        }
        try {
            client = S3Client.builder()
                    .endpointOverride(URI.create(endpoint))
                    .region(Region.of("auto"))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .httpClientBuilder(UrlConnectionHttpClient.builder())
                    .forcePathStyle(true)
                    .build();
            log.info("Cloudflare R2 initialisé — bucket: {}, endpoint: {}", bucket, endpoint);
        } catch (Exception e) {
            log.error("Échec initialisation R2 : {} — stockage local activé en fallback", e.getMessage());
            enabled = false;
        }
    }

    /** true si R2 est configuré et disponible */
    public boolean isAvailable() {
        return enabled && client != null;
    }

    /**
     * Upload un fichier vers R2.
     *
     * @param key         chemin objet (ex: "imf-logos/ABC-uuid.png")
     * @param data        contenu du fichier
     * @param contentType MIME type (ex: "image/png")
     */
    public void upload(String key, byte[] data, String contentType) {
        if (!isAvailable()) throw new IllegalStateException("R2 non disponible");
        try {
            client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) data.length)
                            .build(),
                    RequestBody.fromBytes(data));
            log.info("R2 upload OK : {}/{}", bucket, key);
        } catch (S3Exception e) {
            log.error("R2 upload échoué pour {} : {}", key, e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Erreur upload Cloudflare R2 : " + e.awsErrorDetails().errorMessage(), e);
        }
    }

    /**
     * Télécharge un objet depuis R2.
     * Retourne null si l'objet n'existe pas.
     */
    public byte[] download(String key) {
        if (!isAvailable()) return null;
        try {
            return client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toBytes()).asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            log.warn("R2 download échoué pour {} : {}", key, e.awsErrorDetails().errorMessage());
            return null;
        }
    }

    /**
     * Supprime un objet de R2.
     * Silencieux si l'objet n'existe pas.
     */
    public void delete(String key) {
        if (!isAvailable() || key == null || key.isBlank()) return;
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            log.info("R2 delete OK : {}/{}", bucket, key);
        } catch (S3Exception e) {
            log.warn("R2 delete échoué pour {} : {}", key, e.awsErrorDetails().errorMessage());
        }
    }

    /**
     * Retourne l'URL publique d'un objet R2 si un domaine public est configuré.
     * Sinon retourne null (le proxy backend sera utilisé).
     */
    public String publicUrl(String key) {
        if (publicUrlBase == null || publicUrlBase.isBlank()) return null;
        return publicUrlBase.stripTrailing() + "/" + key;
    }
}
