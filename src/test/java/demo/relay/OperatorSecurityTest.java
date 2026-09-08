package demo.relay;

import ai.loomspan.api.SkillTemplate;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.ObjectMapper;
import static demo.relay.Contracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "spring.datasource.url=jdbc:h2:mem:relay-security;DB_CLOSE_DELAY=-1", "loomspan.observability.enabled=false"})
class OperatorSecurityTest {
    @LocalServerPort int port;
    @Autowired IncidentStore store;
    @Autowired SkillTemplate skills;
    @Autowired ObjectMapper json;
    static void identity(String name,String role) {
        var context=SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(name,"unused",List.of(new SimpleGrantedAuthority("ROLE_"+role))));
        SecurityContextHolder.setContext(context);
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    Run diagnosed(String preset) {
        store.preset(new Preset(preset,store.world().revision()));
        Run run=store.begin(store.create(new NewIncident(null)).id());store.markRunning(run.id());
        Receipt e=store.probe(run.id(),preset.equals("connection")?"inspectNetwork":"inspectDatabase");
        store.complete(run.id(),report(e),"test",List.of());return store.run(run.id());
    }
    Report report(Receipt e) { return new Report("Observed failure",e.observation(),"high",List.of(e.id()),e.actions().stream().map(a->new Recommendation(a.id(),e.id(),"Supported by evidence")).toList(),"Repair then verify"); }
    @Test void sessionsRequireAuthenticationAndCsrfAndLogoutInvalidatesAccess() throws Exception {
        var client=new SessionClient(port);
        assertThat(client.get("/state").statusCode()).isEqualTo(401);
        assertThat(client.send("/login","username=viewer&password=wrong","application/x-www-form-urlencoded",true).statusCode()).isEqualTo(401);
        client.login("viewer");assertThat(client.get("/state").statusCode()).isEqualTo(200);
        assertThat(client.get("/session").body()).contains("viewer","VIEWER");
        assertThat(client.post("/incidents","{}").statusCode()).isEqualTo(403);
        client.login("presenter");
        assertThat(client.send("/incidents","{}","application/json",false).statusCode()).isEqualTo(403);
        assertThat(client.post("/logout","{}").statusCode()).isEqualTo(204);
        assertThat(client.get("/state").statusCode()).isEqualTo(401);
    }
    @Test void presenterAndResponderCannotUseEachOthersEndpoints() throws Exception {
        var presenter=new SessionClient(port);presenter.login("presenter");
        var created=presenter.post("/incidents","{}");assertThat(created.statusCode()).isEqualTo(200);
        String id=json.readValue(created.body(),Incident.class).id();
        assertThat(presenter.post("/incidents/"+id+"/investigate","{}").statusCode()).isEqualTo(403);
        var responder=new SessionClient(port);responder.login("responder");
        World before=store.world();
        assertThat(responder.post("/environment/preset","{\"name\":\"connection\",\"expectedRevision\":"+before.revision()+"}").statusCode()).isEqualTo(403);
        assertThat(store.world()).isEqualTo(before);
    }
    @Test void responderDiagnosisCanBeHandedToCommanderForRepairWithDistinctAuditActors() throws Exception {
        identity("responder","RESPONDER");Run r=diagnosed("connection");World before=store.world();
        String body=json.writeValueAsString(new RepairRequest(r.id(),"RESTORE_DB_LINK"));
        var responder=new SessionClient(port);responder.login("responder");
        assertThat(responder.post("/incidents/"+r.incidentId()+"/repair",body).statusCode()).isEqualTo(403);
        assertThat(store.world()).isEqualTo(before);
        var commander=new SessionClient(port);commander.login("commander");
        assertThat(commander.post("/incidents/"+r.incidentId()+"/repair",body).statusCode()).isEqualTo(200);
        assertThat(commander.post("/incidents/"+r.incidentId()+"/verify","{}").statusCode()).isEqualTo(200);
        assertThat(store.activities(r.incidentId())).anyMatch(a->a.kind().equals("DIAGNOSIS")&&"responder".equals(a.actor()))
            .anyMatch(a->a.kind().equals("REPAIR")&&"commander".equals(a.actor()));
        assertThat(store.incident(r.incidentId()).status()).isEqualTo("RESOLVED");
    }
    @Test void routineRepairsRemainAvailableButRollbackAndNetworkNeedCommander() {
        identity("responder","RESPONDER");Run r=diagnosed("cache");
        store.repair(r.incidentId(),new RepairRequest(r.id(),"START_CACHE"));assertThat(store.world().cacheRunning()).isTrue();
        assertThatThrownBy(()->OperatorSecurity.requireRepair("ROLLBACK_CHECKOUT")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        SecurityContextHolder.clearContext();assertThatThrownBy(()->OperatorSecurity.requireRepair("START_CACHE")).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void automaticAllowlistCannotElevateResponderPrivileges() {
        identity("responder","RESPONDER");store.preset(new Preset("connection",store.world().revision()));
        String id=store.create(new NewIncident(null)).id();Operation op=store.startOperation(id,new InvestigationOptions("AUTO",List.of("RESTORE_DB_LINK"),2));
        store.markRunning(op.currentRunId());Receipt e=store.probe(op.currentRunId(),"inspectNetwork");store.complete(op.currentRunId(),report(e),"test",List.of());
        World before=store.world();store.advanceOperation(op.id());
        assertThat(store.operation(op.id()).status()).isEqualTo("ROLE_BLOCKED");assertThat(store.world()).isEqualTo(before);
        assertThat(store.operation(op.id()).repairs()).isZero();
    }
    @Test void frameworkRejectsViewerForJavaAndYamlSkillsBeforeTheyRun() {
        identity("viewer","VIEWER");
        assertThatThrownBy(()->skills.invoke("inspectNetwork",Map.of("runId","missing"))).hasStackTraceContaining("AccessDenied");
        assertThatThrownBy(()->skills.invoke("investigateIncident",Map.of("runId","missing","ticket","test","symptom","test"))).hasStackTraceContaining("AccessDenied");
        identity("responder","RESPONDER");
        store.preset(new Preset("connection",store.world().revision()));Run r=store.begin(store.create(new NewIncident(null)).id());store.markRunning(r.id());
        Receipt receipt=json.readValue(skills.invoke("inspectNetwork",Map.of("runId",r.id())),Receipt.class);
        assertThat(receipt.actions()).extracting(Action::id).containsExactly("RESTORE_DB_LINK");
    }
    @Test void backgroundOperationKeepsInitiatingIdentityAfterCallerChanges() throws Exception {
        var mock=mock(SkillTemplate.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        doAnswer(invocation->{
            entered.countDown();assertThat(release.await(10,TimeUnit.SECONDS)).isTrue();
            assertThat(OperatorSecurity.actor()).isEqualTo("responder");
            Map<String,Object> args=invocation.getArgument(1);Receipt e=store.probe((String)args.get("runId"),"inspectNetwork");
            return json.writeValueAsString(report(e));
        }).when(mock).invoke(anyString(),anyMap(),any());
        try(var ignored=new AutoCloseable() { public void close() { release.countDown(); } }) {
            var service=new InvestigationService(store,mock,json);
            try {
                identity("responder","RESPONDER");store.preset(new Preset("connection",store.world().revision()));
                String id=store.create(new NewIncident(null)).id();service.start(id,new InvestigationOptions("AUTO",List.of("RESTORE_DB_LINK"),1));
                assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("commander","unused",List.of(new SimpleGrantedAuthority("ROLE_COMMANDER"))));
                release.countDown();
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
                while(store.operations(id).getFirst().status().equals("ACTIVE") && System.nanoTime()<deadline) Thread.sleep(25);
                assertThat(store.operations(id).getFirst().status()).isEqualTo("ROLE_BLOCKED");
                assertThat(store.activities(id)).filteredOn(a->a.kind().equals("DIAGNOSIS")).allMatch(a->"responder".equals(a.actor()));
                assertThat(OperatorSecurity.actor()).isEqualTo("commander");
            } finally { service.close(); }
        }
    }
}
