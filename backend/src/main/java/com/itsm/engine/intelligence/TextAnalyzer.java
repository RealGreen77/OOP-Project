package com.itsm.engine.intelligence;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lightweight NLP pipeline for incident text: lower-casing, tokenisation, stop-word removal,
 * domain synonym normalisation and suffix stemming.
 *
 * The synonym map is what lets "Payment application unavailable", "Payment transaction failing"
 * and "Unable to make payment" be recognised as related: unavailable / failing / unable all
 * normalise to the same concept token {@code fail}.
 *
 * // [Collections] HashSet for O(1) stop-word lookup, HashMap for synonym normalisation.
 */
@Component
public class TextAnalyzer {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being", "to", "of",
            "in", "on", "at", "for", "with", "by", "from", "as", "it", "its", "this", "that", "these", "those",
            "i", "we", "you", "they", "he", "she", "my", "our", "your", "their", "me", "us", "them", "am",
            "have", "has", "had", "do", "does", "did", "can", "could", "will", "would", "should", "may", "might",
            "please", "help", "hi", "hello", "thanks", "thank", "also", "just", "very", "some", "any", "all",
            "when", "while", "since", "after", "before", "again", "still", "now", "today", "there", "here",
            "what", "which", "who", "how", "why", "so", "if", "than", "then", "too", "into", "about", "get",
            "getting", "got", "make", "making", "made", "trying", "try", "tried", "able", "users", "user");

    /** Domain synonyms mapped to a canonical concept token. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("unavailable", "fail"), Map.entry("down", "fail"), Map.entry("outage", "fail"),
            Map.entry("failing", "fail"), Map.entry("failed", "fail"), Map.entry("failure", "fail"),
            Map.entry("fails", "fail"), Map.entry("unable", "fail"), Map.entry("cannot", "fail"),
            Map.entry("cant", "fail"), Map.entry("broken", "fail"), Map.entry("error", "fail"),
            Map.entry("errors", "fail"), Map.entry("crash", "fail"), Map.entry("crashed", "fail"),
            Map.entry("crashing", "fail"), Map.entry("unreachable", "fail"), Map.entry("offline", "fail"),
            Map.entry("not", "fail"), Map.entry("doesnt", "fail"), Map.entry("wont", "fail"),
            Map.entry("slow", "slow"), Map.entry("sluggish", "slow"), Map.entry("lag", "slow"),
            Map.entry("latency", "slow"), Map.entry("timeout", "slow"), Map.entry("timeouts", "slow"),
            Map.entry("hanging", "slow"), Map.entry("freezing", "slow"),
            Map.entry("payments", "payment"), Map.entry("pay", "payment"), Map.entry("checkout", "payment"),
            Map.entry("transaction", "payment"), Map.entry("transactions", "payment"),
            Map.entry("login", "auth"), Map.entry("logon", "auth"), Map.entry("signin", "auth"),
            Map.entry("password", "auth"), Map.entry("sso", "auth"), Map.entry("authentication", "auth"),
            Map.entry("mfa", "auth"), Map.entry("otp", "auth"),
            Map.entry("wifi", "network"), Map.entry("wi-fi", "network"), Map.entry("internet", "network"),
            Map.entry("lan", "network"), Map.entry("connectivity", "network"),
            Map.entry("mail", "email"), Map.entry("outlook", "email"), Map.entry("inbox", "email"),
            Map.entry("db", "database"), Map.entry("sql", "database"), Map.entry("postgres", "database"),
            Map.entry("laptop", "computer"), Map.entry("desktop", "computer"), Map.entry("pc", "computer"));

    /** Full pipeline: normalised, stemmed tokens with stop words removed. */
    public List<String> tokens(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;
        String cleaned = text.toLowerCase(Locale.ROOT)
                .replace("can't", "cant").replace("won't", "wont").replace("doesn't", "doesnt")
                .replace("isn't", "not").replace("aren't", "not").replace("don't", "not")
                .replaceAll("[^a-z0-9\\s-]", " ");
        for (String raw : cleaned.split("[\\s-]+")) {
            if (raw.length() < 2 || STOP_WORDS.contains(raw)) continue;
            String token = SYNONYMS.getOrDefault(raw, raw);
            token = SYNONYMS.getOrDefault(stem(token), stem(token));
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) result.add(token);
        }
        return result;
    }

    /** Lower-cased text with punctuation collapsed - used for keyword phrase matching. */
    public String normalise(String text) {
        if (text == null) return "";
        return " " + text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    /** Conservative suffix stripping (a tiny Porter-style stemmer). */
    String stem(String word) {
        if (word.length() <= 4 || SYNONYMS.containsValue(word)) return word;
        if (word.endsWith("ies") && word.length() > 5) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("ing") && word.length() > 6) return word.substring(0, word.length() - 3);
        if (word.endsWith("ed") && word.length() > 5) return word.substring(0, word.length() - 2);
        if (word.endsWith("es") && word.length() > 5) return word.substring(0, word.length() - 2);
        if (word.endsWith("s") && !word.endsWith("ss")) return word.substring(0, word.length() - 1);
        return word;
    }
}
