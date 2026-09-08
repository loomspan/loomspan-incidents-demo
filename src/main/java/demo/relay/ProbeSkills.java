package demo.relay;

import ai.loomspan.api.SkillMethod;
import ai.loomspan.api.SkillParam;
import org.springframework.stereotype.Component;
import static demo.relay.Contracts.*;

@jakarta.annotation.security.RolesAllowed({"RESPONDER","COMMANDER"})
@Component
public class ProbeSkills {
    private final IncidentStore store;
    public ProbeSkills(IncidentStore store) { this.store=store; }
    @SkillMethod(description="Read checkout liveness, deployment history, errors and logs. Returns an immutable evidence receipt and any supported repairs.")
    public Receipt inspectApplication(@SkillParam(description="Exact investigation runId supplied by the caller") String runId) { return store.probe(runId,"inspectApplication"); }
    @SkillMethod(description="Measure checkout instance availability, gateway traffic and capacity. Distinguish lost redundancy from saturation and offer exact per-instance starts for stopped instances.")
    public Receipt inspectCapacity(@SkillParam(description="Exact investigation runId supplied by the caller") String runId) { return store.probe(runId,"inspectCapacity"); }
    @SkillMethod(description="Check DNS, checkout to database connectivity and firewall counters. Returns an immutable evidence receipt and any supported repairs.")
    public Receipt inspectNetwork(@SkillParam(description="Exact investigation runId supplied by the caller") String runId) { return store.probe(runId,"inspectNetwork"); }
    @SkillMethod(description="Check database process, readiness, cache service, hit rate and database operation demand versus capacity. Returns an immutable evidence receipt and any supported repairs.")
    public Receipt inspectDatabase(@SkillParam(description="Exact investigation runId supplied by the caller") String runId) { return store.probe(runId,"inspectDatabase"); }
    @SkillMethod(description="Read the authoritative evidence receipts already collected by this investigation's specialists. Call after all selected investigators complete to obtain exact receipt and supported repair IDs for the final report. Does not run probes.")
    public java.util.List<Receipt> readInvestigationEvidence(@SkillParam(description="Exact investigation runId supplied by the caller") String runId) {
        store.run(runId);
        var receipts=store.receipts(runId);
        if(receipts.isEmpty()) throw ApiProblem.conflict("Investigators must collect evidence before reading the final evidence record.");
        return receipts;
    }
}
