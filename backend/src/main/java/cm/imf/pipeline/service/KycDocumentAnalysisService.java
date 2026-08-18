package cm.imf.pipeline.service;

import cm.imf.pipeline.entity.KycDocument;
import cm.imf.pipeline.entity.KycDossier;
import cm.imf.pipeline.enums.TypeDocumentKyc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Analyse IA des pièces d'identité KYC scannées (OCR + extraction structurée).
 *
 * Délègue au module Python auto-hébergé {@code pipeline/src/document_extraction}
 * (endpoint {@code POST /document/extraire} du service ml-api) — Tesseract OCR +
 * parsing MRZ ICAO 9303 déterministe, aucune dépendance à une API externe payante.
 *
 * Ne bloque et ne valide JAMAIS automatiquement un document : le résultat
 * (données extraites + écarts) sert uniquement à assister la vérification
 * manuelle du DSI, conformément au principe documenté dans
 * docs/uml/09_sequence_kyc.puml ("Vérification manuelle par le DSI").
 */
@Slf4j
@Service
public class KycDocumentAnalysisService {

    private final RestClient mlRestClient;

    public KycDocumentAnalysisService(@Qualifier("mlRestClient") RestClient mlRestClient) {
        this.mlRestClient = mlRestClient;
    }

    private static final Set<TypeDocumentKyc> TYPES_PIECE_IDENTITE = EnumSet.of(
            TypeDocumentKyc.CNI_RECTO, TypeDocumentKyc.CNI_VERSO,
            TypeDocumentKyc.PASSEPORT, TypeDocumentKyc.PERMIS_CONDUIRE,
            TypeDocumentKyc.CARTE_SEJOUR);

    public boolean estAnalysable(TypeDocumentKyc type, String mimeType) {
        return TYPES_PIECE_IDENTITE.contains(type)
                && mimeType != null && mimeType.startsWith("image/");
    }

    /**
     * Exigences d'extraction par niveau KYC (champs requis, MRZ exigée ou non,
     * documents complémentaires) — source unique de vérité côté module
     * document_extraction, exposée au frontend pour afficher précisément ce
     * qui manque encore pour compléter un niveau donné.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> exigencesKycParNiveau() {
        try {
            Map<String, Object> resultat = mlRestClient.get()
                    .uri("/document/niveaux-kyc")
                    .retrieve()
                    .body(Map.class);
            return resultat != null ? resultat : Map.of();
        } catch (RestClientException e) {
            log.warn("Impossible de récupérer les exigences KYC par niveau : {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * Analyse le document et renseigne directement les champs IA de l'entité
     * (donneesExtraites, ecartsDetectes, analyseIaAt, analyseIaErreur).
     * Ne lève jamais d'exception — un échec d'analyse n'empêche jamais l'upload du document.
     */
    @SuppressWarnings("unchecked")
    public void analyser(KycDocument doc, byte[] fileBytes, String mimeType, KycDossier dossier) {
        doc.setAnalyseIaAt(OffsetDateTime.now());

        try {
            Map<String, Object> requete = Map.of(
                    "type_piece", doc.getTypeDocument().name(),
                    "contenu_base64", Base64.getEncoder().encodeToString(fileBytes)
            );

            Map<String, Object> resultat = mlRestClient.post()
                    .uri("/document/extraire")
                    .body(requete)
                    .retrieve()
                    .body(Map.class);

            if (resultat == null) {
                doc.setAnalyseIaErreur("Réponse vide du service d'extraction de documents.");
                return;
            }

            var champsBruts = (Map<String, Map<String, Object>>) resultat.getOrDefault("champs", Map.of());
            Map<String, Object> donnees = new LinkedHashMap<>();
            champsBruts.forEach((nomChamp, detail) -> {
                Object valeur = detail.get("valeur");
                if (valeur != null) {
                    donnees.put(nomChamp, valeur);
                }
            });

            doc.setDonneesExtraites(donnees);
            doc.setEcartsDetectes(comparerAuDossier(donnees, dossier));

            List<String> erreursExtraction = (List<String>) resultat.get("erreurs");
            if (erreursExtraction != null && !erreursExtraction.isEmpty() && donnees.isEmpty()) {
                doc.setAnalyseIaErreur(String.join(" ; ", erreursExtraction));
            }

        } catch (RestClientException e) {
            log.warn("Service d'extraction de documents indisponible pour document KYC {} : {}",
                    doc.getUid(), e.getMessage());
            doc.setAnalyseIaErreur("Analyse indisponible : service d'extraction injoignable.");
        } catch (Exception e) {
            log.warn("Analyse IA échouée pour document KYC {} : {}", doc.getUid(), e.getMessage());
            doc.setAnalyseIaErreur("Analyse indisponible : " + e.getMessage());
        }
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
