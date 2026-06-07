package cm.imf.pipeline.service.impl;

import cm.imf.pipeline.dto.request.EcheanceUpdateRequest;
import cm.imf.pipeline.dto.response.EcheanceResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.EcheanceApp;
import cm.imf.pipeline.enums.StatutEcheance;
import cm.imf.pipeline.exception.ResourceNotFoundException;
import cm.imf.pipeline.repository.EcheanceAppRepository;
import cm.imf.pipeline.security.TenantContext;
import cm.imf.pipeline.service.IEcheanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EcheanceServiceImpl implements IEcheanceService {

    private final EcheanceAppRepository echeanceRepository;

    @Override
    @Transactional(readOnly = true)
    public List<EcheanceResponse> getByPret(String idPret) {
        return echeanceRepository.findByIdPretOrderByNumEcheanceAsc(idPret)
                .stream()
                .map(EcheanceResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public EcheanceResponse getById(UUID uid) {
        return echeanceRepository.findByUid(uid)
                .map(EcheanceResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Échéance", uid));
    }

    @Override
    @Transactional
    public EcheanceResponse updateStatut(UUID uid, EcheanceUpdateRequest request) {
        EcheanceApp echeance = echeanceRepository.findByUid(uid)
                .orElseThrow(() -> new ResourceNotFoundException("Échéance", uid));

        if (echeance.getStatut() == StatutEcheance.ANNULEE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Une échéance annulée ne peut pas être modifiée");
        }

        echeance.setStatut(request.statut());

        if (request.montantPaye() != null) {
            echeance.setMontantPaye(request.montantPaye());
        }
        if (request.datePaiement() != null) {
            echeance.setDatePaiement(request.datePaiement());
        }
        if (request.observation() != null) {
            echeance.setObservation(request.observation());
        }

        EcheanceApp saved = echeanceRepository.save(echeance);
        log.info("Échéance {} mise à jour → statut: {}, montant payé: {}",
                uid, saved.getStatut(), saved.getMontantPaye());
        return EcheanceResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<EcheanceResponse> getEcheancesEnRetard(int page, int size) {
        Long imfId = TenantContext.currentImfId();
        var pageable = PageRequest.of(page, size, Sort.by("dateEcheance").ascending());
        return PageResponse.from(
                echeanceRepository.findByImfIdAndStatut(imfId, StatutEcheance.EN_RETARD, pageable),
                EcheanceResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public long countEnRetard() {
        Long imfId = TenantContext.currentImfId();
        return echeanceRepository.countByImfIdAndStatut(imfId, StatutEcheance.EN_RETARD);
    }
}
