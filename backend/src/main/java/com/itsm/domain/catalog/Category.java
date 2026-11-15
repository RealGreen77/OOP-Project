package com.itsm.domain.catalog;

import com.itsm.domain.common.BaseEntity;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Incident category (Hardware, Network, Database, ...).
 *
 * Categories are data, not code: administrators can add new categories (with keywords that drive
 * automatic categorisation) at runtime, so introducing a category requires no change to unrelated
 * parts of the application - satisfying the extensibility requirement for classification.
 * // [MVP: Incident Classification]
 */
@Entity
@Table(name = "categories")
public class Category extends BaseEntity {

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 500)
    private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "category_keywords", joinColumns = @JoinColumn(name = "category_id"))
    @Column(name = "keyword", length = 60)
    private Set<String> keywords = new LinkedHashSet<>();

    @Column(nullable = false)
    private boolean active = true;

    protected Category() {
    }

    public Category(String code, String name, String description, Set<String> keywords) {
        rename(code, name, description);
        replaceKeywords(keywords);
    }

    public void rename(String code, String name, String description) {
        if (code == null || !code.matches("[A-Za-z][A-Za-z0-9_]{1,39}")) {
            throw new InvalidInputException("Category code must be 2-40 letters/digits/underscores");
        }
        if (name == null || name.isBlank()) throw new InvalidInputException("Category name is required");
        this.code = code.toUpperCase(Locale.ROOT);
        this.name = name.trim();
        this.description = description;
    }

    public void replaceKeywords(Set<String> newKeywords) {
        keywords.clear();
        if (newKeywords != null) {
            newKeywords.stream()
                    .filter(k -> k != null && !k.isBlank())
                    .map(k -> k.trim().toLowerCase(Locale.ROOT))
                    .forEach(keywords::add);
        }
    }

    public void activate() { this.active = true; }
    public void deactivate() { this.active = false; }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Set<String> getKeywords() { return Collections.unmodifiableSet(keywords); }
    public boolean isActive() { return active; }
}
