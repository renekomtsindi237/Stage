package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.CollecteRequest;
import cm.imf.pipeline.dto.response.CollecteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.CollecteTerrain;
import cm.imf.pipeline.entity.User;
import cm.imf.pipeline.enums.StatutCollecte;
import cm.imf.pipeline.repository.CollecteRepository;
import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollecteService implements ICollecteService {

    private final CollecteRepository collecteRepository;

    @Transactional
    public CollecteResponse enregistrer(CollecteRequest request, User agent) {
        // Déduplication : ID mobile déjà connu
        if (collecteRepository.existsByIdCollecteMobile(request.idCollecteMobile())) {
            log.warn("Collecte dupliquée reçue : {}", request.idCollecteMobile());
            CollecteTerrain existing = collecteRepository
                    .findByIdCollecteMobile(request.idCollecteMobile()).get();
            existing.setStatut(StatutCollecte.DOUBLON);
            return CollecteResponse.from(collecteRepository.save(existing));
        }

        // Déduplication par référence de transaction (même ref + même jour)
        if (request.referenceTransaction() != null
                && collecteRepository.existsByReferenceTransactionAndDateCollecte(
                        request.referenceTransaction(), request.dateCollecte())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Doublon : référence de transaction déjà enregistrée pour cette date");
        }

        CollecteTerrain collecte = CollecteTerrain.builder()
                .idCollecteMobile(request.idCollecteMobile())
                .agent(agent)
                .imf(agent.getImf())
                .clientId(request.clientId())
                .pretId(request.pretId())
                .dateCollecte(request.dateCollecte())
                .montantCollecte(request.montantCollecte())
                .canalPaiement(request.canalPaiement())
                .referenceTransaction(request.referenceTransaction())
                .observation(request.observation())
                .statut(StatutCollecte.CONFIRMEE)
                .latitude(request.latitude())
                .longitude(request.longitude())
                .build();

        CollecteTerrain saved = collecteRepository.save(collecte);
        log.info("Collecte enregistrée : {} — agent: {}", saved.getIdCollecteMobile(),
                agent.getUsername());
        return CollecteResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<CollecteResponse> getMesCollectes(User agent, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Long imfId = agent.getImf() != null ? agent.getImf().getId() : null;
        return PageResponse.from(
                collecteRepository.findByImfIdAndAgentId(imfId, agent.getId(), pageable),
                CollecteResponse::from);
    }

    @Transactional(readOnly = true)
    public CollecteResponse getById(UUID uid) {
        return collecteRepository.findByUid(uid)
                .map(CollecteResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Collecte non trouvée : " + uid));
    }
}
