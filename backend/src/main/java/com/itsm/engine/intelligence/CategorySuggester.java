package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.Category;

import java.util.List;

/**
 * Suggests incident categories from free text.
 *
 * // [OOP: Interfaces] The rule-based {@link KeywordCategorySuggester} is the current implementation.
 * //                  An AI/LLM-backed implementation can be added later as another bean (Adapter over the
 * //                  external API) without changing any caller - the extension point is already in place.
 * // [Intelligent Feature F] Incident Categorization.
 */
public interface CategorySuggester {

    List<CategorySuggestion> suggest(IncidentText text, List<Category> activeCategories);
}
