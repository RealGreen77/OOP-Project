package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.Category;

import java.util.List;

/** A suggested category with confidence (0..1) and the evidence used. */
public record CategorySuggestion(Category category, double confidence, List<String> matchedKeywords) {
}
