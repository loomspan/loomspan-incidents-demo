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
    public Run start(String incidentId) {
        Run run=store.begin(incidentId);
        try { executor.execute(()->execute(run.id())); }
        catch(RejectedExecutionException e) { store.fail(run.id(),"Investigation queue is full. Retry shortly.",null,List.of()); }
        return store.run(run.id());
    }
    void execute(String runId) {
        if(!store.markRunning(runId)) return;
        var session=new AtomicReference<String>();
        var events=new AtomicReference<List<ExecutionEvent>>(List.of());
        try {
            Run run=store.run(runId);
            // The model receives symptoms and an opaque snapshot handle, never fault switches or preset names.
            String result=skills.invoke("investigateIncident",Map.of("runId",runId,"ticket",store.incident(run.incidentId()).title(),"symptom",Simulation.symptom(run.snapshot())),view->{
                session.set(view.sessionId());
                events.set(view.events().stream().filter(e->Set.of("SKILL_STARTED","SKILL_FINISHED").contains(e.type()))
                    .map(e->new ExecutionEvent(e.timestamp().toString(),e.type(),e.frameId(),e.route())).toList());
            });
            store.complete(runId,json.readValue(result,Report.class),session.get(),events.get());
        } catch(RuntimeException e) {
            log.warn("Investigation {} failed ({}){}",runId,e.getClass().getSimpleName(),e instanceof ApiProblem?": "+e.getMessage():"");
            log.debug("Investigation failure",e);
            store.fail(runId,"Investigation could not produce a validated report. Check model configuration or Console diagnostics, then retry.",session.get(),events.get());
        }
    }
}
