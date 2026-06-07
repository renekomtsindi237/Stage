package cm.imf.pipeline.service;

import cm.imf.pipeline.dto.request.AlerteUpdateRequest;
import cm.imf.pipeline.dto.response.AlerteResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.entity.AlerteImpaye;
import cm.imf.pipeline.enums.StatutAlerte;
import cm.imf.pipeline.event.AlerteChangedEvent;
import cm.imf.pipeline.repository.AlerteRepository;
import cm.imf.pipeline.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlerteService implements IAlertService {

    private final AlerteRepository alerteRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public PageResponse<AlerteResponse> getAlertes(StatutAlerte statut, int page, int size) {
        Long imfId = TenantContext.currentImfId();
        var pageable = PageRequest.of(page, size, Sort.by("joursRetard").descending());
        if (statut != null) {
            return PageResponse.from(
                    alerteRepository.findByImfIdAndStatutAlerte(imfId, statut, pageable),
                    AlerteResponse::from);
        }
        return PageResponse.from(
                alerteRepository.findByImfId(imfId, pageable),
                AlerteResponse::from);
    }

    @Transactional(readOnly = true)
    public AlerteResponse getById(UUID uid) {
        Long imfId = TenantContext.currentImfId();
        return (imfId != null
                ? alerteRepository.findByUidAndImf_Id(uid, imfId)
                : alerteRepository.findByUid(uid))
                .map(AlerteResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Alerte non trouvée : " + uid));
    }

    @Transactional
    public AlerteResponse updateStatut(UUID uid, AlerteUpdateRequest request) {
        AlerteImpaye alerte = alerteRepository.findByUid(uid)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Alerte non trouvée : " + uid));

        StatutAlerte nouveau = request.statut();

        // Transitions valides
        if (alerte.getStatutAlerte() == StatutAlerte.CLOTUREE) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Une alerte clôturée ne peut pas changer de statut");
        }

        if (nouveau == StatutAlerte.CLOTUREE) {
            alerte.setDateCloture(OffsetDateTime.now());
        }

        StatutAlerte ancienStatut = alerte.getStatutAlerte();
        alerte.setStatutAlerte(nouveau);
        AlerteImpaye saved = alerteRepository.save(alerte);
        log.info("Alerte {} → statut mis à jour : {}", uid, nouveau);

        AlerteResponse response = AlerteResponse.from(saved);
        eventPublisher.publishEvent(new AlerteChangedEvent(
                this, response, AlerteChangedEvent.ChangeType.UPDATED, ancienStatut));
        return response;
    }

    @Transactional(readOnly = true)
    public long countActiveAlertes() {
        Long imfId = TenantContext.currentImfId();
        return alerteRepository.countByImfIdAndStatutAlerte(imfId, StatutAlerte.ACTIVE);
    }
}
