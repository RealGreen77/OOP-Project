package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Finds open incidents that probably describe the same underlying problem.
 * <pre>
 *   score = 0.60 x TF-IDF cosine(text)
 *         + 0.30 x service relation (same service = 1.0, dependency-related = 0.5)
 *         + 0.10 x time proximity  (1 - hoursApart / windowHours)
 * </pre>
 * // [Intelligent Feature A] Duplicate Incident Detection.
 */
@Component
public class DuplicateDetector {

    public static final double SUSPECT_THRESHOLD = 0.50;
    public static final double STRONG_THRESHOLD = 0.75;

    static final double W_TEXT = 0.60;
    static final double W_SERVICE = 0.30;
    static final double W_TIME = 0.10;

    private final TextAnalyzer analyzer;

    public DuplicateDetector(TextAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * @param candidate the incident being reported/analysed
     * @param pool      recent incidents to compare against
     * @param window    look-back window used for the time-proximity factor
     * @return matches scoring at least {@code minScore}, best first
     */
    public List<SimilarIncident> findSimilar(IncidentText candidate, List<Incident> pool, Instant now,
                                             Duration window, double minScore) {
        List<String> candidateTokens = analyzer.tokens(candidate.fullText());
        if (candidateTokens.isEmpty() || pool.isEmpty()) return List.of();

        List<List<String>> corpus = new ArrayList<>(pool.size() + 1);
        corpus.add(candidateTokens);
        List<List<String>> poolTokens = pool.stream().map(i -> analyzer.tokens(i.searchableText())).toList();
        corpus.addAll(poolTokens);
        TfIdfModel model = new TfIdfModel(corpus);
        var candidateVector = model.vectorise(candidateTokens);

        List<SimilarIncident> matches = new ArrayList<>();
        for (int idx = 0; idx < pool.size(); idx++) {
            Incident existing = pool.get(idx);
            if (candidate.excludeIncidentId() != null && candidate.excludeIncidentId().equals(existing.getId())) continue;
            if (existing.getDuplicateOf() != null) continue; // compare against originals only

            List<String> tokens = poolTokens.get(idx);
            double text = TfIdfModel.cosine(candidateVector, model.vectorise(tokens));
            double service = serviceRelation(candidate.service(), existing.getAffectedService());
            double hoursApart = Math.abs(Duration.between(existing.getReportedAt(), now).toMinutes()) / 60.0;
            double time = Math.max(0, 1 - hoursApart / Math.max(1, window.toHours()));
            double score = W_TEXT * text + W_SERVICE * service + W_TIME * time;

            if (score >= minScore && text > 0.15) {
                List<String> reasons = new ArrayList<>();
                Set<String> shared = TfIdfModel.sharedTerms(candidateTokens, tokens);
                reasons.add(String.format("Text similarity %.0f%% (shared: %s)", text * 100,
                        String.join(", ", shared.stream().sorted().limit(6).toList())));
                if (service == 1.0) reasons.add("Same service: " + existing.getAffectedService().getName());
                else if (service > 0) reasons.add("Related service via dependency: " + existing.getAffectedService().getName());
                reasons.add(String.format("Reported %.1f hours apart", hoursApart));
                matches.add(new SimilarIncident(existing, round(score), round(text), reasons));
            }
        }
        matches.sort(Comparator.comparingDouble(SimilarIncident::score).reversed());
        return matches;
    }

    static double serviceRelation(ITService a, ITService b) {
        if (a == null || b == null) return 0;
        if (a.equals(b) || (a.getId() != null && a.getId().equals(b.getId()))) return 1.0;
        return a.dependsOn(b) || b.dependsOn(a) ? 0.5 : 0.0;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
