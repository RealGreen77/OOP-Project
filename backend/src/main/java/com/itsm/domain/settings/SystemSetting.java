package com.itsm.domain.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Runtime-configurable key/value setting (active priority strategy, active assignment strategy, ...).
 * Persisting these lets administrators switch algorithms without redeploying.
 */
@Entity
@Table(name = "system_settings")
public class SystemSetting {

    public static final String PRIORITY_STRATEGY = "priority.strategy";
    public static final String ASSIGNMENT_STRATEGY = "assignment.strategy";
    public static final String AUTO_ASSIGN = "assignment.auto";

    @Id
    @Column(name = "setting_key", length = 80)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 150)
    private String updatedBy;

    protected SystemSetting() {
    }

    public SystemSetting(String key, String value, Instant at, String by) {
        this.key = key;
        update(value, at, by);
    }

    public void update(String newValue, Instant at, String by) {
        this.value = newValue;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
}
