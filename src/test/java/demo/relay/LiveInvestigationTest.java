package demo.relay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ai.loomspan.api.SkillTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static demo.relay.Contracts.*;
import static org.assertj.core.api.Assertions.*;

/** Opt-in real-provider verification. It never touches the presenter's file database. */
@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:relay-live;DB_CLOSE_DELAY=-1",
    "loomspan.observability.enabled=false"})
@EnabledIfSystemProperty(named="relay.live",matches="true")
class LiveInvestigationTest {
    @Autowired IncidentStore store;
    @Autowired InvestigationService investigations;
    @Autowired SkillTemplate skills;
    @Autowired ObjectMapper json;
    private Run investigate(String incidentId) {
        Run r=store.begin(incidentId);store.markRunning(r.id());
        var views=new ArrayList<ai.loomspan.api.SkillExecutionView>();
        String result=skills.invoke("investigateIncident",Map.of("runId",r.id(),"ticket",store.incident(incidentId).title(),"symptom",Simulation.symptom(r.snapshot())),views::add);
        var view=views.getFirst();
        try {
            store.complete(r.id(),json.readValue(result,Report.class),view.sessionId(),view.events().stream().filter(e->Set.of("SKILL_STARTED","SKILL_FINISHED").contains(e.type())).map(e->new ExecutionEvent(e.timestamp().toString(),e.type(),e.frameId(),e.route())).toList());
        } catch(RuntimeException e) { throw new AssertionError("Rejected model report: "+result+"; receipts="+store.receipts(r.id()),e); }
        r=store.run(r.id());
        assertThat(r.status()).withFailMessage("%s: %s",r.status(),r.error()).isEqualTo("COMPLETE");
        assertThat(r.sessionId()).isNotBlank();
        assertThat(r.events()).anyMatch(e->e.type().equals("SKILL_STARTED")&&e.route()!=null&&e.route().contains("investigateIncident"));
        assertThat(r.evidence()).isNotEmpty();return r;
    }
    @Test void compoundIncidentRepairsAndReinvestigatesWithRealNestedSkills() {
        store.preset(new Preset("compound",store.world().revision()));
        Incident i=store.create(new NewIncident(null));Run first=investigate(i.id());
        assertThat(first.report().recommendations()).extracting(Recommendation::actionId).contains("ROLLBACK_CHECKOUT");
        store.repair(i.id(),new RepairRequest(first.id(),"ROLLBACK_CHECKOUT"));
        assertThat(store.verify(i.id()).success()).isFalse();
        Run second=investigate(i.id());
        assertThat(second.evidence()).extracting(Receipt::probe).contains("inspectApplication","inspectNetwork","inspectDatabase");
        assertThat(second.report().recommendations()).extracting(Recommendation::actionId).contains("RESTORE_DB_LINK");
        store.repair(i.id(),new RepairRequest(second.id(),"RESTORE_DB_LINK"));
        assertThat(store.verify(i.id()).success()).isTrue();
        assertThat(store.incident(i.id()).status()).isEqualTo("RESOLVED");
    }
    @Test void capacitySpecialistDistinguishesRedundancyRiskFromOverload() {
        store.preset(new Preset("redundancy",store.world().revision()));
        Incident i=store.create(new NewIncident(null));Run risk=investigate(i.id());
        assertThat(store.state().checkout().success()).isTrue();
        assertThat(risk.evidence()).extracting(Receipt::probe).contains("inspectApplication","inspectCapacity");
        assertThat(risk.report().recommendations()).extracting(Recommendation::actionId).containsExactly("START_CHECKOUT_B");
        assertThat((risk.report().summary()+risk.report().likelyCause()).toLowerCase()).contains("redundan");
        assertThat(store.verify(i.id()).success()).isFalse();
        store.traffic(new Traffic(150,store.world().revision()));
        Run overloaded=investigate(i.id());
        assertThat(overloaded.report().recommendations()).extracting(Recommendation::actionId).containsExactly("START_CHECKOUT_B");
        assertThat(store.state().capacity().failedRps()).isEqualTo(50);
        store.repair(i.id(),new RepairRequest(overloaded.id(),"START_CHECKOUT_B"));
        assertThat(store.world().demandRps()).isEqualTo(150);
        assertThat(store.verify(i.id()).success()).isTrue();
    }
    @Test void fullPoolOverloadProducesNoUnsupportedScaleOutAction() {
        store.preset(new Preset("healthy",store.world().revision()));store.traffic(new Traffic(240,store.world().revision()));
        Incident i=store.create(new NewIncident(null));Run r=investigate(i.id());
        assertThat(r.evidence()).extracting(Receipt::probe).contains("inspectCapacity");
        assertThat(r.report().recommendations()).isEmpty();
        assertThat(store.verify(i.id()).success()).isFalse();
    }
    @Test void cacheFailuresCoordinateApplicationCapacityAndDatabaseEvidence() {
        for(String preset:List.of("cache","cache-degraded")) {
            store.preset(new Preset(preset,store.world().revision()));
            Incident i=store.create(new NewIncident(null));Run r=investigate(i.id());
            assertThat(r.evidence()).extracting(Receipt::probe).contains("inspectApplication","inspectCapacity","inspectDatabase");
            String action=preset.equals("cache")?"START_CACHE":"RESTORE_CACHE_HIT_RATE";
            assertThat(r.report().recommendations()).extracting(Recommendation::actionId).containsExactly(action);
            assertThat((r.report().summary()+r.report().likelyCause()).toLowerCase()).contains("cache");
            store.repair(i.id(),new RepairRequest(r.id(),action));
            assertThat(store.world().demandRps()).isEqualTo(150);
            assertThat(store.state().dataLoad().databaseDemandOps()).isEqualTo(165);
            assertThat(store.verify(i.id()).success()).isTrue();
        }
    }
}
