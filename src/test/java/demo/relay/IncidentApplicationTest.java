package demo.relay;

import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static demo.relay.Contracts.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:relay-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "loomspan.observability.enabled=false","execution-trace.persistence=ONERROR"})
class IncidentApplicationTest {
    @Autowired IncidentStore store;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired SkillTemplate skills;
    @LocalServerPort int port;
    @BeforeEach void reset() {
        for(String table:List.of("repair","activity","evidence","investigation","incident")) db.update("delete from "+table);
        db.update("update environment set revision=1,checkout_running=true,database_running=true,link_allowed=true,bad_deploy=false");
    }
    private Run prepare(String preset) {
        store.preset(new Preset(preset,store.world().revision()));
        Incident i=store.create(new NewIncident(null)); Run r=store.begin(i.id()); store.markRunning(r.id()); return r;
    }
    private Report report(Receipt e) {
        return new Report("Observed "+e.probe(),e.observation(),"high",List.of(e.id()),e.actions().stream().map(a->new Recommendation(a.id(),e.id(),"Supported by recorded observation")).toList(),"Apply one repair, then verify recovery.");
    }
    private void finish(Run r,Receipt e) { store.complete(r.id(),report(e),"test-session",List.of()); }
    @Test void simulationRemainsConsistentAcrossEveryFaultCombination() {
        for(int mask=0;mask<16;mask++) {
            World w=new World(1,(mask&1)==0,(mask&2)==0,(mask&4)==0,(mask&8)!=0);
            assertThat(Simulation.checkout(w).success()).isEqualTo(mask==0);
            assertThat(Simulation.probe(w,"inspectNetwork").actions().isEmpty()).isEqualTo(w.linkAllowed());
            assertThat(Simulation.probe(w,"inspectDatabase").actions().isEmpty()).isEqualTo(w.databaseRunning());
        }
    }
    @Test void blockedConnectionHasHealthyProcessesAndRepairsNeedVerification() {
        Run r=prepare("connection");
        Receipt app=store.probe(r.id(),"inspectApplication"), database=store.probe(r.id(),"inspectDatabase"), network=store.probe(r.id(),"inspectNetwork");
        assertThat(app.observation()).contains("liveness UP","504");
        assertThat(database.observation()).contains("process UP");
        assertThat(network.actions()).extracting(Action::id).containsExactly("RESTORE_DB_LINK");
        finish(r,network); store.repair(r.incidentId(),new RepairRequest(r.id(),"RESTORE_DB_LINK"));
        assertThat(store.incident(r.incidentId()).status()).isEqualTo("MITIGATED");
        assertThat(store.verify(r.incidentId()).success()).isTrue();
        assertThat(store.detail(r.incidentId()).incident().status()).isEqualTo("RESOLVED");
        assertThat(store.detail(r.incidentId()).runs().getFirst().evidence()).hasSize(3);
    }
    @Test void compoundFailureRequiresAnotherInvestigationAfterFirstRepair() {
        Run r=prepare("compound"); Receipt app=store.probe(r.id(),"inspectApplication");finish(r,app);
        store.repair(r.incidentId(),new RepairRequest(r.id(),"ROLLBACK_CHECKOUT"));
        assertThat(store.world().linkAllowed()).isFalse();
        assertThat(store.verify(r.incidentId()).success()).isFalse();
        assertThat(store.incident(r.incidentId()).status()).isEqualTo("OPEN");
        Run second=store.begin(r.incidentId());store.markRunning(second.id());Receipt network=store.probe(second.id(),"inspectNetwork");finish(second,network);
        store.repair(r.incidentId(),new RepairRequest(second.id(),"RESTORE_DB_LINK"));
        assertThat(store.verify(r.incidentId()).success()).isTrue();
        assertThat(store.detail(r.incidentId()).runs()).hasSize(2);
    }
    @Test void snapshotCannotChangeMidInvestigationAndStaleRepairIsRejected() {
        Run r=prepare("connection");store.preset(new Preset("healthy",store.world().revision()));
        Receipt e=store.probe(r.id(),"inspectNetwork");assertThat(e.observation()).contains("denies");finish(r,e);
        int revision=store.world().revision();
        assertThatThrownBy(()->store.repair(r.incidentId(),new RepairRequest(r.id(),"RESTORE_DB_LINK"))).isInstanceOf(ApiProblem.class).hasMessageContaining("environment changed");
        assertThat(store.world().revision()).isEqualTo(revision);
    }
    @Test void rejectsInventedAndCrossRunEvidenceAndUnproposedActions() {
        Run r=prepare("connection"); Receipt e=store.probe(r.id(),"inspectNetwork");
        Report bad=new Report("a","b","high",List.of(e.id()),List.of(new Recommendation("START_DATABASE",e.id(),"fiction")),"verify");
        assertThatThrownBy(()->store.complete(r.id(),bad,null,List.of())).isInstanceOf(ApiProblem.class);
        Run other=prepare("connection");store.probe(other.id(),"inspectNetwork");
        assertThatThrownBy(()->store.complete(other.id(),report(e),null,List.of())).isInstanceOf(ApiProblem.class).hasMessageContaining("outside");
        finish(r,e);
        assertThatThrownBy(()->store.repair(r.incidentId(),new RepairRequest(r.id(),"START_DATABASE"))).isInstanceOf(ApiProblem.class);
        assertThat(store.world().linkAllowed()).isFalse();
    }
    @Test void repairIsIdempotentAndConcurrentCopiesMutateOnce() throws Exception {
        Run r=prepare("connection");finish(r,store.probe(r.id(),"inspectNetwork"));int before=store.world().revision();
        try(var executor=Executors.newFixedThreadPool(2)) {
            Callable<World> task=()->store.repair(r.incidentId(),new RepairRequest(r.id(),"RESTORE_DB_LINK"));
            var futures=executor.invokeAll(List.of(task,task));for(var f:futures) assertThat(f.get().linkAllowed()).isTrue();
        }
        assertThat(store.world().revision()).isEqualTo(before+1);
        assertThat(store.activities(r.incidentId()).stream().filter(a->a.kind().equals("REPAIR"))).hasSize(1);
    }
    @Test void duplicateInvestigationAndWritesAfterFailureAreRejected() {
        Run r=prepare("connection");
        assertThatThrownBy(()->store.begin(r.incidentId())).isInstanceOf(ApiProblem.class);
        store.probe(r.id(),"inspectNetwork");store.fail(r.id(),"Model unavailable",null,List.of());
        assertThatThrownBy(()->store.probe(r.id(),"inspectApplication")).isInstanceOf(ApiProblem.class);
        assertThat(store.run(r.id()).evidence()).hasSize(1);
        assertThat(store.incident(r.incidentId()).status()).isEqualTo("OPEN");
    }
    @Test void missingControlFieldsCannotSilentlyStopAService() {
        assertThatThrownBy(()->store.control(new Control("checkout",null,1))).isInstanceOf(ApiProblem.class);
        assertThatThrownBy(()->store.preset(new Preset("connection",null))).isInstanceOf(ApiProblem.class);
        assertThat(store.world()).isEqualTo(new World(1,true,true,true,false));
    }
    @Test void stoppedDatabaseSurvivesNetworkRepairAndPreventsResolution() {
        Run r=prepare("connection");
        store.control(new Control("database",false,store.world().revision()));
        store.fail(r.id(),"New state requested",null,List.of());
        Run fresh=store.begin(r.incidentId());store.markRunning(fresh.id());finish(fresh,store.probe(fresh.id(),"inspectNetwork"));
        store.repair(fresh.incidentId(),new RepairRequest(fresh.id(),"RESTORE_DB_LINK"));
        assertThat(store.world().databaseRunning()).isFalse();
        assertThat(store.verify(fresh.incidentId()).success()).isFalse();
        Run last=store.begin(r.incidentId());store.markRunning(last.id());finish(last,store.probe(last.id(),"inspectDatabase"));
        store.repair(last.incidentId(),new RepairRequest(last.id(),"START_DATABASE"));
        assertThat(store.verify(last.incidentId()).success()).isTrue();
    }
    @Test void interruptedRunsRecoverWithoutErasingEvidence() {
        Run r=prepare("connection");store.probe(r.id(),"inspectNetwork");store.recoverInterrupted();
        assertThat(store.run(r.id()).status()).isEqualTo("FAILED");
        assertThat(store.run(r.id()).evidence()).hasSize(1);
        assertThat(store.begin(r.incidentId()).snapshot()).isEqualTo(store.world());
    }
    @Test void javaProbeRunsThroughSupportedFacadeWithoutAProvider() {
        Run r=prepare("connection");var views=new ArrayList<ai.loomspan.api.SkillExecutionView>();
        Receipt e=json.readValue(skills.invoke("inspectNetwork",Map.of("runId",r.id()),views::add),Receipt.class);
        assertThat(e.actions()).extracting(Action::id).containsExactly("RESTORE_DB_LINK");
        assertThat(views).hasSize(1);
        assertThat(views.getFirst().events()).extracting(ai.loomspan.api.SkillExecutionEvent::type).contains("SKILL_STARTED","SKILL_FINISHED");
    }
    @Test void httpFlowReturnsStructuredErrorsAndDurableIncidentDetail() throws Exception {
        HttpClient client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port+"/api";
        var create=client.send(HttpRequest.newBuilder(URI.create(base+"/incidents")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"Checkout unavailable\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(create.statusCode()).isEqualTo(200);Incident i=json.readValue(create.body(),Incident.class);
        var get=client.send(HttpRequest.newBuilder(URI.create(base+"/incidents/"+i.id())).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(json.readValue(get.body(),Detail.class).incident().title()).isEqualTo("Checkout unavailable");
        var stale=client.send(HttpRequest.newBuilder(URI.create(base+"/environment/preset")).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"connection\",\"expectedRevision\":99}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(stale.statusCode()).isEqualTo(409);assertThat(stale.body()).contains("environment changed");
    }
    @Test void productionCodeUsesOnlySupportedLoomspanApi() throws Exception {
        try(var files=Files.walk(Path.of("src/main/java"))) {
            for(Path file:files.filter(p->p.toString().endsWith(".java")).toList())
                assertThat(Files.readString(file)).doesNotContain("ai.loomspan.internal","ai.loomspan.autoconfigure");
        }
    }
}
