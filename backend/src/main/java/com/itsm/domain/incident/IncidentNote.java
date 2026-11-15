package com.itsm.domain.incident;

import com.itsm.domain.common.BaseEntity;
import com.itsm.domain.user.User;
import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One entry in an incident's work log (investigation note, information request/response, comment).
 * // [OOP: Composition] Notes are created only through Incident and deleted with it (cascade + orphanRemoval).
 */
@Entity
@Table(name = "incident_notes")
public class IncidentNote extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private User author;

    @Enumerated(EnumType.STRING)
    @Column(name = "note_type", nullable = false, length = 20)
    private NoteType type;

    @Column(nullable = false, length = 4000)
    private String content;

    /** Internal notes are visible to support staff only, never to the reporter. */
    @Column(name = "internal_only", nullable = false)
    private boolean internal;

    protected IncidentNote() {
    }

    IncidentNote(Incident incident, User author, NoteType type, String content, boolean internal, Instant at) {
        if (content == null || content.isBlank()) throw new InvalidInputException("Note content must not be blank");
        if (content.length() > 4000) throw new InvalidInputException("Note content must be at most 4000 characters");
        this.incident = incident;
        this.author = author;
        this.type = type;
        this.content = content.trim();
        this.internal = internal;
        initialiseCreatedAt(at);
    }

    public Incident getIncident() { return incident; }
    public User getAuthor() { return author; }
    public NoteType getType() { return type; }
    public String getContent() { return content; }
    public boolean isInternal() { return internal; }
}
