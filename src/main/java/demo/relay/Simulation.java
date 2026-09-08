package demo.relay;

import java.util.ArrayList;
import java.util.List;
import static demo.relay.Contracts.*;

/** Pure, repeatable infrastructure behavior. Requests are an aggregate one-second batch. */
public final class Simulation {
    public static final int INSTANCE_CAPACITY = 100;
    private Simulation() {}
    public static boolean dnsWorks(World w) { return !Boolean.FALSE.equals(w.dnsHealthy()); }
    public static Measurement measurement(World w) { return new Measurement(checkout(w),capacity(w),dataLoad(w)); }

    public static DataLoad dataLoad(World w) {
        boolean modeled=w.cacheRunning()!=null;
        int online=(w.checkoutRunning()?1:0)+(Boolean.TRUE.equals(w.checkoutBRunning())?1:0);
        int admitted=w.badDeploy()||!dnsWorks(w)||!w.linkAllowed()||!w.databaseRunning()?0:Math.min(w.demandRps(),online*INSTANCE_CAPACITY);
        int hit=Boolean.TRUE.equals(w.cacheRunning())&&w.cacheHitPercent()!=null?w.cacheHitPercent():0;
        int hits=admitted*hit/100;
        int demand=modeled?2*admitted-hits:0;
        return new DataLoad(modeled,admitted,hit,hits,demand,200,modeled&&demand>200);
    }

    public static Capacity capacity(World w) {
        int target=w.checkoutBRunning()==null?1:2;
        int online=(w.checkoutRunning()?1:0)+(Boolean.TRUE.equals(w.checkoutBRunning())?1:0);
        int capacity=online*INSTANCE_CAPACITY;
        int successful=w.badDeploy()||!dnsWorks(w)||!w.linkAllowed()||!w.databaseRunning()?0:Math.min(capacity,w.demandRps());
        DataLoad d=dataLoad(w);
        if(d.saturated()) successful=successful*d.databaseCapacityOps()/d.databaseDemandOps();
        int failed=w.demandRps()-successful;
        String status=successful==0?"OUTAGE":failed>0?"DEGRADED":online<target?"AT_RISK":"HEALTHY";
        return new Capacity(w.demandRps(),online,target,capacity,successful,failed,status,online==target);
    }

    public static TransactionResult checkout(World w) {
        Capacity c=capacity(w);
        if(c.onlineInstances()==0) return new TransactionResult(false,"Gateway returned 503: no online checkout instance.",20,w.revision());
        if(w.badDeploy()) return new TransactionResult(false,"Checkout returned 500: order validation exception.",45,w.revision());
        if(!dnsWorks(w)) return new TransactionResult(false,"Checkout returned 503: database connection could not be established.",1000,w.revision());
        if(!w.linkAllowed()||!w.databaseRunning()) return new TransactionResult(false,"Checkout returned 504: database connection timed out.",3000,w.revision());
        if(dataLoad(w).saturated()) return new TransactionResult(false,"Database operations saturated: "+c.successfulRps()+" of "+c.demandRps()+" requests/s succeed; "+c.failedRps()+" fail. Checkout logs show slow database queries and dependency timeouts.",3000,w.revision());
        if(c.failedRps()>0) return new TransactionResult(false,"Capacity exceeded: "+c.successfulRps()+" of "+c.demandRps()+" requests/s succeed; "+c.failedRps()+" receive gateway 503 responses.",1000,w.revision());
        return new TransactionResult(true,"All "+c.demandRps()+" requests/s succeed. Customer checkout is available.",c.demandRps()>c.capacityRps()*0.8||dataLoad(w).databaseDemandOps()>160?600:120,w.revision());
    }

    public static String symptom(World w) {
        Capacity c=capacity(w);
        return checkout(w).message()+(c.onlineInstances()<c.targetInstances()?" Availability alert: "+c.onlineInstances()+" of "+c.targetInstances()+" checkout instances online; redundancy target is not met.":"");
    }

    public static RecoveryResult recovery(World w) {
        boolean customer=checkout(w).success(),redundancy=capacity(w).redundancyRestored();
        String message=customer&&redundancy?"Customer checkout and the instance availability target both pass. Recovery verified."
            :customer?"Customer checkout passes, but the instance availability target is not met. Restore the stopped instance before resolving this incident."
            :checkout(w).message()+(!redundancy?" The instance availability target is also not met.":"");
        return new RecoveryResult(customer&&redundancy,customer,redundancy,message,w.revision());
    }

    public record Observation(String text, List<Action> actions) {}
    private static Observation observation(String text,Action... actions) { return new Observation(text,List.of(actions)); }
    public static Observation probe(World w,String probe) {
        Capacity c=capacity(w);
        return switch(probe) {
            case "inspectApplication" -> {
                if(c.onlineInstances()==0) yield observation("Gateway is healthy. Checkout liveness DOWN on every instance. No requests reach application code. Inspect capacity and instance availability.");
                String health="Checkout liveness UP on "+c.onlineInstances()+" of "+c.targetInstances()+" instances. ";
                if(w.badDeploy()) yield observation(health+"Version checkout-2.0 is deployed to the entire pool. Requests return 500; logs show OrderValidator exception since deployment. Previous version checkout-1.9 passed release checks.",
                    new Action("ROLLBACK_CHECKOUT","Roll back checkout deployment","Restores checkout-1.9 across the pool; preserves instance power, traffic and network configuration."));
                if(!dnsWorks(w)) yield observation(health+"Version checkout-1.9, no recent deployment. Requests return 503; logs show UnknownHostException for orders-db.internal. No database connection was attempted. Ask the network specialist to test name resolution.");
                if(!w.linkAllowed()||!w.databaseRunning()) yield observation(health+"Version checkout-1.9, no recent deployment. Requests return 504; logs show database connection timeouts. Check the network path and database health.");
                if(dataLoad(w).saturated()) yield observation(health+"Version checkout-1.9. No deployment regression. Slow database queries and dependency timeouts; inspect database/cache load and pool capacity to distinguish the bottleneck.");
                yield observation(health+"Version checkout-1.9. No recent deployment or current application exceptions. "+
                    (c.failedRps()>0?"Admitted requests succeed, but the gateway rejects excess demand. Ask the capacity specialist for traffic and pool measurements.":"Customer requests succeed.")+
                    (!c.redundancyRestored()?" The pool is below its availability target; inspect capacity even though admitted requests succeed.":""));
            }
            case "inspectCapacity" -> {
                var actions=new ArrayList<Action>();
                if(!w.checkoutRunning()) actions.add(new Action(w.checkoutBRunning()==null?"START_CHECKOUT":"START_CHECKOUT_A","Start checkout A","Starts checkout A and adds 100 requests/s of online capacity; preserves deployment, traffic and dependencies."));
                if(Boolean.FALSE.equals(w.checkoutBRunning())) actions.add(new Action("START_CHECKOUT_B","Start checkout B","Starts checkout B and adds 100 requests/s of online capacity; preserves deployment, traffic and dependencies."));
                String nodes="Checkout A: "+(w.checkoutRunning()?"online":"stopped")+(w.checkoutBRunning()==null?"":"; checkout B: "+(w.checkoutBRunning()?"online":"stopped"))+". ";
                String demand="Incoming demand "+c.demandRps()+" requests/s; each online instance supports 100 requests/s. Online capacity "+c.capacityRps()+" requests/s. Gateway distributes work across online instances only. ";
                String outcome=c.demandRps()>c.capacityRps()?"Demand exceeds online capacity by "+(c.demandRps()-c.capacityRps())+" requests/s. ":"Current demand fits online capacity. ";
                yield new Observation(nodes+demand+outcome+"Availability target: "+c.targetInstances()+" online instances; observed "+c.onlineInstances()+". "+
                    (c.redundancyRestored()?"Instance availability target met. ":"Instance availability target NOT met. ")+
                    "These capacity figures do not prove application or database health. "+
                    (c.demandRps()>c.targetInstances()*100?"Demand exceeds the maximum configured pool capacity. No extra instances or traffic-shedding repair are available in this demo. Escalate capacity needs; do not invent a repair.":""),List.copyOf(actions));
            }
            case "inspectNetwork" -> !dnsWorks(w)
                ?observation("DNS lookup for orders-db.internal returns NXDOMAIN: the database hostname record is missing. Checkout hostname resolves correctly. The hostname-based TCP probe cannot run until DNS is restored; firewall reachability is not yet established.",
                    new Action("RESTORE_DB_DNS","Restore database DNS record","Restores orders-db.internal to the configured database address; preserves firewall rules, services, traffic and deployment. Verify and collect fresh network evidence afterward."))
                :w.linkAllowed()
                ?observation("DNS resolves checkout and database correctly. Firewall permits the checkout pool → database:5432. Gateway → checkout path is permitted. "+(w.databaseRunning()?"Database TCP handshake succeeds from the network probe.":"Database endpoint does not answer despite an allowed route; inspect database health."))
                :observation("DNS resolves correctly. Firewall rule DB-17 denies the checkout pool → database:5432. Connectivity probes are denied. Gateway → checkout path remains permitted.",
                    new Action("RESTORE_DB_LINK","Restore database connection","Allows both checkout instances → database:5432 through rule DB-17; preserves services, traffic and deployment."));
            case "inspectDatabase" -> {
                var actions=new ArrayList<Action>();
                if(!w.databaseRunning()) actions.add(new Action("START_DATABASE","Start database service","Starts database; preserves cache, firewall, traffic and deployment."));
                DataLoad d=dataLoad(w);
                String health=w.databaseRunning()?"Database process UP. Local readiness and SELECT 1 pass. ":"Database process DOWN; local readiness fails. ";
                if(d.modeled()) {
                    if(!w.cacheRunning()) actions.add(new Action("START_CACHE","Start cache service","Starts cache; preserves its configured hit rate, database, traffic and checkout instances."));
                    if(w.cacheHitPercent()<90) actions.add(new Action("RESTORE_CACHE_HIT_RATE","Restore cache hit rate","Restores configured cache hit rate to 90%; preserves cache power, database, traffic and checkout instances."));
                    health+="Cache "+(w.cacheRunning()?"UP":"DOWN")+"; configured hit rate "+w.cacheHitPercent()+"%, effective "+d.effectiveHitPercent()+"%. "+
                        d.admittedRps()+" admitted checkout requests/s; "+d.cacheHitsRps()+" cache hits/s. Each admitted checkout requires one database write plus one read on a cache miss. Offered database demand "+d.databaseDemandOps()+" operations/s; capacity 200 operations/s. "+
                        (d.saturated()?"Database is SATURATED; queries slow and requests time out. Starting checkout instances cannot increase database capacity. ":"Database is not saturated under the current admitted load. ");
                }
                yield new Observation(health+"Local readiness does not establish application reachability. Zero offered load can reflect an upstream fault; it does not prove recovery.",List.copyOf(actions));
            }
            default -> throw new IllegalArgumentException("Unknown probe");
        };
    }

    public static World repair(World w,String action) {
        boolean a=w.checkoutRunning(),db=w.databaseRunning(),link=w.linkAllowed(),bad=w.badDeploy();Boolean b=w.checkoutBRunning(),cache=w.cacheRunning();Integer hit=w.cacheHitPercent();Boolean dns=w.dnsHealthy();
        switch(action) {
            case "START_CHECKOUT","START_CHECKOUT_A" -> a=true;
            case "START_CHECKOUT_B" -> {if(b==null) throw new IllegalArgumentException("No checkout B in this snapshot");b=true;}
            case "START_DATABASE" -> db=true;
            case "START_CACHE" -> {if(cache==null) throw new IllegalArgumentException("No cache in this snapshot");cache=true;}
            case "RESTORE_CACHE_HIT_RATE" -> {if(cache==null) throw new IllegalArgumentException("No cache in this snapshot");hit=90;}
            case "RESTORE_DB_DNS" -> dns=true;
            case "RESTORE_DB_LINK" -> link=true;
            case "ROLLBACK_CHECKOUT" -> bad=false;
            default -> throw new IllegalArgumentException("Unknown repair");
        }
        return new World(w.revision()+1,a,db,link,bad,b,w.demandRps(),cache,hit,dns);
    }
}
