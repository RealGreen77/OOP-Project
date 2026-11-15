package com.itsm.engine.intelligence;

import com.itsm.domain.incident.Incident;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Recommends resolutions from similar historical incidents (TF-IDF over title + description + root cause).
 * Same-category history receives a small boost.
 *
 * // [Intelligent Feature G] Suggested Resolution.
 */
@Component
public class ResolutionSuggester {

    static final double MIN_SIMILARITY = 0.18;
    static final double SAME_CATEGORY_BOOST = 0.10;

    private final TextAnalyzer analyzer;

    public ResolutionSuggester(TextAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    public List<ResolutionSuggestion> suggest(Incident incident, List<Incident> history, int limit) {
        List<String> target = analyzer.tokens(incident.searchableText());
        if (target.isEmpty() || history.isEmpty()) return List.of();

        List<List<String>> docs = history.stream().map(h -> analyzer.tokens(
                h.searchableText() + " " + (h.getResolution().getRootCause() == null ? "" : h.getResolution().getRootCause())))
                .toList();
        List<List<String>> corpus = new ArrayList<>(docs);
        corpus.add(target);
        TfIdfModel model = new TfIdfModel(corpus);
        var targetVector = model.vectorise(target);

        List<ResolutionSuggestion> suggestions = new ArrayList<>();
        for (int i = 0; i < history.size(); i++) {
            Incident past = history.get(i);
            if (past.getId() != null && past.getId().equals(incident.getId())) continue;
            double sim = TfIdfModel.cosine(targetVector, model.vectorise(docs.get(i)));
            if (incident.getCategory() != null && past.getCategory() != null
                    && incident.getCategory().getId().equals(past.getCategory().getId())) {
                sim += SAME_CATEGORY_BOOST;
            }
            sim = Math.min(1.0, sim);
            if (sim >= MIN_SIMILARITY) {
                suggestions.add(new ResolutionSuggestion(past, Math.round(sim * 1000.0) / 1000.0,
                        past.getResolution().getSummary(), past.getResolution().getRootCause()));
            }
        }
        return suggestions.stream()
                .sorted(Comparator.comparingDouble(ResolutionSuggestion::similarity).reversed())
                .limit(limit)
                .toList();
    }
}
