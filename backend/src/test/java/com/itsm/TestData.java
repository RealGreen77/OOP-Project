package com.itsm;

import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.common.BaseEntity;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentFactory;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Urgency;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.user.Employee;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.Technician;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Builders for domain objects used by unit tests (no Spring, no database). */
public final class TestData {

    private static final AtomicLong IDS = new AtomicLong(1000);
    public static final Instant T0 = Instant.parse("2026-11-16T10:00:00Z");

    private TestData() {
    }

    /** JPA ids are database-generated; unit tests assign them reflectively. */
    public static <T extends BaseEntity> T withId(T entity) {
        try {
            Field f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, IDS.incrementAndGet());
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Category category(String code, String... keywords) {
        return withId(new Category(code, code.charAt(0) + code.substring(1).toLowerCase(), null, Set.of(keywords)));
    }

    public static ITService service(String code, BusinessCriticality criticality, Category defaultCategory) {
        return withId(new ITService(code, code + " service", null, criticality, "Team", defaultCategory));
    }

    public static Employee employee(String code) {
        return withId(new Employee(code, "Employee " + code, code.toLowerCase() + "@test.local", "hash", "Dept"));
    }

    public static Technician technician(String code, int experience, int maxIncidents, Category... skills) {
        Technician t = withId(new Technician(code, "Tech " + code, code.toLowerCase() + "@test.local", "hash", "IT",
                experience, maxIncidents));
        t.replaceSkills(Set.of(skills));
        return t;
    }

    public static TeamLead teamLead(String code) {
        return withId(new TeamLead(code, "Lead " + code, code.toLowerCase() + "@test.local", "hash", "IT", 10, 3));
    }

    public static SlaPolicy policy(Priority priority, int responseMin, int resolutionMin) {
        return withId(new SlaPolicy(priority + " policy", priority, responseMin, resolutionMin, 75, null));
    }

    public static Incident incident(String title, String description, ITService service, Instant at) {
        Incident i = IncidentFactory.report(title, description, employee("E" + IDS.incrementAndGet()), service,
                Impact.MEDIUM, Urgency.MEDIUM, 1, at);
        withId(i);
        i.assignNumber(i.getId());
        return i;
    }

    /** Incident classified, prioritised and with an SLA, ready for assignment. */
    public static Incident classifiedIncident(Category category, ITService service, Priority priority, SlaPolicy policy) {
        Incident i = incident("Something is broken", "A detailed description of the problem", service, T0);
        i.classify(category, T0);
        i.applyPriority(priority, 10, "test");
        i.applySlaPolicy(policy);
        return i;
    }
}
