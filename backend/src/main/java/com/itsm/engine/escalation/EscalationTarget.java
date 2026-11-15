package com.itsm.engine.escalation;

import com.itsm.domain.user.User;

import java.util.List;

/** Who an incident was escalated to, and at which level. */
public record EscalationTarget(int level, String levelName, List<User> recipients) {
}
