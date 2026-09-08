package demo.relay;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import static demo.relay.Contracts.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:relay-dns;DB_CLOSE_DELAY=-1","loomspan.observability.enabled=false"})
@WithMockUser(username="commander",roles="COMMANDER")
class DnsTest {
    @Autowired IncidentStore store;
    Report report(Receipt e) { return new Report("Network failure",e.observation(),"high",List.of(e.id()),e.actions().stream().map(a->new Recommendation(a.id(),e.id(),"Supported by network evidence")).toList(),"Repair then verify"); }
    void diagnose(String id) { store.markRunning(id);Receipt e=store.probe(id,"inspectNetwork");store.complete(id,report(e),"test",List.of()); }
    @Test void dnsFailureMasksFirewallEvidenceWithoutChangingLocalDatabaseHealth() {
        for(boolean firewall:List.of(true,false)) {
            World w=new World(1,true,true,firewall,false,true,60,true,90,false);
            assertThat(Simulation.checkout(w).success()).isFalse();assertThat(Simulation.capacity(w).successfulRps()).isZero();
            assertThat(Simulation.dataLoad(w).databaseDemandOps()).isZero();
            assertThat(Simulation.probe(w,"inspectApplication").text()).contains("UnknownHostException");
            assertThat(Simulation.probe(w,"inspectDatabase").text()).contains("SELECT 1 pass");
            assertThat(Simulation.probe(w,"inspectNetwork").actions()).extracting(Action::id).containsExactly("RESTORE_DB_DNS");
            assertThat(Simulation.probe(w,"inspectNetwork").text()).contains("NXDOMAIN","not yet established");
            World fixed=Simulation.repair(w,"RESTORE_DB_DNS");assertThat(fixed.linkAllowed()).isEqualTo(firewall);
            assertThat(Simulation.recovery(fixed).success()).isEqualTo(firewall);
        }
    }
    @Test void oldSnapshotsKeepTheirMeaningAndOtherRepairsPreserveDnsFault() {
        World old=new World(1,true,true,true,false,true,60,true,90);
        assertThat(old.dnsHealthy()).isNull();assertThat(Simulation.checkout(old).success()).isTrue();
        for(String action:IncidentStore.REPAIR_ACTIONS) {
            if(action.equals("RESTORE_DB_DNS")) continue;
            assertThat(Simulation.repair(old.withDns(false),action).dnsHealthy()).isFalse();
        }
    }
    @Test void unrelatedControlsPreserveDnsAndTheDnsControlUsesRevisionFencing() {
        store.preset(new Preset("dns",store.world().revision()));int revision=store.world().revision();
        store.traffic(new Traffic(150,revision));store.cache(new CacheSettings(true,20,store.world().revision()));
        store.control(new Control("checkout",false,store.world().revision()));assertThat(store.world().dnsHealthy()).isFalse();
        assertThatThrownBy(()->store.control(new Control("dns",true,revision))).isInstanceOf(ApiProblem.class);
        store.control(new Control("dns",true,store.world().revision()));assertThat(store.world().dnsHealthy()).isTrue();
        assertThat(store.world().checkoutRunning()).isFalse();assertThat(store.world().cacheHitPercent()).isEqualTo(20);
    }
    @Test void combinedRecoveryRequiresNewEvidenceBetweenRepairs() {
        store.preset(new Preset("dns-compound",store.world().revision()));String id=store.create(new NewIncident(null)).id();
        Operation op=store.startOperation(id,new InvestigationOptions("AUTO",List.of("RESTORE_DB_DNS","RESTORE_DB_LINK"),2));
        diagnose(op.currentRunId());Run original=store.run(op.currentRunId());
        assertThat(original.report().recommendations()).extracting(Recommendation::actionId).containsExactly("RESTORE_DB_DNS");
        String next=store.advanceOperation(op.id());assertThat(next).isNotNull();
        assertThat(store.world().dnsHealthy()).isTrue();assertThat(store.world().linkAllowed()).isFalse();
        assertThat(store.activities(id)).anyMatch(a->a.kind().equals("VERIFICATION")&&!a.measurement().checkout().success());
        assertThat(store.run(next).snapshot().revision()).isEqualTo(original.snapshot().revision()+1);
        diagnose(next);assertThat(store.run(next).report().recommendations()).extracting(Recommendation::actionId).containsExactly("RESTORE_DB_LINK");
        store.advanceOperation(op.id());assertThat(store.operation(op.id()).status()).isEqualTo("RESOLVED");
        assertThat(store.operation(op.id()).repairs()).isEqualTo(2);assertThat(store.run(original.id()).snapshot().dnsHealthy()).isFalse();
    }
    @Test @WithMockUser(username="responder",roles="RESPONDER")
    void responderCannotAuthorizeDnsThroughManualOrAutomaticPaths() {
        store.preset(new Preset("dns",store.world().revision()));String id=store.create(new NewIncident(null)).id();
        Operation op=store.startOperation(id,new InvestigationOptions("AUTO",List.of("RESTORE_DB_DNS"),2));diagnose(op.currentRunId());
        World before=store.world();store.advanceOperation(op.id());assertThat(store.operation(op.id()).status()).isEqualTo("ROLE_BLOCKED");
        assertThatThrownBy(()->store.repair(id,new RepairRequest(op.currentRunId(),"RESTORE_DB_DNS"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(store.world()).isEqualTo(before);
    }
}
