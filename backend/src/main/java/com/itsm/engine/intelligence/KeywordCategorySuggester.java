package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.Category;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rule-based categoriser driven by administrator-maintained category keywords.
 * <pre>
 *   score(category) = 2 x (keyword hits in title) + 1 x (keyword hits in description)
 *                   + 1.5 if the category is the affected service's default category
 *   confidence      = score / sum of all scores
 * </pre>
 * Multi-word keywords ("blue screen") are matched as phrases on word boundaries.
 */
@Component
public class KeywordCategorySuggester implements CategorySuggester {

    static final double TITLE_WEIGHT = 2.0;
    static final double DESCRIPTION_WEIGHT = 1.0;
    static final double SERVICE_DEFAULT_BONUS = 1.5;

    private final TextAnalyzer analyzer;

    public KeywordCategorySuggester(TextAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    @Override
    public List<CategorySuggestion> suggest(IncidentText text, List<Category> activeCategories) {
        String title = analyzer.normalise(text.title());
        String description = analyzer.normalise(text.description());
        Category serviceDefault = text.service() == null ? null : text.service().getDefaultCategory();

        record Scored(Category category, double score, List<String> matches) {}
        List<Scored> scored = new ArrayList<>();
        double total = 0;
        for (Category category : activeCategories) {
            double score = 0;
            List<String> matches = new ArrayList<>();
            for (String keyword : category.getKeywords()) {
                String needle = analyzer.normalise(keyword);
                boolean inTitle = title.contains(needle);
                boolean inDescription = description.contains(needle);
                if (inTitle) score += TITLE_WEIGHT;
                if (inDescription) score += DESCRIPTION_WEIGHT;
                if (inTitle || inDescription) matches.add(keyword);
            }
            if (serviceDefault != null && serviceDefault.getId() != null && serviceDefault.getId().equals(category.getId())) {
                score += SERVICE_DEFAULT_BONUS;
                matches.add("(default for service)");
            }
            if (score > 0) {
                scored.add(new Scored(category, score, matches));
                total += score;
            }
        }
        final double sum = total;
        return scored.stream()
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(3)
                .map(s -> new CategorySuggestion(s.category(), Math.round(s.score() / sum * 100.0) / 100.0, s.matches()))
                .toList();
    }
}
