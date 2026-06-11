package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.EncaissementRequest;
import cm.imf.pipeline.dto.request.ExecuterDecaissementRequest;
import cm.imf.pipeline.dto.request.GenererContratRequest;
import cm.imf.pipeline.dto.response.ContratCreditResponse;
import cm.imf.pipeline.dto.response.DecaissementResponse;
import cm.imf.pipeline.dto.response.OperationCaisseResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.ContratCredit;
import cm.imf.pipeline.entity.Decaissement;
import cm.imf.pipeline.entity.DossierCredit;
import cm.imf.pipeline.entity.OperationCaisse;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.repository.ContratCreditRepository;
import cm.imf.pipeline.repository.DecaissementRepository;
import cm.imf.pipeline.repository.DossierCreditRepository;
import cm.imf.pipeline.repository.OperationCaisseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BackOfficeService implements IBackOfficeService {

    private final DossierCreditRepository  dossierRepo;
    private final ContratCreditRepository  contratRepo;
    private final DecaissementRepository   decaissementRepo;
    private final OperationCaisseRepository operationRepo;

    // ── Contrats ──────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public ContratCreditResponse genererContrat(UUID dossierUid, GenererContratRequest req, User user) {
        requireTenant(user);
        DossierCredit dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable."));
        if (!"APPROUVE".equals(dossier.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le contrat ne peut être généré que pour un dossier APPROUVE.");
        }
        if (contratRepo.findByDossierId(dossier.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un contrat existe déjà pour ce dossier.");
        }
        String ref = "CONT-" + dossier.getImfId() + "-" + System.currentTimeMillis();
        ContratCredit contrat = ContratCredit.builder()
                .dossierId(dossier.getId())
                .referenceContrat(ref)
                .montantFinal(req.montantFinal())
                .tauxInteret(req.tauxInteret())
                .fraisDossier(req.fraisDossier())
                .nbEcheances(req.nbEcheances())
                .periodicite(req.periodicite())
                .dateSignature(req.dateSignature())
                .agentSaisieId(user.getId())
                .build();
        return ContratCreditResponse.from(contratRepo.save(contrat));
    }

    @Override
    @Transactional
    public ContratCreditResponse validerSignatures(UUID contratUid, User user) {
        requireTenant(user);
        ContratCredit contrat = contratRepo.findByUid(contratUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable."));
        contrat.setSignaturesConformes(true);
        contrat.setStatut("SIGNE");
        return ContratCreditResponse.from(contratRepo.save(contrat));
    }

    @Override
    public ContratCreditResponse getContrat(UUID contratUid) {
        return ContratCreditResponse.from(
                contratRepo.findByUid(contratUid)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable.")));
    }

    @Override
    public ContratCreditResponse getContratParDossier(UUID dossierUid) {
        DossierCredit dossier = dossierRepo.findByUid(dossierUid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dossier introuvable."));
        return ContratCreditResponse.from(
                contratRepo.findByDossierId(dossier.getId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aucun contrat pour ce dossier.")));
    }

    // ── Décaissements ─────────────────────────────────────────────────────────

    @Override
    @Transactional
    public DecaissementResponse executerDecaissement(ExecuterDecaissementRequest req, User user) {
        requireTenant(user);
        ContratCredit contrat = contratRepo.findByUid(req.contratUid())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable."));
        if (!"SIGNE".equals(contrat.getStatut())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le décaissement requiert un contrat avec statut SIGNE.");
        }
        if (decaissementRepo.findByContratId(contrat.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un décaissement existe déjà pour ce contrat.");
        }
        Decaissement dec = Decaissement.builder()
                .contratId(contrat.getId())
                .caissierId(user.getId())
                .montantNet(req.montantNet())
                .mode(req.mode())
                .referencePaiement(req.referencePaiement())
                .dateDecaissement(OffsetDateTime.now())
                .statut("EXECUTE")
                .build();
        Decaissement saved = decaissementRepo.save(dec);

        // Mise à jour statut dossier
        dossierRepo.findById(contrat.getDossierId()).ifPresent(d -> {
            d.setStatut("DEBLOQUE");
            dossierRepo.save(d);
        });

        // Enregistrement opération caisse
        OperationCaisse op = OperationCaisse.builder()
                .caissierId(user.getId())
                .imfId(user.getImf().getId())
                .type("DECAISSEMENT")
                .montant(req.montantNet())
                .reference(saved.getUid().toString())
                .build();
        operationRepo.save(op);

        return DecaissementResponse.from(saved);
    }

    // ── Journal caisse ────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OperationCaisseResponse enregistrerEncaissement(EncaissementRequest req, User user) {
        requireTenant(user);
        OperationCaisse op = OperationCaisse.builder()
                .caissierId(user.getId())
                .imfId(user.getImf().getId())
                .type("ENCAISSEMENT")
                .montant(req.montant())
                .reference(req.reference())
                .pretId(req.pretId())
                .clientId(req.clientId())
                .build();
        return OperationCaisseResponse.from(operationRepo.save(op));
    }

    @Override
    public PageResponse<OperationCaisseResponse> journalCaisse(User user, int page, int size) {
        requireTenant(user);
        var pageable = PageRequest.of(page, size, Sort.by("dateOperation").descending());
        var p = operationRepo.findByImfIdOrderByDateOperationDesc(user.getImf().getId(), pageable);
        return PageResponse.from(p, OperationCaisseResponse::from);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void requireTenant(User user) {
        if (user.getImf() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Le SUPER_ADMIN n'a pas accès au back-office.");
        }
    }
}
