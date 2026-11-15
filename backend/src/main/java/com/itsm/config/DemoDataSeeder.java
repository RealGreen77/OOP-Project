package com.itsm.config;

import com.itsm.domain.audit.AuditAction;
import com.itsm.domain.audit.AuditEntry;
import com.itsm.domain.catalog.BusinessCriticality;
import com.itsm.domain.catalog.Category;
import com.itsm.domain.catalog.ITService;
import com.itsm.domain.incident.ClosureCode;
import com.itsm.domain.incident.Feedback;
import com.itsm.domain.incident.Impact;
import com.itsm.domain.incident.Incident;
import com.itsm.domain.incident.IncidentFactory;
import com.itsm.domain.incident.IncidentStatus;
import com.itsm.domain.incident.NoteType;
import com.itsm.domain.incident.Priority;
import com.itsm.domain.incident.Resolution;
import com.itsm.domain.incident.Urgency;
import com.itsm.domain.sla.SlaPolicy;
import com.itsm.domain.user.Employee;
import com.itsm.domain.user.NotificationPreferences;
import com.itsm.domain.user.Role;
import com.itsm.domain.user.TeamLead;
import com.itsm.domain.user.Technician;
import com.itsm.domain.user.User;
import com.itsm.domain.user.UserFactory;
import com.itsm.engine.priority.MatrixPriorityStrategy;
import com.itsm.engine.priority.PriorityAssessment;
import com.itsm.engine.priority.PriorityContext;
import com.itsm.engine.sla.SlaPolicyResolver;
import com.itsm.repository.AuditEntryRepository;
import com.itsm.repository.CategoryRepository;
import com.itsm.repository.ITServiceRepository;
import com.itsm.repository.IncidentRepository;
import com.itsm.repository.SlaPolicyRepository;
import com.itsm.repository.UserRepository;
import com.itsm.service.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Populates an empty database with a realistic organisation: categories, a dependent service catalogue,
 * SLA policies (the spec's table + stricter Payment Gateway overrides), users of every role and ~50 incidents
 * spanning the last 30 days in every lifecycle state - built through the real domain model (factory, state
 * transitions, priority strategy, SLA resolver) so the history is internally consistent.
 *
 * All demo accounts use the password {@value #DEMO_PASSWORD}.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password@123";
    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ItsmProperties properties;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ITServiceRepository serviceRepository;
    private final SlaPolicyRepository slaRepository;
    private final IncidentRepository incidentRepository;
    private final AuditEntryRepository auditRepository;
    private final SlaPolicyResolver slaResolver;
    private final SettingsService settingsService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final MatrixPriorityStrategy priorityStrategy = new MatrixPriorityStrategy();
    private final Map<String, Category> categories = new HashMap<>();
    private final Map<String, ITService> services = new HashMap<>();
    private final Map<String, User> users = new HashMap<>();
    private final Random random = new Random(42);

    public DemoDataSeeder(ItsmProperties properties, UserRepository userRepository, CategoryRepository categoryRepository,
                          ITServiceRepository serviceRepository, SlaPolicyRepository slaRepository,
                          IncidentRepository incidentRepository, AuditEntryRepository auditRepository,
                          SlaPolicyResolver slaResolver, SettingsService settingsService,
                          PasswordEncoder passwordEncoder, Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.slaRepository = slaRepository;
        this.incidentRepository = incidentRepository;
        this.auditRepository = auditRepository;
        this.slaResolver = slaResolver;
        this.settingsService = settingsService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        settingsService.initialiseDefaults();
        if (!properties.seedDemoData() || userRepository.count() > 0) return;
        log.info("Seeding demo data...");
        seedCategories();
        seedServices();
        seedSlaPolicies();
        seedUsers();
        seedIncidents();
        log.info("Demo data ready: {} users, {} services, {} incidents. Password for all demo users: {}",
                userRepository.count(), serviceRepository.count(), incidentRepository.count(), DEMO_PASSWORD);
    }

    // ================================================================ catalogue

    private void seedCategories() {
        category("HARDWARE", "Hardware", "Laptops, desktops, peripherals and printers",
                "laptop", "desktop", "monitor", "keyboard", "mouse", "printer", "battery", "screen", "blue screen",
                "overheating", "hardware", "docking", "charger", "display");
        category("SOFTWARE", "Software", "Desktop software installation, licensing and crashes",
                "software", "install", "installation", "license", "excel", "word", "office", "upgrade", "patch",
                "add-in", "crashes", "teams");
        category("NETWORK", "Network", "LAN, Wi-Fi, VPN, DNS and connectivity",
                "network", "wifi", "wi-fi", "internet", "lan", "vpn", "router", "switch", "dns", "connectivity",
                "packet loss", "firewall", "access point");
        category("DATABASE", "Database", "Database availability, performance and data issues",
                "database", "sql", "query", "deadlock", "replication", "postgres", "oracle", "connection pool",
                "index", "cpu", "stale data", "report");
        category("CLOUD", "Cloud", "Cloud infrastructure, VMs and containers",
                "cloud", "aws", "azure", "kubernetes", "container", "vm", "virtual machine", "instance", "pod",
                "autoscaling", "disk space");
        category("SECURITY", "Security", "Phishing, malware, suspicious activity and breaches",
                "security", "phishing", "malware", "virus", "ransomware", "breach", "suspicious", "spam",
                "trojan", "compromised", "antivirus");
        category("AUTHENTICATION", "Authentication", "Login, passwords, SSO and MFA",
                "login", "log in", "password", "sso", "mfa", "otp", "account locked", "authentication", "sign in",
                "2fa", "access denied", "saml");
        category("APPLICATION", "Application", "Business applications and portals",
                "application", "portal", "payment", "transaction", "checkout", "website", "page", "erp", "crm",
                "api", "timeout", "error 500", "refund", "outlook", "email");
        category("STORAGE", "Storage", "Disks, file shares, backups and quotas",
                "storage", "disk", "drive", "file share", "shared drive", "sharepoint", "onedrive", "backup",
                "quota", "nas", "upload", "sync");
    }

    private void category(String code, String name, String description, String... keywords) {
        categories.put(code, categoryRepository.save(new Category(code, name, description, new LinkedHashSet<>(List.of(keywords)))));
    }

    private void seedServices() {
        service("CORE_DB", "Core Database Cluster", "Primary PostgreSQL cluster backing business systems",
                BusinessCriticality.MISSION_CRITICAL, "Database Team", "DATABASE");
        service("AUTH_SSO", "Authentication & SSO", "Identity provider, single sign-on and MFA",
                BusinessCriticality.MISSION_CRITICAL, "Identity Team", "AUTHENTICATION", "CORE_DB");
        service("NETWORK", "Corporate Network & Wi-Fi", "Office LAN, Wi-Fi and internet breakout",
                BusinessCriticality.HIGH, "Network Team", "NETWORK");
        service("PAYMENT", "Payment Gateway", "Customer payments, refunds and checkout",
                BusinessCriticality.MISSION_CRITICAL, "Payments Team", "APPLICATION", "CORE_DB", "AUTH_SSO");
        service("EMAIL", "Email & Calendar", "Corporate e-mail and calendaring",
                BusinessCriticality.HIGH, "Collaboration Team", "APPLICATION", "AUTH_SSO");
        service("VPN", "VPN Remote Access", "Remote access for staff working off-site",
                BusinessCriticality.HIGH, "Network Team", "NETWORK", "AUTH_SSO", "NETWORK");
        service("ERP", "ERP (Finance & Procurement)", "Finance, invoicing and procurement",
                BusinessCriticality.HIGH, "Business Apps Team", "APPLICATION", "CORE_DB", "AUTH_SSO");
        service("HR_PORTAL", "HR Self-Service Portal", "Leave, payroll slips and HR requests",
                BusinessCriticality.MEDIUM, "Business Apps Team", "APPLICATION", "CORE_DB", "AUTH_SSO");
        service("CLOUD_STORAGE", "Cloud File Storage", "Shared drives and document storage",
                BusinessCriticality.MEDIUM, "Infrastructure Team", "STORAGE", "AUTH_SSO");
        service("END_USER", "End-User Devices", "Laptops, desktops, printers and peripherals",
                BusinessCriticality.LOW, "Desktop Support", "HARDWARE");
    }

    private void service(String code, String name, String description, BusinessCriticality criticality, String team,
                         String defaultCategory, String... dependsOn) {
        ITService s = new ITService(code, name, description, criticality, team, categories.get(defaultCategory));
        for (String dep : dependsOn) s.addDependency(services.get(dep));
        services.put(code, serviceRepository.save(s));
    }

    private void seedSlaPolicies() {
        slaRepository.save(new SlaPolicy("P1 - Critical (default)", Priority.P1, 5, 30, 75, null));
        slaRepository.save(new SlaPolicy("P2 - High (default)", Priority.P2, 15, 120, 75, null));
        slaRepository.save(new SlaPolicy("P3 - Medium (default)", Priority.P3, 60, 480, 75, null));
        slaRepository.save(new SlaPolicy("P4 - Low (default)", Priority.P4, 240, 1440, 75, null));
        // Revenue-critical service gets stricter targets
        slaRepository.save(new SlaPolicy("Payment Gateway P1", Priority.P1, 5, 20, 70, services.get("PAYMENT")));
        slaRepository.save(new SlaPolicy("Payment Gateway P2", Priority.P2, 10, 60, 70, services.get("PAYMENT")));
    }

    // ================================================================ users

    private void seedUsers() {
        user(Role.ADMIN, "A001", "Priya Sharma", "admin@itsm.local", "IT Operations", null, null);
        user(Role.IT_MANAGER, "M001", "Rajesh Iyer", "manager@itsm.local", "IT Management", null, null);

        TeamLead infraLead = (TeamLead) user(Role.TEAM_LEAD, "L301", "Ananya Gupta", "lead.infra@itsm.local", "Infrastructure", 9, 4);
        TeamLead appsLead = (TeamLead) user(Role.TEAM_LEAD, "L302", "Vikram Rao", "lead@itsm.local", "Business Applications", 10, 4);
        skills(infraLead, "NETWORK", "CLOUD", "STORAGE", "HARDWARE");
        skills(appsLead, "APPLICATION", "DATABASE", "AUTHENTICATION", "SECURITY");

        technician("T201", "Arjun Mehta", "tech@itsm.local", 6, 5, infraLead, "NETWORK", "CLOUD");
        technician("T202", "Sneha Patel", "sneha.patel@itsm.local", 4, 5, appsLead, "DATABASE", "APPLICATION");
        technician("T203", "Rahul Verma", "rahul.verma@itsm.local", 2, 6, infraLead, "HARDWARE", "SOFTWARE");
        technician("T204", "Kavya Nair", "kavya.nair@itsm.local", 7, 5, appsLead, "AUTHENTICATION", "SECURITY");
        technician("T205", "Rohan Das", "rohan.das@itsm.local", 3, 5, appsLead, "APPLICATION", "SOFTWARE", "DATABASE");
        technician("T206", "Meera Joshi", "meera.joshi@itsm.local", 5, 5, infraLead, "STORAGE", "CLOUD", "NETWORK");
        ((Technician) users.get("T202")).replaceServiceExpertise(Set.of(services.get("CORE_DB"), services.get("ERP")));
        ((Technician) users.get("T204")).replaceServiceExpertise(Set.of(services.get("AUTH_SSO")));
        ((Technician) users.get("T205")).replaceServiceExpertise(Set.of(services.get("PAYMENT"), services.get("HR_PORTAL")));

        user(Role.EMPLOYEE, "E101", "Asha Rao", "employee@itsm.local", "Finance", null, null);
        user(Role.EMPLOYEE, "E102", "Karan Singh", "karan.singh@itsm.local", "Sales", null, null);
        user(Role.EMPLOYEE, "E103", "Divya Menon", "divya.menon@itsm.local", "Human Resources", null, null);
        user(Role.EMPLOYEE, "E104", "Nikhil Kumar", "nikhil.kumar@itsm.local", "Operations", null, null);
        user(Role.EMPLOYEE, "E105", "Isha Agarwal", "isha.agarwal@itsm.local", "Marketing", null, null);

        // Phone numbers + SMS opt-in for on-call staff, to demonstrate the SMS channel
        users.get("T201").updateProfile("Arjun Mehta", "Infrastructure", "+91 98765 43201");
        users.get("T201").updatePreferences(new NotificationPreferences(true, true));
        users.get("L302").updateProfile("Vikram Rao", "Business Applications", "+91 98765 43302");
        users.get("L302").updatePreferences(new NotificationPreferences(true, true));
        users.get("M001").updateProfile("Rajesh Iyer", "IT Management", "+91 98765 43001");
        users.get("M001").updatePreferences(new NotificationPreferences(true, true));
    }

    private User user(Role role, String code, String name, String email, String department, Integer exp, Integer max) {
        User u = UserFactory.create(role, code, name, email, passwordEncoder.encode(DEMO_PASSWORD), department, exp, max);
        users.put(code, userRepository.save(u));
        return u;
    }

    private void technician(String code, String name, String email, int exp, int max, TeamLead lead, String... skillCodes) {
        Technician t = (Technician) user(Role.TECHNICIAN, code, name, email, lead.getDepartment(), exp, max);
        t.joinTeam(lead);
        skills(t, skillCodes);
    }

    private void skills(Technician t, String... codes) {
        Set<Category> set = new LinkedHashSet<>();
        for (String c : codes) set.add(categories.get(c));
        t.replaceSkills(set);
    }

    // ================================================================ incidents

    private record Template(String title, String description, String service, String category, Impact impact,
                            Urgency urgency, int users, String resolution, String rootCause, String tech) {}

    private static final List<Template> HISTORY = List.of(
            new Template("Payment gateway timing out at checkout",
                    "Customers report the checkout page spinning and payment transactions failing with a timeout after 30 seconds.",
                    "PAYMENT", "APPLICATION", Impact.HIGH, Urgency.CRITICAL, 500,
                    "Restarted payment API pods and increased the database connection pool from 20 to 50; transactions are processing normally.",
                    "Database connection pool exhaustion under peak load", "T205"),
            new Template("Unable to login with SSO",
                    "Getting 'invalid SAML response' when signing in to all internal apps via SSO since this morning.",
                    "AUTH_SSO", "AUTHENTICATION", Impact.HIGH, Urgency.HIGH, 120,
                    "Renewed the expired SAML signing certificate on the identity provider and re-published metadata.",
                    "Expired IdP signing certificate", "T204"),
            new Template("VPN disconnects every few minutes",
                    "Remote staff VPN connection drops every 5-10 minutes and users have to reconnect repeatedly.",
                    "VPN", "NETWORK", Impact.MEDIUM, Urgency.HIGH, 40,
                    "Adjusted VPN keep-alive and MTU settings on the concentrator and pushed an updated client profile.",
                    "MTU mismatch causing tunnel renegotiation", "T201"),
            new Template("Laptop screen flickering",
                    "My laptop display flickers constantly and sometimes goes black when the lid is moved.",
                    "END_USER", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 1,
                    "Replaced the display cable under warranty.", "Damaged display ribbon cable", "T203"),
            new Template("Outlook not syncing emails",
                    "Outlook desktop is stuck on 'Updating inbox' and new mail has not arrived since yesterday.",
                    "EMAIL", "APPLICATION", Impact.MEDIUM, Urgency.MEDIUM, 1,
                    "Rebuilt the local OST profile and cleared stale credentials from Credential Manager.",
                    "Corrupted local mail cache", "T205"),
            new Template("Wi-Fi very slow on 3rd floor",
                    "Internet over the 3rd floor Wi-Fi is extremely slow; pages take a minute to load.",
                    "NETWORK", "NETWORK", Impact.MEDIUM, Urgency.MEDIUM, 60,
                    "Replaced faulty access point AP-3F-02 and rebalanced wireless channels.",
                    "Failed wireless access point", "T206"),
            new Template("ERP month-end report failing",
                    "The finance month-end report in ERP fails with a 'query timeout' error.",
                    "ERP", "DATABASE", Impact.HIGH, Urgency.HIGH, 15,
                    "Added the missing index on ledger_entries(period) and rebuilt statistics; the report completes in 40 seconds.",
                    "Missing database index after schema upgrade", "T202"),
            new Template("Phishing email received by many staff",
                    "Received a suspicious email asking to reset my password via an external link; several colleagues got it too.",
                    "EMAIL", "SECURITY", Impact.MEDIUM, Urgency.HIGH, 200,
                    "Blocked the sender domain, purged the messages from all mailboxes and issued a security awareness notice.",
                    "Targeted phishing campaign", "T204"),
            new Template("Shared drive quota exceeded",
                    "Cannot save files to the team shared drive; the error says the storage quota is exceeded.",
                    "CLOUD_STORAGE", "STORAGE", Impact.LOW, Urgency.MEDIUM, 12,
                    "Increased the team quota to 2 TB and archived files older than three years.",
                    "Team storage quota reached", "T206"),
            new Template("HR portal leave page error 500",
                    "The HR self-service portal shows error 500 when applying for leave.",
                    "HR_PORTAL", "APPLICATION", Impact.MEDIUM, Urgency.MEDIUM, 300,
                    "Rolled back faulty HR portal release 4.2.1 and re-deployed 4.2.0.",
                    "Regression in the latest portal release", "T205"),
            new Template("Excel crashes when opening large files",
                    "Excel crashes immediately when opening spreadsheets larger than 20 MB.",
                    "END_USER", "SOFTWARE", Impact.LOW, Urgency.LOW, 1,
                    "Updated Office to the latest build and disabled a faulty third-party add-in.",
                    "Incompatible Excel add-in", "T203"),
            new Template("Database replication lag",
                    "Read replicas are several minutes behind the primary and reports show stale data.",
                    "CORE_DB", "DATABASE", Impact.HIGH, Urgency.HIGH, 100,
                    "Tuned WAL settings and upgraded replica storage IOPS; lag is back under one second.",
                    "Replica disk I/O saturation", "T202"),
            new Template("Password reset not sending OTP",
                    "Self-service password reset never sends the OTP SMS.",
                    "AUTH_SSO", "AUTHENTICATION", Impact.MEDIUM, Urgency.HIGH, 25,
                    "Renewed the SMS provider API credentials in the identity platform.",
                    "Expired SMS gateway credentials", "T204"),
            new Template("Printer not working in finance",
                    "The finance department printer shows offline for everyone.",
                    "END_USER", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 10,
                    "Reassigned a static IP to the printer after a DHCP conflict and restarted the print spooler.",
                    "IP address conflict", "T203"),
            new Template("Cloud VM out of disk space",
                    "The analytics VM in the cloud reports disk full and scheduled jobs are failing.",
                    "CLOUD_STORAGE", "CLOUD", Impact.MEDIUM, Urgency.HIGH, 5,
                    "Expanded the VM disk volume and configured log rotation.",
                    "Unrotated application logs filled the disk", "T201"),
            new Template("Payment refunds not processing",
                    "Refund transactions are stuck in pending state in the payment gateway.",
                    "PAYMENT", "APPLICATION", Impact.HIGH, Urgency.HIGH, 80,
                    "Restarted the refund worker queue consumer and replayed the stuck messages.",
                    "Message queue consumer crashed", "T205"),
            new Template("Malware alert on workstation",
                    "Antivirus flagged a trojan on my workstation after I opened an attachment.",
                    "END_USER", "SECURITY", Impact.MEDIUM, Urgency.HIGH, 1,
                    "Isolated the machine, removed the malware, reimaged the workstation and reset credentials.",
                    "Malicious email attachment", "T204"),
            new Template("Email delivery to clients delayed",
                    "Emails to external clients are delayed by over an hour.",
                    "EMAIL", "APPLICATION", Impact.MEDIUM, Urgency.HIGH, 150,
                    "Cleared the mail transport queue and fixed the DNS SPF record.",
                    "Misconfigured SPF record causing deferrals", "T205"));

    private void seedIncidents() {
        Instant now = clock.instant();
        String[] reporters = {"E101", "E102", "E103", "E104", "E105"};

        // ~40 historical incidents over the last 30 days, mostly closed
        for (int n = 0; n < 40; n++) {
            Template t = HISTORY.get(n % HISTORY.size());
            Instant reported = now.minus(Duration.ofHours(30L * 24 - n * 17L - random.nextInt(6)));
            if (reported.isAfter(now.minus(Duration.ofDays(2)))) reported = now.minus(Duration.ofDays(2 + random.nextInt(3)));
            Employee reporter = (Employee) users.get(reporters[n % reporters.length]);
            boolean late = n % 7 == 3; // some SLA breaches for realistic compliance figures
            historical(t, reporter, reported, late, n % 5 == 4 ? IncidentStatus.VERIFIED : IncidentStatus.CLOSED, 3 + random.nextInt(3));
        }

        // A resolved incident awaiting the demo employee's verification
        historical(new Template("External monitor not detected",
                        "My docking station no longer detects the external monitor after the latest Windows update.",
                        "END_USER", "HARDWARE", Impact.LOW, Urgency.MEDIUM, 1,
                        "Updated the docking station firmware and display drivers; both monitors are detected again.",
                        "Outdated dock firmware incompatible with OS update", "T203"),
                (Employee) users.get("E101"), now.minus(Duration.ofHours(26)), false, IncidentStatus.RESOLVED, 0);

        // Live, active incidents - including a correlated cluster around the Core Database
        active("Core database CPU at 100%", "Monitoring shows the primary database CPU pinned at 100% and queries queueing.",
                "CORE_DB", "DATABASE", Impact.CRITICAL, Urgency.HIGH, 300, "E104", "T202", IncidentStatus.ACKNOWLEDGED, 95);
        active("ERP invoices page very slow", "Opening the invoices list in ERP takes over two minutes and often times out.",
                "ERP", "DATABASE", Impact.HIGH, Urgency.HIGH, 20, "E101", "T205", IncidentStatus.IN_PROGRESS, 180);
        active("HR portal timing out", "The HR portal times out when loading payslips; error 504 after a long wait.",
                "HR_PORTAL", "APPLICATION", Impact.MEDIUM, Urgency.HIGH, 50, "E103", "T205", IncidentStatus.ASSIGNED, 120);
        active("VPN authentication failing for some users", "About 15 remote users cannot authenticate to the VPN; others are fine.",
                "VPN", "NETWORK", Impact.MEDIUM, Urgency.MEDIUM, 15, "E102", "T201", IncidentStatus.PENDING_INFO, 360);
        active("Cloud storage sync errors", "The desktop sync client shows 'sync pending' errors for the marketing shared drive.",
                "CLOUD_STORAGE", "STORAGE", Impact.MEDIUM, Urgency.MEDIUM, 8, "E105", "T206", IncidentStatus.IN_PROGRESS, 540);
        active("Laptop battery not charging", "My laptop battery stays at 0% and only works when plugged in.",
                "END_USER", "HARDWARE", Impact.LOW, Urgency.LOW, 1, "E101", null, IncidentStatus.CLASSIFIED, 300);
        active("Teams calls dropping audio", "Microsoft Teams calls lose audio for several seconds every few minutes.",
                "NETWORK", "SOFTWARE", Impact.LOW, Urgency.MEDIUM, 4, "E102", "T203", IncidentStatus.ASSIGNED, 25);
    }

    /** A past incident taken through its lifecycle with realistic timings. */
    private void historical(Template t, Employee reporter, Instant reported, boolean late, IncidentStatus target, int rating) {
        Incident i = create(t.title(), t.description(), t.service(), t.category(), t.impact(), t.urgency(), t.users(), reporter, reported);
        Technician tech = (Technician) users.get(t.tech());
        int responseMin = i.getSla().getResponseTargetMinutes();
        int resolutionMin = i.getSla().getResolutionTargetMinutes();

        Instant assigned = reported.plus(Duration.ofMinutes(2));
        assign(i, tech, assigned);
        Instant ack = assigned.plus(Duration.ofMinutes(Math.max(1, late ? responseMin + 5 : responseMin / 2)));
        i.acknowledge(ack);
        status(i, tech, ack, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, "Technician acknowledged incident");
        Instant start = ack.plus(Duration.ofMinutes(3));
        i.startWork(start);
        status(i, tech, start, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, "Investigation started");
        i.addNote(tech, NoteType.INVESTIGATION, "Investigating: checked logs and recent changes for " + t.service() + ".", true, start.plus(Duration.ofMinutes(5)));

        long fraction = late ? 130 : 35 + random.nextInt(45);
        Instant resolvedAt = reported.plus(Duration.ofMinutes(Math.max(10, resolutionMin * fraction / 100)));
        if (!resolvedAt.isAfter(start)) resolvedAt = start.plus(Duration.ofMinutes(5));
        i.resolve(new Resolution(t.resolution(), t.rootCause(), false), resolvedAt);
        i.addNote(tech, NoteType.RESOLUTION, t.resolution(), false, resolvedAt);
        status(i, tech, resolvedAt, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED, "Resolution provided; incident marked RESOLVED");
        if (target == IncidentStatus.RESOLVED) return;

        Instant verified = resolvedAt.plus(Duration.ofMinutes(20 + random.nextInt(120)));
        i.verify(verified);
        status(i, reporter, verified, IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, "Resolution verified");
        if (rating > 0) {
            i.submitFeedback(new Feedback(rating, rating >= 4 ? "Quick and clear, thanks!" : "Took a while but fixed.", verified));
            audit(i, AuditAction.FEEDBACK_SUBMITTED, reporter, verified, null, null, "Reporter feedback: " + rating + "/5");
        }
        if (target == IncidentStatus.VERIFIED) return;

        Instant closed = verified.plus(Duration.ofMinutes(10));
        i.close(ClosureCode.RESOLVED, "Confirmed by reporter", closed);
        status(i, tech, closed, IncidentStatus.VERIFIED, IncidentStatus.CLOSED, "Incident CLOSED");
    }

    /** A currently-open incident in the requested state, reported {@code minutesAgo} minutes ago. */
    private void active(String title, String description, String service, String category, Impact impact, Urgency urgency,
                        int affected, String reporterCode, String techCode, IncidentStatus target, int minutesAgo) {
        Instant reported = clock.instant().minus(Duration.ofMinutes(minutesAgo));
        Incident i = create(title, description, service, category, impact, urgency, affected, users.get(reporterCode), reported);
        if (techCode == null) return;
        Technician tech = (Technician) users.get(techCode);
        Instant assigned = reported.plus(Duration.ofMinutes(1));
        assign(i, tech, assigned);
        if (target == IncidentStatus.ASSIGNED) return;
        Instant ack = assigned.plus(Duration.ofMinutes(Math.min(10, minutesAgo / 6)));
        i.acknowledge(ack);
        status(i, tech, ack, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, "Technician acknowledged incident");
        if (target == IncidentStatus.ACKNOWLEDGED) return;
        Instant start = ack.plus(Duration.ofMinutes(2));
        i.startWork(start);
        status(i, tech, start, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, "Investigation started");
        i.addNote(tech, NoteType.INVESTIGATION, "Initial triage done; collecting diagnostics.", true, start.plus(Duration.ofMinutes(3)));
        if (target == IncidentStatus.IN_PROGRESS) return;
        Instant asked = start.plus(Duration.ofMinutes(20));
        i.addNote(tech, NoteType.INFO_REQUEST, "Which VPN client version are the affected users running? Please share a screenshot of the error.", false, asked);
        i.requestInformation(asked);
        status(i, tech, asked, IncidentStatus.IN_PROGRESS, IncidentStatus.PENDING_INFO, "Additional information requested from reporter");
    }

    private Incident create(String title, String description, String serviceCode, String categoryCode, Impact impact,
                            Urgency urgency, int affected, User reporter, Instant reported) {
        ITService service = services.get(serviceCode);
        Incident i = IncidentFactory.report(title, description, reporter, service, impact, urgency, affected, reported);
        incidentRepository.save(i);
        i.assignNumber(1000 + i.getId());
        audit(i, AuditAction.INCIDENT_CREATED, reporter, reported, null, "NEW",
                i.getNumber() + " created by " + reporter.displayName() + ": \"" + title + "\" on " + service.getName());

        Category category = categories.get(categoryCode);
        Instant classified = reported.plusSeconds(20);
        i.classify(category, classified);
        audit(i, AuditAction.INCIDENT_CLASSIFIED, null, classified, null, category.getCode(),
                "Incident classified as " + category.getCode() + " (automatic)");

        PriorityAssessment a = priorityStrategy.assess(new PriorityContext(impact, urgency, service.getCriticality(), affected, service.getId()));
        i.applyPriority(a.priority(), a.score(), a.rationale());
        audit(i, AuditAction.PRIORITY_CALCULATED, null, classified, null, a.priority().name(),
                "Priority calculated as " + a.priority().display() + ". " + a.rationale());
        SlaPolicy policy = slaResolver.resolve(service, a.priority());
        i.applySlaPolicy(policy);
        audit(i, AuditAction.SLA_APPLIED, null, classified, null, null, "SLA \"" + policy.getName() + "\" applied");
        return i;
    }

    private void assign(Incident i, Technician tech, Instant at) {
        i.assignTo(tech, at);
        audit(i, AuditAction.INCIDENT_ASSIGNED, null, at, null, tech.getUserCode(),
                "Assigned to Technician " + tech.displayName() + " (automatic, strategy: smart)");
    }

    private void status(Incident i, User actor, Instant at, IncidentStatus from, IncidentStatus to, String description) {
        AuditAction action = switch (to) {
            case RESOLVED -> AuditAction.INCIDENT_RESOLVED;
            case CLOSED -> AuditAction.INCIDENT_CLOSED;
            case PENDING_INFO -> AuditAction.INFO_REQUESTED;
            default -> AuditAction.STATUS_CHANGED;
        };
        audit(i, action, actor, at, from.name(), to.name(), description);
    }

    private void audit(Incident i, AuditAction action, User actor, Instant at, String from, String to, String description) {
        AuditEntry.Builder b = AuditEntry.builder(action, "INCIDENT", at)
                .entity(i.getId(), i.getNumber())
                .transition(from, to)
                .description(description);
        if (actor != null) b.actor(actor.getId(), actor.displayName(), actor.getRole().name());
        auditRepository.save(b.build());
    }

    /** Exposed for tests. */
    public List<String> demoAccounts() {
        return new ArrayList<>(List.of("admin@itsm.local", "manager@itsm.local", "lead@itsm.local", "tech@itsm.local", "employee@itsm.local"));
    }
}
