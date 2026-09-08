package demo.relay;

import java.util.List;

public final class Contracts {
    private Contracts() {}
    public record World(int revision, boolean checkoutRunning, boolean databaseRunning,
                        boolean linkAllowed, boolean badDeploy, Boolean checkoutBRunning, Integer demandRps) {
        public World { if(demandRps==null) demandRps=60; }
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
    public record Control(String control, Boolean enabled, Integer expectedRevision) {}
    public record Preset(String name, Integer expectedRevision) {}
    public record NewIncident(String title) {}
    public record RepairRequest(String runId, String actionId) {}
    public record Action(String id, String label, String effect) {}
    public record Receipt(String id, String runId, int revision, String observedAt,
                          String probe, String observation, List<Action> actions) {}
    public record Recommendation(String actionId, String evidenceId, String reason) {}
    public record Report(String summary, String likelyCause, String confidence,
                         List<String> evidenceIds, List<Recommendation> recommendations, String nextStep) {}
    public record ExecutionEvent(String timestamp, String type, String frameId, String route) {}
    public record Run(String id, String incidentId, String status, String createdAt, World snapshot,
                      Report report, String sessionId, List<ExecutionEvent> events, String error,
                      List<Receipt> evidence) {}
    public record Incident(String id, String title, String status, String createdAt, String lastRunId) {}
    public record Activity(long id, String incidentId, String createdAt, String kind, String message, int revision) {}
    public record Detail(Incident incident, List<Run> runs, List<Activity> activity) {}
    public record State(World environment, TransactionResult checkout, Capacity capacity, List<Incident> incidents, List<Activity> activity) {}
}
