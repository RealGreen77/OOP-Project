package com.itsm.engine.assignment;

import com.itsm.domain.user.Technician;

import java.util.List;

/** A ranked technician with an explainable score. */
public record TechnicianRecommendation(
        Technician technician,
        double score,
        int activeIncidents,
        int maxIncidents,
        List<String> reasons) {
}
