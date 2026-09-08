export type World = {
  revision: number;
  checkoutRunning: boolean;
  databaseRunning: boolean;
  linkAllowed: boolean;
  badDeploy: boolean;
  checkoutBRunning: boolean | null;
  demandRps: number;
  cacheRunning: boolean | null;
  cacheHitPercent: number | null;
};
export type Capacity = {
  demandRps: number;
  onlineInstances: number;
  targetInstances: number;
  capacityRps: number;
  successfulRps: number;
  failedRps: number;
  status: "HEALTHY" | "AT_RISK" | "DEGRADED" | "OUTAGE";
  redundancyRestored: boolean;
};
export type Recovery = {
  success: boolean;
  customerHealthy: boolean;
  redundancyRestored: boolean;
  message: string;
  revision: number;
};
export type Check = {
  success: boolean;
  message: string;
  latencyMs: number;
  revision: number;
};
export type Action = { id: string; label: string; effect: string };
export type Receipt = {
  id: string;
  runId: string;
  revision: number;
  observedAt: string;
  probe: string;
  observation: string;
  actions: Action[];
};
export type Report = {
  summary: string;
  likelyCause: string;
  confidence: string;
  evidenceIds: string[];
  recommendations: { actionId: string; evidenceId: string; reason: string }[];
  nextStep: string;
};
export type Run = {
  id: string;
  incidentId: string;
  status: string;
  createdAt: string;
  snapshot: World;
  report: Report | null;
  sessionId: string | null;
  events: {
    timestamp: string;
    type: string;
    frameId: string | null;
    route: string | null;
  }[];
  error: string | null;
  evidence: Receipt[];
};
export type Incident = {
  id: string;
  title: string;
  status: string;
  createdAt: string;
  lastRunId: string | null;
};
export type Activity = {
  id: number;
  incidentId: string | null;
  createdAt: string;
  kind: string;
  message: string;
  revision: number;
};
export type Detail = { incident: Incident; runs: Run[]; activity: Activity[] };
export type State = {
  environment: World;
  checkout: Check;
  capacity: Capacity;
  dataLoad: {
    modeled: boolean;
    admittedRps: number;
    effectiveHitPercent: number;
    cacheHitsRps: number;
    databaseDemandOps: number;
    databaseCapacityOps: number;
    saturated: boolean;
  };
  incidents: Incident[];
  activity: Activity[];
};
