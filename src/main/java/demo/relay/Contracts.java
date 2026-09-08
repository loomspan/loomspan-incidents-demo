package demo.relay;

import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record World(int revision, boolean checkoutRunning, boolean databaseRunning,
                        boolean linkAllowed, boolean badDeploy, Boolean checkoutBRunning, Integer demandRps,
                        Boolean cacheRunning, Integer cacheHitPercent) {
        public World { if(demandRps==null) demandRps=60; }
        public World(int revision, boolean a, boolean db, boolean link, boolean bad, Boolean b, Integer demand) {
            this(revision,a,db,link,bad,b,demand,null,null);
        }
        // Missing B identifies historical single-instance snapshots. Never rewrite their meaning.
        public World(int revision, boolean checkoutRunning, boolean databaseRunning, boolean linkAllowed, boolean badDeploy) {
            this(revision,checkoutRunning,databaseRunning,linkAllowed,badDeploy,null,60);
        }
    }
    public record Capacity(int demandRps, int onlineInstances, int targetInstances, int capacityRps,
                           int successfulRps, int failedRps, String status, boolean redundancyRestored) {}
    public record TransactionResult(boolean success, String message, int latencyMs, int revision) {}
    public record RecoveryResult(boolean success, boolean customerHealthy, boolean redundancyRestored, String message, int revision) {}
    public record Traffic(Integer demandRps, Integer expectedRevision) {}
    public record CacheSettings(Boolean running, Integer hitPercent, Integer expectedRevision) {}
    public record DataLoad(boolean modeled, int admittedRps, int effectiveHitPercent, int cacheHitsRps,
                           int databaseDemandOps, int databaseCapacityOps, boolean saturated) {}
    public record Control(String control, Boolean enabled, Integer expectedRevision) {}
    public record Preset(String name, Integer expectedRevision) {}
    public record NewIncident(String title) {}
    public record RepairRequest(String runId, String actionId) {}
    public record InvestigationOptions(String mode, List<String> allowedActions, Integer maxRepairs) {}
    public record Operation(String id, String incidentId, String createdAt, String mode,
                            List<String> allowedActions, int maxRepairs, int repairs, String status,
                            String currentRunId, int expectedRevision, String message) {}
    public record Correction(String status, String reason, String sessionId, List<ExecutionEvent> events) {}
    public record Action(String id, String label, String effect) {}
    public record Receipt(String id, String runId, int revision, String observedAt,
                          String probe, String observation, List<Action> actions) {}
    public record Recommendation(String actionId, String evidenceId, String reason) {}
    public record Report(String summary, String likelyCause, String confidence,
                         List<String> evidenceIds, List<Recommendation> recommendations, String nextStep) {}
    public record ExecutionEvent(String timestamp, String type, String frameId, String route) {}
    public record Measurement(TransactionResult checkout, Capacity capacity, DataLoad dataLoad) {}
    public record Run(String id, String incidentId, String status, String createdAt, World snapshot,
                      Report report, String sessionId, List<ExecutionEvent> events, String error,
                      List<Receipt> evidence, String mode, Correction correction, Measurement measurement) {}
    public record Incident(String id, String title, String status, String createdAt, String lastRunId) {}
    public record Activity(long id, String incidentId, String createdAt, String kind, String message, int revision,
                           String runId, Measurement measurement) {}
    public record Detail(Incident incident, List<Run> runs, List<Activity> activity, List<Operation> operations) {}
    public record State(World environment, TransactionResult checkout, Capacity capacity, DataLoad dataLoad, List<Incident> incidents, List<Activity> activity) {}
}
