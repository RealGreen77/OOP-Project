package com.itsm.web.controller;

import com.itsm.service.IntelligenceService;
import com.itsm.web.dto.IntelligenceDtos.AnalysisResponse;
import com.itsm.web.dto.IntelligenceDtos.AnalyzeRequest;
import com.itsm.web.dto.IntelligenceDtos.CorrelationClusterDto;
import com.itsm.web.dto.IntelligenceDtos.RecommendationResponse;
import com.itsm.web.dto.IntelligenceDtos.ResolutionSuggestionDto;
import com.itsm.web.dto.IntelligenceDtos.SimilarIncidentDto;
import com.itsm.web.dto.IntelligenceDtos.SlaRiskItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/intelligence")
@Tag(name = "Intelligence", description = "Automated categorisation, duplicate detection, correlation, SLA risk, recommendations")
public class IntelligenceController {

    private final IntelligenceService intelligence;

    public IntelligenceController(IntelligenceService intelligence) {
        this.intelligence = intelligence;
    }

    @PostMapping("/analyze")
    @Operation(summary = "Live analysis of a draft incident: category suggestions, possible duplicates, predicted priority")
    public AnalysisResponse analyze(@Valid @RequestBody AnalyzeRequest request) {
        return intelligence.analyze(request);
    }

    @GetMapping("/correlations")
    @PreAuthorize("hasAuthority('VIEW_ALL_INCIDENTS')")
    @Operation(summary = "Clusters of open incidents sharing a probable root service (major incident detection)")
    public List<CorrelationClusterDto> correlations() {
        return intelligence.correlations();
    }

    @GetMapping("/sla-risks")
    @PreAuthorize("hasAuthority('VIEW_ALL_INCIDENTS')")
    @Operation(summary = "Active incidents predicted to breach their SLA")
    public List<SlaRiskItem> slaRisks() {
        return intelligence.slaRisks();
    }

    @GetMapping("/incidents/{id}/similar")
    @Operation(summary = "Open incidents similar to this one")
    public List<SimilarIncidentDto> similar(@PathVariable Long id) {
        return intelligence.similarTo(id);
    }

    @GetMapping("/incidents/{id}/resolutions")
    @Operation(summary = "Resolutions used for similar historical incidents")
    public List<ResolutionSuggestionDto> resolutions(@PathVariable Long id) {
        return intelligence.suggestedResolutions(id);
    }

    @GetMapping("/incidents/{id}/recommendations")
    @Operation(summary = "Technicians ranked by the active assignment strategy, with reasons")
    public RecommendationResponse recommendations(@PathVariable Long id) {
        return intelligence.recommendations(id);
    }
}
