package cm.imf.pipeline.controller;

import cm.imf.pipeline.service.IKpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/kpi")
@RequiredArgsConstructor
@Tag(name = "KPI", description = "Indicateurs clés de performance — PAR et collectes")
public class KpiController {

    private final IKpiService kpiService;


    @Operation(summary = "PAR30/PAR90 par zone pour une période")
    @GetMapping("/par-stats")
    public ResponseEntity<List<Map<String, Object>>> getParStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(kpiService.getParStats(dateDebut, dateFin));
    }

    @Operation(summary = "Volume des collectes par canal et par zone pour une période")
    @GetMapping("/collecte-stats")
    public ResponseEntity<List<Map<String, Object>>> getCollecteStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        return ResponseEntity.ok(kpiService.getCollecteStats(dateDebut, dateFin));
    }

    @Operation(summary = "Résumé tableau de bord — derniers 30 jours")
    @GetMapping("/dashboard-summary")
    public ResponseEntity<Map<String, Object>> getDashboardSummary() {
        return ResponseEntity.ok(kpiService.getDashboardSummary());
    }
}
