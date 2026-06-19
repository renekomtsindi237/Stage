package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.AgenceResponse;
import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.service.IAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint public (authentifié) pour la liste des agences — accessible à tous les rôles.
 * Utilisé par les composants DIRECTEUR, CHEF_AGENCE, etc.
 */
@RestController
@RequestMapping("/agences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Tag(name = "Agences", description = "Liste des agences de l'IMF courante")
public class AgenceController {

    private final IAdminService adminService;

    @Operation(summary = "Liste des agences actives de l'IMF")
    @GetMapping
    public ResponseEntity<ApiResponse<List<AgenceResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(adminService.listAgences()));
    }
}
