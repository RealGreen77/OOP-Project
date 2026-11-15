package com.itsm.engine.intelligence;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TF-IDF vector space model with cosine similarity, built over a corpus of tokenised documents.
 * Rare, informative words ("payment", "vpn") weigh more than common ones ("fail").
 *
 * // [OOP: Encapsulation] Immutable after construction; document frequencies are private.
 */
public final class TfIdfModel {

    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private final int documentCount;

    public TfIdfModel(Collection<List<String>> corpus) {
        this.documentCount = corpus.size();
        for (List<String> doc : corpus) {
            for (String term : new HashSet<>(doc)) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
    }

    /** Smoothed inverse document frequency. */
    public double idf(String term) {
        int df = documentFrequency.getOrDefault(term, 0);
        return Math.log((1.0 + documentCount) / (1.0 + df)) + 1.0;
    }

    public Map<String, Double> vectorise(List<String> tokens) {
        Map<String, Double> tf = new HashMap<>();
        for (String t : tokens) tf.merge(t, 1.0, Double::sum);
        Map<String, Double> vector = new HashMap<>();
        for (Map.Entry<String, Double> e : tf.entrySet()) {
            vector.put(e.getKey(), (e.getValue() / tokens.size()) * idf(e.getKey()));
        }
        return vector;
    }

    public static double cosine(Map<String, Double> a, Map<String, Double> b) {
        if (a.isEmpty() || b.isEmpty()) return 0.0;
        Map<String, Double> small = a.size() <= b.size() ? a : b;
        Map<String, Double> large = small == a ? b : a;
        double dot = 0;
        for (Map.Entry<String, Double> e : small.entrySet()) {
            Double other = large.get(e.getKey());
            if (other != null) dot += e.getValue() * other;
        }
        double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
        double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
        return normA == 0 || normB == 0 ? 0.0 : dot / (normA * normB);
    }

    public double similarity(List<String> a, List<String> b) {
        return cosine(vectorise(a), vectorise(b));
    }

    /** Terms shared by both token lists (for explanations). */
    public static Set<String> sharedTerms(List<String> a, List<String> b) {
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(new HashSet<>(b));
        return shared;
    }
}
