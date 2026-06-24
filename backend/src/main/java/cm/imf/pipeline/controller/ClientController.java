package cm.imf.pipeline.controller;

import cm.imf.pipeline.dto.response.ApiResponse;
import cm.imf.pipeline.dto.response.ClientResponse;
import cm.imf.pipeline.dto.response.PageResponse;
import cm.imf.pipeline.service.IClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Recherche et consultation des clients depuis le pipeline staging")
public class ClientController {

    private final IClientService clientService;

    @Operation(summary = "Recherche autocomplete par nom ou téléphone")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ClientResponse>>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.search(q, limit)));
    }

    @Operation(summary = "Liste paginée de tous les clients")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ClientResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String agence) {
        List<ClientResponse> data = clientService.list(page, size, search, statut, agence);
        long total                = clientService.count(search, statut, agence);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(data, page, size, total)));
    }

    @Operation(summary = "Détail d'un client par son identifiant")
    @GetMapping("/{idClient}")
    public ResponseEntity<ApiResponse<ClientResponse>> getById(@PathVariable String idClient) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getById(idClient)));
    }
}
