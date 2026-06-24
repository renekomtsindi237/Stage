package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.request.*;
import cm.imf.pipeline.dto.response.*;
import cm.imf.pipeline.entity.*;
import cm.imf.pipeline.enums.*;
import cm.imf.pipeline.repository.*;
import cm.imf.pipeline.service.IKycService;
import cm.imf.pipeline.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class KycServiceImpl implements IKycService {

    private final KycDossierRepository      dossierRepo;
    private final KycDocumentRepository     documentRepo;
    private final KycVerificationRepository verificationRepo;
    private final R2StorageService          r2;

    // ── Initier un dossier KYC ────────────────────────────────────────────────

    @Override
    @Transactional
    public KycDossierResponse initierDossier(InitierKycRequest req, User currentUser) {
        requireTenant(currentUser);
        Long imfId = currentUser.getImf().getId();

        if (dossierRepo.findByImfIdAndClientId(imfId, req.clientId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un dossier KYC existe déjà pour le client " + req.clientId());
        }

        KycDossier dossier = KycDossier.builder()
                .imf(currentUser.getImf())
                .clientId(req.clientId())
                .nomClient(req.nomClient())
                .prenomClient(req.prenomClient())
                .dateNaissance(req.dateNaissance())
                .lieuNaissance(req.lieuNaissance())
                .nationalite(req.nationalite() != null ? req.nationalite() : "Camerounaise")
                .telephone(req.telephone())
                .email(req.email())
                .adresse(req.adresse())
                .ville(req.ville())
                .profession(req.profession())
                .employeur(req.employeur())
                .revenuMensuelEstim(req.revenuMensuelEstim())
                .typePieceIdentite(req.typePieceIdentite())
                .numeroPiece(req.numeroPiece())
                .dateEmissionPiece(req.dateEmissionPiece())
                .dateExpirationPiece(req.dateExpirationPiece())
                .lieuEmissionPiece(req.lieuEmissionPiece())
                .niveauDemande(req.niveauDemande())
                .estPep(req.estPep())
                .observations(req.observations())
                .build();

        // scorerRisque() + dateExpiration calculés dans @PrePersist
        dossier = dossierRepo.save(dossier);

        log.info("Dossier KYC initié — client={} imf={} niveau={} risque={}",
                req.clientId(), imfId, req.niveauDemande(), dossier.getNiveauRisque());
        return KycDossierResponse.from(dossier);
    }

    // ── Lister les dossiers ───────────────────────────────────────────────────

    @Override
    public PageResponse<KycDossierResponse> listDossiers(
            Long imfId, StatutKyc statut, NiveauKyc niveau, NiveauRisque risque, int page, int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());
        Page<KycDossier> result;

        if (statut != null && niveau != null) {
            result = dossierRepo.findByImfIdAndStatutAndNiveau(imfId, statut, niveau, pageable);
        } else if (statut != null) {
            result = dossierRepo.findByImfIdAndStatut(imfId, statut, pageable);
        } else if (niveau != null) {
            result = dossierRepo.findByImfIdAndNiveau(imfId, niveau, pageable);
        } else if (risque != null) {
            result = dossierRepo.findByImfIdAndRisque(imfId, risque, pageable);
        } else {
            result = dossierRepo.findByImfId(imfId, pageable);
        }

        return PageResponse.from(result, KycDossierResponse::from);
    }

    // ── Détail d'un dossier ───────────────────────────────────────────────────

    @Override
    public KycDossierResponse getDossier(UUID uid) {
        return KycDossierResponse.from(findOrThrow(uid));
    }

    // ── Évaluation du risque ──────────────────────────────────────────────────

    @Override
    @Transactional
    public KycDossierResponse evaluerRisque(UUID uid, EvaluerRisqueKycRequest req, User currentUser) {
        KycDossier dossier = findOrThrow(uid);

        dossier.setEstPep(req.estPep());
        dossier.setVerifSanctions(req.verifSanctions());
        dossier.setVerifListesNoires(req.verifListesNoires());
        dossier.setMotifRisqueEleve(req.motifRisqueEleve());
        dossier.setDateDernierAudit(OffsetDateTime.now());
        if (req.observations() != null) dossier.setObservations(req.observations());

        // Si un score manuel est fourni (audit externe), il prend la priorité
        if (req.scoreManuel() != null) {
            dossier.setScoreRisque(req.scoreManuel());
            dossier.setNiveauRisque(NiveauRisque.of(req.scoreManuel()));
        }
        // Sinon recalculé automatiquement dans @PreUpdate

        dossier = dossierRepo.save(dossier);

        // PPE → forcer niveau 3 automatiquement (exigence COBAC R-2005/01 art. 14)
        if (req.estPep() && dossier.getNiveauDemande().ordinal() < NiveauKyc.NIVEAU_3.ordinal()) {
            dossier.setNiveauDemande(NiveauKyc.NIVEAU_3);
            dossierRepo.save(dossier);
            log.warn("Client PPE détecté — niveau KYC auto-élevé à NIVEAU_3 (dossier={})", uid);
        }

        log.info("Risque évalué — dossier={} score={} risque={} pep={}",
                uid, dossier.getScoreRisque(), dossier.getNiveauRisque(), req.estPep());
        return KycDossierResponse.from(dossier);
    }

    // ── Soumettre un document ─────────────────────────────────────────────────

    @Override
    @Transactional
    public KycDocumentResponse soumettreDocument(UUID dossierUid, SoumettreDocumentKycRequest req, User currentUser) {
        KycDossier dossier = findOrThrow(dossierUid);

        // Vérification de la taille selon le plafond configuré pour cet IMF
        long estimatedOctets = (long) (req.contenuBase64().length() * 0.75);
        long maxOctets = dossier.getImf().getMaxDocumentKycOctets();
        if (estimatedOctets > maxOctets) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    String.format("Document trop volumineux : max %d Mo autorisé pour %s",
                            maxOctets / 1_048_576, dossier.getImf().getCode()));
        }

        // Décoder le base64 → bytes pour upload R2
        byte[] fileBytes = Base64.getDecoder().decode(req.contenuBase64());
        String mimeType = req.mimeType() != null ? req.mimeType() : "application/octet-stream";

        KycDocument doc = KycDocument.builder()
                .dossier(dossier)
                .typeDocument(req.typeDocument())
                .nomFichier(req.nomFichier())
                .mimeType(mimeType)
                .tailleOctets(req.tailleOctets() != null ? req.tailleOctets() : (long) fileBytes.length)
                .dateExpirationDoc(req.dateExpirationDoc())
                .build();

        // Sauvegarde initiale pour obtenir l'UID du document
        doc = documentRepo.save(doc);

        if (r2.isAvailable()) {
            // Upload vers R2 : kyc-docs/{imfCode}/{dossierUid}/{docUid}-{nomFichier}
            String ext       = extensionDe(req.nomFichier(), mimeType);
            String imfCode   = dossier.getImf().getCode();
            String r2Key     = String.format("kyc-docs/%s/%s/%s%s",
                    imfCode, dossierUid, doc.getUid(), ext);
            try {
                r2.upload(r2Key, fileBytes, mimeType);
                doc.setCheminStockage(r2Key);
                doc.setContenuBase64(null);
                log.info("KYC document {} uploadé sur R2 : {}", doc.getUid(), r2Key);
            } catch (Exception e) {
                log.warn("R2 indisponible pour doc KYC {} — fallback base64 : {}", doc.getUid(), e.getMessage());
                doc.setContenuBase64(req.contenuBase64());
            }
        } else {
            // Fallback PostgreSQL base64 si R2 non configuré
            doc.setContenuBase64(req.contenuBase64());
        }

        doc = documentRepo.save(doc);

        // Passer le dossier en DOCUMENTS_SOUMIS si nécessaire
        if (dossier.getStatut() == StatutKyc.EN_ATTENTE || dossier.getStatut() == StatutKyc.COMPLEMENT_REQUIS) {
            dossier.setStatut(StatutKyc.DOCUMENTS_SOUMIS);
            dossierRepo.save(dossier);
        }

        log.info("Document {} soumis pour le dossier KYC {} (r2={})",
                req.typeDocument(), dossierUid, r2.isAvailable());
        return KycDocumentResponse.from(doc);
    }

    // ── Télécharger le contenu d'un document ─────────────────────────────────

    @Override
    public DocumentContenu telechargerContenu(UUID documentUid) {
        KycDocument doc = documentRepo.findByUid(documentUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Document KYC introuvable : " + documentUid));

        byte[] data = null;

        if (doc.getCheminStockage() != null && !doc.getCheminStockage().isBlank()) {
            data = r2.download(doc.getCheminStockage());
            if (data == null) {
                log.warn("Document KYC {} introuvable sur R2 (clé: {})", documentUid, doc.getCheminStockage());
            }
        }

        if (data == null && doc.getContenuBase64() != null && !doc.getContenuBase64().isBlank()) {
            try {
                data = Base64.getDecoder().decode(doc.getContenuBase64());
            } catch (Exception e) {
                log.warn("Contenu base64 invalide pour document KYC {}", documentUid);
            }
        }

        if (data == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Contenu du document indisponible.");
        }

        String mime = doc.getMimeType() != null ? doc.getMimeType() : "application/octet-stream";
        return new DocumentContenu(data, mime, doc.getNomFichier());
    }

    // ── Lister les documents d'un dossier ─────────────────────────────────────

    @Override
    public List<KycDocumentResponse> getDocuments(UUID dossierUid) {
        KycDossier dossier = findOrThrow(dossierUid);
        return documentRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(KycDocumentResponse::from).toList();
    }

    // ── Valider / Rejeter un document ─────────────────────────────────────────

    @Override
    @Transactional
    public KycDocumentResponse validerDocument(UUID documentUid, ValiderDocumentKycRequest req, User currentUser) {
        KycDocument doc = documentRepo.findByUid(documentUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document introuvable : " + documentUid));

        if (!req.valide() && (req.motifRejet() == null || req.motifRejet().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un motif de rejet est obligatoire.");
        }

        doc.setValide(req.valide());
        doc.setMotifRejet(req.valide() ? null : req.motifRejet());
        doc.setVerifiePar(currentUser);
        doc.setDateVerification(OffsetDateTime.now());
        doc = documentRepo.save(doc);

        log.info("Document {} {} par {}", documentUid, req.valide() ? "validé" : "rejeté", currentUser.getUsername());
        return KycDocumentResponse.from(doc);
    }

    // ── Vérification finale du dossier ────────────────────────────────────────

    @Override
    @Transactional
    public KycDossierResponse verifier(UUID dossierUid, VerifierKycRequest req, User currentUser) {
        KycDossier dossier = findOrThrow(dossierUid);

        if (req.resultat() == ResultatVerificationKyc.REJETE
                && (req.motifRejet() == null || req.motifRejet().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Un motif de rejet est obligatoire.");
        }

        StatutKyc ancienStatut = dossier.getStatut();
        NiveauKyc ancienNiveau = dossier.getNiveauActuel();

        switch (req.resultat()) {
            case APPROUVE -> {
                dossier.setStatut(StatutKyc.APPROUVE);
                NiveauKyc niveauApprouve = req.niveauApprouve() != null ? req.niveauApprouve() : dossier.getNiveauDemande();
                dossier.setNiveauActuel(niveauApprouve);
                dossier.setVerificateur(currentUser);
                dossier.setDateVerification(OffsetDateTime.now());
                // Expiration KYC : 2 ans pour Niveau 1-2, 1 an pour PPE/Niveau 3
                int dureeAns = (dossier.isEstPep() || niveauApprouve == NiveauKyc.NIVEAU_3) ? 1 : 2;
                dossier.setDateExpirationKyc(LocalDate.now().plusYears(dureeAns));
            }
            case REJETE -> {
                dossier.setStatut(StatutKyc.REJETE);
                dossier.setObservations(req.motifRejet());
                dossier.setVerificateur(currentUser);
                dossier.setDateVerification(OffsetDateTime.now());
            }
            case COMPLEMENT_REQUIS -> {
                dossier.setStatut(StatutKyc.COMPLEMENT_REQUIS);
                dossier.setObservations(req.commentaire());
            }
        }

        dossier = dossierRepo.save(dossier);

        // Journaliser dans l'historique de vérifications
        KycVerification verif = KycVerification.builder()
                .dossier(dossier)
                .verificateur(currentUser)
                .ancienStatut(ancienStatut)
                .nouveauStatut(dossier.getStatut())
                .ancienNiveau(ancienNiveau)
                .nouveauNiveau(dossier.getNiveauActuel())
                .resultat(req.resultat())
                .commentaire(req.commentaire())
                .motifRejet(req.motifRejet())
                .build();
        verificationRepo.save(verif);

        log.info("KYC {} — dossier={} statut={} niveau={} par {}",
                req.resultat(), dossierUid, dossier.getStatut(), dossier.getNiveauActuel(), currentUser.getUsername());
        return KycDossierResponse.from(dossier);
    }

    // ── Historique de vérifications ───────────────────────────────────────────

    @Override
    public List<KycVerificationResponse> getHistoriqueVerifications(UUID dossierUid) {
        KycDossier dossier = findOrThrow(dossierUid);
        return verificationRepo.findByDossierIdOrderByCreatedAtDesc(dossier.getId())
                .stream().map(KycVerificationResponse::from).toList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private KycDossier findOrThrow(UUID uid) {
        return dossierRepo.findByUid(uid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Dossier KYC introuvable : " + uid));
    }

    private void requireTenant(User user) {
        if (user.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès refusé : SUPER_ADMIN ne peut pas gérer les KYC.");
        }
    }

    private static String extensionDe(String nomFichier, String mimeType) {
        if (nomFichier != null && nomFichier.contains(".")) {
            return nomFichier.substring(nomFichier.lastIndexOf('.'));
        }
        return switch (mimeType) {
            case "application/pdf"  -> ".pdf";
            case "image/jpeg"       -> ".jpg";
            case "image/png"        -> ".png";
            case "image/webp"       -> ".webp";
            default                 -> "";
        };
    }
}
