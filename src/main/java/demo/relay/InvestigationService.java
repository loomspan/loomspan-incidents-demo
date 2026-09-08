package demo.relay;

import ai.loomspan.api.SkillTemplate;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static demo.relay.Contracts.*;

@Service
public class InvestigationService {
    private static final Logger log=LoggerFactory.getLogger(InvestigationService.class);
    private final IncidentStore store;
    private final SkillTemplate skills;
    private final ObjectMapper json;
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(6));
    public InvestigationService(IncidentStore store,SkillTemplate skills,ObjectMapper json) { this.store=store;this.skills=skills;this.json=json; }
    @EventListener(ApplicationReadyEvent.class) public void recover() { store.recoverInterrupted(); }
    @PreDestroy public void close() { executor.shutdownNow(); }
    public Run start(String incidentId) { return start(incidentId,null); }
    public Run start(String incidentId,InvestigationOptions options) {
        Operation op=store.startOperation(incidentId,options);
        var caller=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        caller.setAuthentication(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication());
        try { executor.execute(new org.springframework.security.concurrent.DelegatingSecurityContextRunnable(()->executeOperation(op.id()),caller)); }
        catch(RejectedExecutionException e) { store.stopOperation(op.id(),"FAILED","Investigation queue is full. Retry shortly."); }
        return store.run(op.currentRunId());
    }
    void executeOperation(String operationId) {
        try {
            Operation op=store.operation(operationId);
            String runId=op.currentRunId();
            while(runId!=null && store.operation(operationId).status().equals("ACTIVE")) {
                execute(runId);
                runId=store.advanceOperation(operationId);
            }
        } catch(RuntimeException e) {
            log.warn("Operation {} stopped ({})",operationId,e.getClass().getSimpleName());
            store.stopOperation(operationId,"FAILED","Operation stopped after an execution error. Review evidence before retrying.");
        }
    }
    private Report checked(String value,String runId) {
        Report report=json.readValue(value,Report.class);
        IncidentStore.validate(report,store.receipts(runId));return report;
    }
    static List<ExecutionEvent> events(ai.loomspan.api.SkillExecutionView view) {
        return view.events().stream().filter(e->Set.of("SKILL_STARTED","SKILL_FINISHED").contains(e.type()))
            .map(e->new ExecutionEvent(e.timestamp().toString(),e.type(),e.frameId(),e.route())).toList();
    }
    void execute(String runId) {
        if(!store.markRunning(runId)) return;
        var session=new AtomicReference<String>();
        var observed=new AtomicReference<List<ExecutionEvent>>(List.of());
        try {
            Run run=store.run(runId);
            String symptom=Simulation.symptom(run.snapshot());
            String root=symptom.startsWith("Database operations saturated:")?"investigateIncidentLoad":"investigateIncident";
            String result=skills.invoke(root,Map.of("runId",runId,"ticket",store.incident(run.incidentId()).title(),"symptom",symptom),view->{
                session.set(view.sessionId());observed.set(events(view));
            });
            if(!store.run(runId).status().equals("RUNNING")) return;
            Report report;
            try { report=checked(result,runId); }
            catch(RuntimeException invalid) {
                // Exactly one application-level correction, using saved evidence and no tools.
                String reason=invalid instanceof ApiProblem?invalid.getMessage():"Report JSON did not match the required contract.";
                var correctionSession=new AtomicReference<String>();
                var correctionEvents=new AtomicReference<List<ExecutionEvent>>(List.of());
                store.correction(runId,new Correction("STARTED",reason,null,List.of()));
                try {
                    String corrected=skills.invoke("correctIncidentReport",Map.of("candidate",result,"receipts",json.writeValueAsString(store.receipts(runId)),"validationError",reason),view->{
                        correctionSession.set(view.sessionId());correctionEvents.set(events(view));
                    });
                    report=checked(corrected,runId);
                    store.correction(runId,new Correction("ACCEPTED",reason,correctionSession.get(),correctionEvents.get()));
                } catch(RuntimeException rejected) {
                    store.correction(runId,new Correction("REJECTED",reason,correctionSession.get(),correctionEvents.get()));
                    throw rejected;
                }
            }
            store.complete(runId,report,session.get(),observed.get());
        } catch(RuntimeException e) {
            log.warn("Investigation {} failed ({}){}",runId,e.getClass().getSimpleName(),e instanceof ApiProblem?": "+e.getMessage():"");
            log.debug("Investigation failure",e);
            store.fail(runId,"Investigation could not produce a validated report. Any permitted correction attempt is exhausted; review evidence before retrying.",session.get(),observed.get());
        }
    }
}
