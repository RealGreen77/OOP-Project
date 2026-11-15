package com.itsm.engine.intelligence;

import com.itsm.domain.catalog.ITService;

/** The textual facts about a (possibly not yet saved) incident that intelligent features analyse. */
public record IncidentText(String title, String description, ITService service, Long excludeIncidentId) {

    public String fullText() {
        return (title == null ? "" : title) + " " + (description == null ? "" : description);
    }
}
