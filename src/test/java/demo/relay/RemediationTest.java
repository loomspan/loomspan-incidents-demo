package demo.relay;

import ai.loomspan.api.SkillTemplate;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import static demo.relay.Contracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:remediation-tests;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000","loomspan.observability.enabled=false","execution-trace.persistence=ONERROR"})
class RemediationTest {
    @Autowired IncidentStore store;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @BeforeEach void reset() {
        for(String table:List.of("repair","activity","evidence","investigation","operation","incident")) db.update("delete from "+table);
        store.preset(new Preset("cache-compound",store.world().revision()));
    }
    Operation start(String mode,List<String> allowed,int limit) {
        return store.startOperation(store.create(new NewIncident(null)).id(),new InvestigationOptions(mode,allowed,limit));
    }
    Report report(Receipt e) { return new Report("Cache dependency failure",e.observation(),"high",List.of(e.id()),e.actions().stream().map(a->new Recommendation(a.id(),e.id(),"Observed by database probe")).toList(),"Verify after one repair."); }
    void diagnose(String id) { store.markRunning(id);store.complete(id,report(store.probe(id,"inspectDatabase")),"test",List.of()); }
    @Test void twoStageRepairUsesFreshSnapshotsAndResolves() {
        Operation op=start("AUTO",List.of("START_CACHE","RESTORE_CACHE_HIT_RATE"),2);
        diagnose(op.currentRunId());String next=store.advanceOperation(op.id());
        assertThat(next).isNotNull();assertThat(store.world().cacheRunning()).isTrue();assertThat(store.world().cacheHitPercent()).isEqualTo(20);
        assertThat(store.run(op.currentRunId()).snapshot().cacheRunning()).isFalse();
        assertThat(store.run(next).snapshot().revision()).isEqualTo(op.expectedRevision()+1);
        diagnose(next);assertThat(store.advanceOperation(op.id())).isNull();
        assertThat(store.operation(op.id()).status()).isEqualTo("RESOLVED");assertThat(store.operation(op.id()).repairs()).isEqualTo(2);
        assertThat(store.incident(op.incidentId()).status()).isEqualTo("RESOLVED");assertThat(store.world().demandRps()).isEqualTo(150);
        assertThat(store.activities(op.incidentId())).filteredOn(a->a.kind().equals("VERIFICATION")).hasSize(2);
    }
    @Test void observeCannotAuthorizeRepairsAndRecommendWaitsForOperator() {
        Operation observe=start("OBSERVE",List.of("START_CACHE"),2);diagnose(observe.currentRunId());
        assertThat(store.run(observe.currentRunId()).report().recommendations()).isEmpty();
        store.advanceOperation(observe.id());
        assertThatThrownBy(()->store.repair(observe.incidentId(),new RepairRequest(observe.currentRunId(),"START_CACHE"))).isInstanceOf(ApiProblem.class);
        World before=store.world();Operation rec=start("RECOMMEND",List.of(),2);diagnose(rec.currentRunId());store.advanceOperation(rec.id());
        assertThat(store.world()).isEqualTo(before);assertThat(store.operation(rec.id()).status()).isEqualTo("COMPLETED");
        store.repair(rec.incidentId(),new RepairRequest(rec.currentRunId(),"START_CACHE"));assertThat(store.world().cacheRunning()).isTrue();
    }
    @Test void permissionsAndLimitsStopWithoutClaimingRecovery() {
        Operation blocked=start("AUTO",List.of("START_DATABASE"),2);World before=store.world();diagnose(blocked.currentRunId());store.advanceOperation(blocked.id());
        assertThat(store.operation(blocked.id()).status()).isEqualTo("POLICY_BLOCKED");assertThat(store.world()).isEqualTo(before);
        Operation limited=start("AUTO",List.of("START_CACHE","RESTORE_CACHE_HIT_RATE"),1);diagnose(limited.currentRunId());store.advanceOperation(limited.id());
        assertThat(store.operation(limited.id()).status()).isEqualTo("LIMIT_REACHED");assertThat(store.operation(limited.id()).repairs()).isEqualTo(1);
        assertThat(store.incident(limited.incidentId()).status()).isEqualTo("OPEN");assertThat(store.world().cacheHitPercent()).isEqualTo(20);
    }
    @Test void environmentChangesInvalidateTheEntireOperation() {
        Operation op=start("AUTO",List.of("START_CACHE"),2);diagnose(op.currentRunId());
        store.traffic(new Traffic(160,store.world().revision()));World before=store.world();store.advanceOperation(op.id());
        assertThat(store.operation(op.id()).status()).isEqualTo("STALE");assertThat(store.world()).isEqualTo(before);
    }
    @Test void manualWorkAndDuplicateStartsAreFencedUntilStop() {
        Operation op=start("AUTO",List.of("START_CACHE"),2);store.markRunning(op.currentRunId());Receipt e=store.probe(op.currentRunId(),"inspectDatabase");
        assertThatThrownBy(()->store.begin(op.incidentId())).isInstanceOf(ApiProblem.class);
        assertThatThrownBy(()->store.startOperation(op.incidentId(),null)).isInstanceOf(ApiProblem.class);
        assertThatThrownBy(()->store.verify(op.incidentId())).isInstanceOf(ApiProblem.class);
        assertThatThrownBy(()->store.repair(op.incidentId(),new RepairRequest(op.currentRunId(),"START_CACHE"))).isInstanceOf(ApiProblem.class);
        store.stop(op.incidentId(),op.id());store.complete(op.currentRunId(),report(e),"late",List.of());
        assertThat(store.run(op.currentRunId()).status()).isEqualTo("FAILED");assertThat(store.advanceOperation(op.id())).isNull();
        assertThatThrownBy(()->store.probe(op.currentRunId(),"inspectDatabase")).isInstanceOf(ApiProblem.class);
        assertThat(store.world().cacheRunning()).isFalse();
    }
    @Test void concurrentAdvanceCannotApplyTheSameRepairTwice() throws Exception {
        Operation op=start("AUTO",List.of("START_CACHE"),2);diagnose(op.currentRunId());int revision=store.world().revision();
        try(var executor=Executors.newFixedThreadPool(2)) {
            var tasks=executor.invokeAll(List.<Callable<String>>of(()->store.advanceOperation(op.id()),()->store.advanceOperation(op.id())));
            for(var task:tasks) task.get();
        }
        assertThat(store.world().revision()).isEqualTo(revision+1);assertThat(store.operation(op.id()).repairs()).isEqualTo(1);
        assertThat(store.operation(op.id()).status()).isEqualTo("ACTIVE");
    }
    @Test void restartStopsActiveOperationsButPreservesCompletedReports() {
        Operation op=start("AUTO",List.of("START_CACHE"),2);diagnose(op.currentRunId());store.recoverInterrupted();
        assertThat(store.operation(op.id()).status()).isEqualTo("STOPPED");assertThat(store.run(op.currentRunId()).status()).isEqualTo("COMPLETE");
        assertThat(store.world().cacheRunning()).isFalse();
    }
    @Test void unsupportedPolicyAndExcessiveLimitsAreRejected() {
        String id=store.create(new NewIncident(null)).id();
        for(InvestigationOptions options:List.of(new InvestigationOptions("AUTO",List.of("DELETE_DATABASE"),2),new InvestigationOptions("AUTO",List.of(),4),new InvestigationOptions("AUTO",List.of(),0),new InvestigationOptions("OTHER",List.of(),2)))
            assertThatThrownBy(()->store.startOperation(id,options)).isInstanceOf(ApiProblem.class);
        assertThat(store.operations(id)).isEmpty();
    }
    @Test void oneCorrectionCanRecoverAnInvalidReportUsingTheSameEvidence() {
        SkillTemplate skills=mock(SkillTemplate.class);Operation op=start("RECOMMEND",List.of(),2);
        when(skills.invoke(eq("investigateIncidentLoad"),anyMap(),any())).thenAnswer(call->{store.probe(op.currentRunId(),"inspectDatabase");return "{}";});
        when(skills.invoke(eq("correctIncidentReport"),anyMap(),any())).thenAnswer(call->{
            Map<String,Object> input=call.getArgument(1);assertThat(input.get("receipts").toString()).contains("START_CACHE");
            return json.writeValueAsString(report(store.receipts(op.currentRunId()).getFirst()));
        });
        var service=new InvestigationService(store,skills,json);try {service.executeOperation(op.id());} finally {service.close();}
        assertThat(store.run(op.currentRunId()).correction().status()).isEqualTo("ACCEPTED");
        assertThat(store.run(op.currentRunId()).evidence()).hasSize(1);assertThat(store.operation(op.id()).status()).isEqualTo("COMPLETED");
        verify(skills,times(1)).invoke(eq("correctIncidentReport"),anyMap(),any());
    }
    @Test void secondInvalidReportStopsAutoRepairWithoutMutation() {
        SkillTemplate skills=mock(SkillTemplate.class);Operation op=start("AUTO",List.of("START_CACHE"),2);World before=store.world();
        when(skills.invoke(eq("investigateIncidentLoad"),anyMap(),any())).thenAnswer(call->{store.probe(op.currentRunId(),"inspectDatabase");return "{}";});
        when(skills.invoke(eq("correctIncidentReport"),anyMap(),any())).thenReturn("{}");
        var service=new InvestigationService(store,skills,json);try {service.executeOperation(op.id());} finally {service.close();}
        assertThat(store.run(op.currentRunId()).correction().status()).isEqualTo("REJECTED");
        assertThat(store.operation(op.id()).status()).isEqualTo("FAILED");assertThat(store.world()).isEqualTo(before);
        verify(skills,times(1)).invoke(eq("correctIncidentReport"),anyMap(),any());
    }
    @Test void savedMeasurementsAndRunLinksSurviveLaterEnvironmentChanges() {
        Operation op=start("AUTO",List.of("START_CACHE","RESTORE_CACHE_HIT_RATE"),2);
        diagnose(op.currentRunId());String next=store.advanceOperation(op.id());diagnose(next);store.advanceOperation(op.id());
        var records=store.activities(op.incidentId());
        Activity opening=records.stream().filter(a->a.kind().equals("OPENED")).findFirst().orElseThrow();
        var checks=records.stream().filter(a->a.kind().equals("VERIFICATION")).toList();
        assertThat(opening.measurement().dataLoad().databaseDemandOps()).isEqualTo(300);
        assertThat(checks.get(1).measurement().dataLoad().databaseDemandOps()).isEqualTo(270);
        assertThat(checks.get(1).measurement().checkout().success()).isFalse();
        assertThat(checks.get(1).runId()).isEqualTo(op.currentRunId());
        assertThat(checks.getFirst().measurement().dataLoad().databaseDemandOps()).isEqualTo(165);
        assertThat(checks.getFirst().measurement().checkout().success()).isTrue();
        assertThat(checks.getFirst().runId()).isEqualTo(next);
        store.traffic(new Traffic(400,store.world().revision()));
        assertThat(store.activities(op.incidentId())).isEqualTo(records);
        assertThat(store.run(op.currentRunId()).measurement().capacity().successfulRps()).isEqualTo(100);
        assertThat(store.state().checkout().success()).isFalse();
    }
    @Test void v5DoesNotInventMeasurementsForEarlierVerificationRecords() {
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource("jdbc:h2:mem:presentation-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1","sa","");
        org.flywaydb.core.Flyway.configure().dataSource(source).target("4").load().migrate();
        var old=new JdbcTemplate(source);
        old.update("insert into incident values ('earlier','Earlier incident','RESOLVED','2026-09-07T00:00:00Z',null)");
        old.update("insert into activity(incident_id,created_at,kind,message,revision) values ('earlier','2026-09-07T00:00:00Z','VERIFICATION','Recovery verified.',7)");
        org.flywaydb.core.Flyway.configure().dataSource(source).load().migrate();
        Activity a=new IncidentStore(old,json).activities("earlier").getFirst();
        assertThat(a.message()).isEqualTo("Recovery verified.");assertThat(a.revision()).isEqualTo(7);
        assertThat(a.measurement()).isNull();assertThat(a.runId()).isNull();
    }
}
