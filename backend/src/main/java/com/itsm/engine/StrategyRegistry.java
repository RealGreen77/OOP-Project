package com.itsm.engine;

import com.itsm.exception.InvalidInputException;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic, type-safe lookup of strategies by key.
 *
 * // [OOP: Generics + Polymorphism] Works for any {@code T extends NamedStrategy}; callers receive the
 * //                               abstraction and never depend on a concrete algorithm class.
 * // [Extensibility] Adding a new algorithm = adding one Spring bean; nothing else changes (Open/Closed).
 * // [Collections] LinkedHashMap preserves registration order for stable display in the admin UI.
 */
public class StrategyRegistry<T extends NamedStrategy> {

    private final String family;
    private final Map<String, T> strategies;

    public StrategyRegistry(String family, List<T> implementations) {
        this.family = family;
        Map<String, T> map = new LinkedHashMap<>();
        for (T impl : implementations) {
            if (map.putIfAbsent(impl.key(), impl) != null) {
                throw new IllegalStateException("Duplicate " + family + " strategy key: " + impl.key());
            }
        }
        if (map.isEmpty()) throw new IllegalStateException("No " + family + " strategies registered");
        this.strategies = Collections.unmodifiableMap(map);
    }

    public T get(String key) {
        T strategy = strategies.get(key);
        if (strategy == null) {
            throw new InvalidInputException("Unknown " + family + " strategy '" + key + "'. Available: " + strategies.keySet());
        }
        return strategy;
    }

    public boolean contains(String key) {
        return strategies.containsKey(key);
    }

    public List<T> all() {
        return List.copyOf(strategies.values());
    }

    public String family() {
        return family;
    }
}
