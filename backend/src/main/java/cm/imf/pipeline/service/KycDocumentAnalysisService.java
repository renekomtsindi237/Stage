package cm.imf.pipeline.service;

import cm.imf.pipeline.entity.KycDocument;
import cm.imf.pipeline.entity.KycDossier;
import cm.imf.pipeline.enums.TypeDocumentKyc;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Analyse IA des pièces d'identité KYC scannées (OCR + extraction structurée).
 *
 * Utilise un modèle vision Groq (API compatible OpenAI, même clé que le
 * chatbot IA — cf. AiChatController) pour lire les champs visibles sur la
 * pièce (nom, date de naissance, numéro, dates d'émission/expiration) et les
 * compare aux champs saisis dans le dossier KYC.
 *
 * Ne bloque et ne valide JAMAIS automatiquement un document : le résultat
 * (données extraites + écarts) sert uniquement à assister la vérification
 * manuelle du DSI, conformément au principe documenté dans
 * docs/uml/09_sequence_kyc.puml ("Vérification manuelle par le DSI").
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KycDocumentAnalysisService {

    private final ObjectMapper mapper;

    @Value("${imf.ai.api-key:}")
    private String apiKey;

    @Value("${imf.ai.base-url:https://api.groq.com/openai/v1}")
    private String baseUrl;

    /** Modèle Groq à capacité vision — configurable sans redéploiement de code. */
    @Value("${imf.ai.vision-model:meta-llama/llama-4-scout-17b-16e-instruct}")
    private String visionModel;

    private static final Set<TypeDocumentKyc> TYPES_PIECE_IDENTITE = EnumSet.of(
            TypeDocumentKyc.CNI_RECTO, TypeDocumentKyc.CNI_VERSO,
            TypeDocumentKyc.PASSEPORT, TypeDocumentKyc.PERMIS_CONDUIRE,
            TypeDocumentKyc.CARTE_SEJOUR);

    private static final String PROMPT = """
            Tu analyses le scan d'une pièce d'identité camerounaise. Lis attentivement
            tous les champs visibles et renvoie UNIQUEMENT un objet JSON strict (pas de
            texte autour, pas de markdown) avec exactement ces clés — mets null si un
            champ n'est pas visible sur cette face du document :
            {
              "nom": string ou null,
              "prenom": string ou null,
              "dateNaissance": string ou null (format AAAA-MM-JJ),
              "lieuNaissance": string ou null,
              "numeroPiece": string ou null,
              "dateEmissionPiece": string ou null (format AAAA-MM-JJ),
              "dateExpirationPiece": string ou null (format AAAA-MM-JJ),
              "lieuEmissionPiece": string ou null
            }
            """;

    public boolean estAnalysable(TypeDocumentKyc type, String mimeType) {
        return TYPES_PIECE_IDENTITE.contains(type)
                && mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Analyse le document et renseigne directement les champs IA de l'entité
     * (donneesExtraites, ecartsDetectes, analyseIaAt, analyseIaErreur).
     * Ne lève jamais d'exception — un échec IA n'empêche jamais l'upload du document.
     */
    public void analyser(KycDocument doc, byte[] fileBytes, String mimeType, KycDossier dossier) {
        doc.setAnalyseIaAt(OffsetDateTime.now());

        if (apiKey == null || apiKey.isBlank()) {
            doc.setAnalyseIaErreur("Analyse IA non configurée (GROQ_API_KEY absent).");
            return;
        }
        try {
            Map<String, Object> extrait = extraireChamps(fileBytes, mimeType);
            doc.setDonneesExtraites(extrait);
            doc.setEcartsDetectes(comparerAuDossier(extrait, dossier));
        } catch (Exception e) {
            log.warn("Analyse IA échouée pour document KYC {} : {}", doc.getUid(), e.getMessage());
            doc.setAnalyseIaErreur("Analyse indisponible : " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extraireChamps(byte[] fileBytes, String mimeType) throws Exception {
        String dataUri = "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(fileBytes);

        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        List<Map<String, Object>> content = List.of(
                Map.of("type", "text", "text", PROMPT),
                Map.of("type", "image_url", "image_url", Map.of("url", dataUri))
        );
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", visionModel);
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        body.put("max_tokens", 500);
        body.put("temperature", 0.0);
        body.put("response_format", Map.of("type", "json_object"));

        var resp = new RestTemplate().exchange(
                baseUrl + "/chat/completions",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class
        );

        var choices = (List<Map<String, Object>>) resp.getBody().get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");
        String jsonContent = (String) message.get("content");
        return mapper.readValue(jsonContent, Map.class);
    }

    private List<Map<String, Object>> comparerAuDossier(Map<String, Object> extrait, KycDossier dossier) {
        List<Map<String, Object>> ecarts = new ArrayList<>();
        comparerChamp(ecarts, "nom", extrait.get("nom"), dossier.getNomClient());
        comparerChamp(ecarts, "prenom", extrait.get("prenom"), dossier.getPrenomClient());
        comparerChamp(ecarts, "dateNaissance", extrait.get("dateNaissance"),
                dossier.getDateNaissance() != null ? dossier.getDateNaissance().toString() : null);
        comparerChamp(ecarts, "numeroPiece", extrait.get("numeroPiece"), dossier.getNumeroPiece());
        comparerChamp(ecarts, "dateEmissionPiece", extrait.get("dateEmissionPiece"),
                dossier.getDateEmissionPiece() != null ? dossier.getDateEmissionPiece().toString() : null);
        comparerChamp(ecarts, "dateExpirationPiece", extrait.get("dateExpirationPiece"),
                dossier.getDateExpirationPiece() != null ? dossier.getDateExpirationPiece().toString() : null);
        return ecarts;
    }

    private void comparerChamp(List<Map<String, Object>> ecarts, String champ, Object valeurDetectee, String valeurSaisie) {
        if (valeurDetectee == null || valeurSaisie == null || valeurSaisie.isBlank()) return;
        String detectee = valeurDetectee.toString().trim();
        String saisie = valeurSaisie.trim();
        if (!detectee.equalsIgnoreCase(saisie)) {
            ecarts.add(Map.of(
                    "champ", champ,
                    "valeurSaisie", saisie,
                    "valeurDetectee", detectee
            ));
        }
    }
}
