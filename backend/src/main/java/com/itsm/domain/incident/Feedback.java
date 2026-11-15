package com.itsm.domain.incident;

import com.itsm.exception.InvalidInputException;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.time.Instant;

/**
 * Reporter's satisfaction feedback after resolution.
 * // [OOP: Composition] Immutable value object owned by an Incident; validates its own invariants.
 */
@Embeddable
public class Feedback {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;

    @Column(name = "feedback_rating")
    private Integer rating;

    @Column(name = "feedback_comment", length = 1000)
    private String comment;

    @Column(name = "feedback_at")
    private Instant submittedAt;

    protected Feedback() {
    }

    public Feedback(int rating, String comment, Instant submittedAt) {
        if (rating < MIN_RATING || rating > MAX_RATING) {
            throw new InvalidInputException("Rating must be between " + MIN_RATING + " and " + MAX_RATING);
        }
        this.rating = rating;
        this.comment = comment == null ? null : comment.trim();
        this.submittedAt = submittedAt;
    }

    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getSubmittedAt() { return submittedAt; }
}
