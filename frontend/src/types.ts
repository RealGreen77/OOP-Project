// Types mirroring the backend DTOs (com.itsm.web.dto).

export type Role = 'EMPLOYEE' | 'TECHNICIAN' | 'TEAM_LEAD' | 'ADMIN' | 'IT_MANAGER';
export type Permission =
  | 'REPORT_INCIDENT' | 'VIEW_OWN_INCIDENTS' | 'VIEW_ALL_INCIDENTS' | 'WORK_INCIDENT' | 'ASSIGN_INCIDENT'
  | 'CLOSE_INCIDENT' | 'VIEW_TEAM_WORKLOAD' | 'MANAGE_USERS' | 'MANAGE_CATALOG' | 'MANAGE_SLA'
  | 'CONFIGURE_STRATEGY' | 'VIEW_REPORTS' | 'VIEW_AUDIT' | 'RECEIVE_ESCALATIONS';

export type IncidentStatus =
  | 'NEW' | 'CLASSIFIED' | 'ASSIGNED' | 'ACKNOWLEDGED' | 'IN_PROGRESS' | 'PENDING_INFO' | 'RESOLVED' | 'VERIFIED' | 'CLOSED';
export type IncidentAction =
  | 'CLASSIFY' | 'ASSIGN' | 'REASSIGN' | 'ACKNOWLEDGE' | 'START_WORK' | 'REQUEST_INFO' | 'PROVIDE_INFO' | 'RESOLVE'
  | 'VERIFY' | 'REOPEN' | 'CLOSE' | 'MARK_DUPLICATE' | 'UPDATE_DETAILS' | 'ADD_NOTE' | 'FEEDBACK';
export type Priority = 'P1' | 'P2' | 'P3' | 'P4';
export type Level = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type Criticality = 'LOW' | 'MEDIUM' | 'HIGH' | 'MISSION_CRITICAL';
export type SlaStatus = 'ON_TRACK' | 'AT_RISK' | 'PAUSED' | 'BREACHED' | 'MET' | 'NOT_APPLICABLE';
export type Availability = 'AVAILABLE' | 'BUSY' | 'ON_LEAVE' | 'OFFLINE';
export type NoteType = 'COMMENT' | 'INVESTIGATION' | 'INFO_REQUEST' | 'INFO_RESPONSE' | 'RESOLUTION' | 'SYSTEM';

export interface Ref { id: number; code: string; name: string }
export interface UserRef { id: number; userCode: string; fullName: string; role: Role }

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number }

export interface TechnicianProfile {
  experienceYears: number;
  maxConcurrentIncidents: number;
  availability: Availability;
  skills: Ref[];
  serviceExpertise: Ref[];
  teamLead?: UserRef;
  activeIncidents: number;
}

export interface User {
  id: number;
  userCode: string;
  fullName: string;
  email: string;
  role: Role;
  department?: string;
  phone?: string;
  active: boolean;
  lastLoginAt?: string;
  createdAt?: string;
  emailNotifications: boolean;
  smsNotifications: boolean;
  permissions: Permission[];
  technician?: TechnicianProfile;
}

export interface AuthResponse { token: string; expiresAt: string; user: User }

export interface Category { id: number; code: string; name: string; description?: string; keywords: string[]; active: boolean }
export interface Service {
  id: number; code: string; name: string; description?: string; criticality: Criticality; ownerTeam?: string;
  defaultCategory?: Ref; dependencies: Ref[]; active: boolean;
}
export interface SlaPolicy {
  id: number; name: string; priority: Priority; responseMinutes: number; resolutionMinutes: number;
  warningThresholdPercent: number; service?: Ref; active: boolean;
}

export interface Sla {
  policyName: string; responseTargetMinutes: number; resolutionTargetMinutes: number;
  responseDeadline: string; resolutionDeadline: string; respondedAt?: string;
  responseBreached: boolean; resolutionBreached: boolean; paused: boolean; status: SlaStatus; elapsedPercent: number;
}
export interface SlaRisk { riskScore: number; level: 'LOW' | 'MEDIUM' | 'HIGH' | 'BREACHED'; predictedResolution: string; deadline: string; factors: string[] }
export interface Note { id: number; type: NoteType; content: string; internal: boolean; author?: UserRef; createdAt: string }

export interface IncidentSummary {
  id: number; number: string; title: string; status: IncidentStatus; priority?: Priority; impact: Level; urgency: Level;
  category?: Ref; service: Ref; reporter: UserRef; assignedTo?: UserRef; reportedAt: string; slaStatus: SlaStatus;
  resolutionDeadline?: string; escalationLevel: number; suspectedDuplicate: boolean;
}

export interface IncidentDetail {
  id: number; number: string; title: string; description: string; status: IncidentStatus; allowedActions: IncidentAction[];
  priority?: Priority; priorityScore?: number; priorityRationale?: string; impact: Level; urgency: Level; affectedUsers: number;
  category?: Ref; service: Ref; serviceCriticality: Criticality; reporter: UserRef; assignedTo?: UserRef;
  reportedAt: string; assignedAt?: string; acknowledgedAt?: string; resolvedAt?: string; verifiedAt?: string; closedAt?: string;
  sla?: Sla; slaRisk?: SlaRisk; resolution?: { summary: string; rootCause?: string; workaround: boolean };
  closureCode?: string; closureNotes?: string; feedback?: { rating: number; comment?: string; submittedAt: string };
  escalationLevel: number; reopenCount: number; duplicateOf?: Ref; suspectedDuplicateOf?: Ref; suspectedDuplicateScore?: number;
  notes: Note[]; version: number;
}

export interface AuditEntry {
  id: number; occurredAt: string; actorName: string; actorRole?: string; action: string; entityType: string;
  entityId?: number; entityRef?: string; previousState?: string; newState?: string; description: string;
}

export interface Notification {
  id: number; channel: 'IN_APP' | 'EMAIL' | 'SMS'; eventType: string; title: string; message: string;
  incidentId?: number; incidentNumber?: string; read: boolean; createdAt: string;
  deliveryStatus: 'DELIVERED' | 'SIMULATED' | 'FAILED'; destination?: string; recipient?: UserRef; failureReason?: string;
}

export interface TechnicianWorkload {
  technician: UserRef; availability: Availability; activeIncidents: number; maxConcurrentIncidents: number;
  loadPercent: number; skills: string[]; experienceYears: number; teamLead?: UserRef;
}

export interface CategorySuggestion { category: Ref; confidence: number; matchedKeywords: string[] }
export interface SimilarIncident {
  id: number; number: string; title: string; status: IncidentStatus; service: Ref; reportedAt: string;
  score: number; textSimilarity: number; reasons: string[];
}
export interface PriorityPreview {
  priority: Priority; score: number; rationale: string; strategy: string; responseMinutes?: number; resolutionMinutes?: number;
}
export interface Analysis { categorySuggestions: CategorySuggestion[]; possibleDuplicates: SimilarIncident[]; predictedPriority?: PriorityPreview }
export interface ResolutionSuggestion {
  incidentId: number; incidentNumber: string; title: string; category?: Ref; similarity: number; summary: string; rootCause?: string;
}
export interface Recommendation { technician: UserRef; score: number; activeIncidents: number; maxIncidents: number; reasons: string[] }
export interface RecommendationResponse { strategy: string; strategyName: string; recommendations: Recommendation[] }
export interface ClusterIncident { id: number; number: string; title: string; priority?: Priority; status: IncidentStatus; service: Ref; reportedAt: string }
export interface CorrelationCluster {
  probableRoot: Ref; size: number; majorIncident: boolean; explanation: string;
  directIncidents: ClusterIncident[]; dependentIncidents: ClusterIncident[];
}
export interface SlaRiskItem {
  incidentId: number; number: string; title: string; priority?: Priority; status: IncidentStatus; assignedTo?: UserRef;
  riskScore: number; level: string; predictedResolution: string; deadline: string; factors: string[];
}

export interface LabelCount { label: string; count: number }
export interface Dashboard {
  overview: {
    totalIncidents: number; openIncidents: number; criticalOpen: number; resolvedIncidents: number; closedIncidents: number;
    slaViolations: number; escalatedOpen: number; unassignedOpen: number; meanTimeToAcknowledgeMinutes?: number;
    meanTimeToResolveMinutes?: number; slaCompliancePercent?: number; averageRating?: number;
  };
  byStatus: LabelCount[]; byPriority: LabelCount[]; byCategory: LabelCount[]; byService: LabelCount[];
  technicianWorkload: { technician: UserRef; availability: string; activeIncidents: number; maxIncidents: number; resolvedTotal: number; averageRating?: number }[];
  slaCompliance: { priority: string; resolved: number; breached: number; compliancePercent: number }[];
  trend: { date: string; reported: number; resolved: number }[];
}

export interface StrategyOption { key: string; displayName: string; description: string }
export interface StrategySettings {
  activePriorityStrategy: string; activeAssignmentStrategy: string; autoAssign: boolean;
  priorityStrategies: StrategyOption[]; assignmentStrategies: StrategyOption[];
}

/** RFC 7807 problem detail returned by the backend for every error. */
export interface ProblemDetail {
  status: number; title: string; detail: string; errorCode: string; fieldErrors?: Record<string, string>;
  [key: string]: unknown;
}
