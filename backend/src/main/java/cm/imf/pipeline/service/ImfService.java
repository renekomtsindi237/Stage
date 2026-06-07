package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CreateImfAdminRequest;
import cm.imf.pipeline.dto.request.CreateImfRequest;
import cm.imf.pipeline.dto.response.ImfResponse;
import cm.imf.pipeline.dto.response.PlatformStatsResponse;
import cm.imf.pipeline.entity.Imf;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.Role;
import cm.imf.pipeline.exception.BusinessException;
import cm.imf.pipeline.exception.DuplicateResourceException;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.ImfRepository;
import cm.imf.pipeline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImfService implements IImfService {

    private final ImfRepository imfRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ── Helper ───────────────────────────────────────────────────────────────

    private ImfResponse toResponse(Imf imf) {
        boolean hasDsi = userRepository.existsByImfIdAndRole(imf.getId(), Role.DSI);
        return ImfResponse.of(imf, hasDsi);
    }

    // ── Requêtes ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlatformStatsResponse getStats() {
        long total     = imfRepository.count();
        long active    = imfRepository.countByActifTrue();
        long users     = userRepository.countByRoleNot(Role.SUPER_ADMIN);
        long thisMonth = imfRepository.countCreatedThisMonth();
        return new PlatformStatsResponse(total, active, total - active, users, thisMonth);
    }

    @Transactional(readOnly = true)
    public List<ImfResponse> listAll() {
        List<Imf> imfs = imfRepository.findAll();
        // Une seule requête pour éviter le N+1
        Set<Long> imfsWithDsi = userRepository.findImfIdsByRole(Role.DSI);
        return imfs.stream()
                .map(imf -> ImfResponse.of(imf, imfsWithDsi.contains(imf.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ImfResponse getById(UUID uid) {
        return imfRepository.findByUid(uid)
                .map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", uid));
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    @Transactional
    public ImfResponse create(CreateImfRequest request) {
        if (imfRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("IMF", "code", request.code());
        }
        Imf imf = Imf.builder()
                .code(request.code())
                .nom(request.nom())
                .pays(request.pays() != null ? request.pays() : "Cameroun")
                .actif(true)
                // Constitution
                .denominationSociale(request.denominationSociale())
                .adresseSiege(request.adresseSiege())
                .formeJuridique(request.formeJuridique())
                .capitalSocial(request.capitalSocial())
                .numAgrement(request.numAgrement())
                .telephone(request.telephone())
                .email(request.email())
                // Paramètres crédit
                .tauxInteretAnnuel(request.tauxInteretAnnuel())
                .dureeMaxCreditMois(request.dureeMaxCreditMois())
                .tauxPenaliteRetard(request.tauxPenaliteRetard())
                .seuilRelanceJours(request.seuilRelanceJours())
                // Paramètres épargne
                .tauxEpargne(request.tauxEpargne())
                .soldeMinEpargne(request.soldeMinEpargne())
                .fraisTenueCompte(request.fraisTenueCompte())
                // Segmentation
                .segmentsClients(request.segmentsClients())
                .typesGaranties(request.typesGaranties())
                .build();
        Imf saved = imfRepository.save(imf);
        log.info("IMF créée : {} [{}]", saved.getNom(), saved.getCode());
        return ImfResponse.of(saved, false); // nouvelle IMF — pas encore de DSI
    }

    @Transactional
    public ImfResponse deactivate(UUID uid) {
        Imf imf = imfRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", uid));
        imf.setActif(false);
        log.info("IMF désactivée : {}", imf.getCode());
        return toResponse(imfRepository.save(imf));
    }

    @Transactional
    public ImfResponse activate(UUID uid) {
        Imf imf = imfRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", uid));
        imf.setActif(true);
        log.info("IMF réactivée : {}", imf.getCode());
        return toResponse(imfRepository.save(imf));
    }

    @Transactional
    public void delete(UUID uid) {
        Imf imf = imfRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", uid));
        long userCount = userRepository.countByImfId(imf.getId());
        if (userCount > 0) {
            throw new BusinessException(
                "Impossible de supprimer : " + userCount + " utilisateur(s) rattaché(s) à cette IMF. " +
                "Supprimez d'abord les utilisateurs ou désactivez l'IMF.",
                org.springframework.http.HttpStatus.CONFLICT
            );
        }
        imfRepository.delete(imf);
        log.info("IMF supprimée définitivement : {} [{}]", imf.getNom(), imf.getCode());
    }

    /**
     * Crée le compte DSI et retourne l'IMF mise à jour (hasDsi = true).
     */
    @Transactional
    public ImfResponse createAdmin(UUID imfUid, CreateImfAdminRequest request) {
        Imf imf = imfRepository.findByUid(imfUid)
                .orElseThrow(() -> new ResourceNotFoundException("IMF", imfUid));

        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Utilisateur", "username", request.username());
        }

        User dsi = User.builder()
                .username(request.username())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.DSI)
                .imf(imf)
                .actif(true)
                .mustChangePassword(true)
                .build();

        userRepository.save(dsi);
        log.info("DSI créé : {} pour IMF {}", request.username(), imf.getCode());
        return ImfResponse.of(imf, true);
    }
}
