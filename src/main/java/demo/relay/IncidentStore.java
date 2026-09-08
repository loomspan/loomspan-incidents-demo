package demo.relay;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import static demo.relay.Contracts.*;

@Service
public class IncidentStore {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    public IncidentStore(JdbcTemplate db, ObjectMapper json) { this.db=db; this.json=json; }
    String encode(Object value) { return json.writeValueAsString(value); }
    <T> T decode(String value, Class<T> type) { return value==null?null:json.readValue(value,type); }
    static String id() { return UUID.randomUUID().toString(); }
    static String now() { return Instant.now().toString(); }
    private void lockWorld() { db.queryForObject("select revision from environment where id=1 for update",Integer.class); }
    private void lockIncident(String id) {
        if(db.query("select id from incident where id=? for update",(rs,n)->rs.getString(1),id).isEmpty()) throw ApiProblem.missing();
    }
    public World world() {
        return db.queryForObject("select * from environment where id=1",(rs,n)->new World(rs.getInt("revision"),rs.getBoolean("checkout_running"),rs.getBoolean("database_running"),rs.getBoolean("link_allowed"),rs.getBoolean("bad_deploy"),rs.getBoolean("checkout_b_running"),rs.getInt("demand_rps"),rs.getBoolean("cache_running"),rs.getInt("cache_hit_percent")));
    }
    private void saveWorld(World w) {
        db.update("update environment set revision=?,checkout_running=?,database_running=?,link_allowed=?,bad_deploy=?,checkout_b_running=?,demand_rps=?,cache_running=?,cache_hit_percent=? where id=1",
            w.revision(),w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),w.badDeploy(),w.checkoutBRunning(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
    }
    private void expected(World w,int revision) {
        if(w.revision()!=revision) throw ApiProblem.conflict("The environment changed. Refresh and investigate its current state before applying this action.");
    }
    private void activity(String incident,String kind,String message,int revision) {
        String runId=incident==null?null:incident(incident).lastRunId();
        Measurement measurement=Set.of("OPENED","REPAIR","VERIFICATION").contains(kind)?Simulation.measurement(world()):null;
        db.update("insert into activity(incident_id,created_at,kind,message,revision,run_id,measurement_json,actor) values (?,?,?,?,?,?,?,?)",incident,now(),kind,message,revision,runId,measurement==null?null:encode(measurement),OperatorSecurity.actor());
    }
    @Transactional
    public World control(Control c) {
        if(c.enabled()==null || c.expectedRevision()==null) throw ApiProblem.bad("Control value and expectedRevision are required.");
        lockWorld(); World w=world(); expected(w,c.expectedRevision());
        World next=switch(c.control()==null?"":c.control()) {
            case "checkout" -> new World(w.revision()+1,c.enabled(),w.databaseRunning(),w.linkAllowed(),w.badDeploy(),w.checkoutBRunning(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
            case "checkoutB" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),w.badDeploy(),c.enabled(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
            case "database" -> new World(w.revision()+1,w.checkoutRunning(),c.enabled(),w.linkAllowed(),w.badDeploy(),w.checkoutBRunning(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
            case "link" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),c.enabled(),w.badDeploy(),w.checkoutBRunning(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
            case "deployment" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),c.enabled(),w.checkoutBRunning(),w.demandRps(),w.cacheRunning(),w.cacheHitPercent());
            default -> throw ApiProblem.bad("Unknown environment control.");
        };
        saveWorld(next); activity(null,"CONTROL",c.control()+" changed to "+(c.enabled()?"enabled":"disabled"),next.revision()); return next;
    }
    @Transactional
    public World preset(Preset p) {
        if(p.expectedRevision()==null) throw ApiProblem.bad("expectedRevision is required.");
        lockWorld(); World w=world(); expected(w,p.expectedRevision());
        World next=switch(p.name()==null?"":p.name()) {
            case "healthy" -> new World(w.revision()+1,true,true,true,false,true,60,true,90);
            case "connection" -> new World(w.revision()+1,true,true,false,false,true,60,true,90);
            case "deployment" -> new World(w.revision()+1,true,true,true,true,true,60,true,90);
            case "compound" -> new World(w.revision()+1,true,true,false,true,true,60,true,90);
            case "redundancy" -> new World(w.revision()+1,true,true,true,false,false,60,true,90);
            case "cache" -> new World(w.revision()+1,true,true,true,false,true,150,false,90);
            case "cache-compound" -> new World(w.revision()+1,true,true,true,false,true,150,false,20);
            case "cache-degraded" -> new World(w.revision()+1,true,true,true,false,true,150,true,20);
            case "overload" -> new World(w.revision()+1,true,true,true,false,false,150,true,90);
            default -> throw ApiProblem.bad("Unknown preset.");
        };
        saveWorld(next); activity(null,"PRESET","Applied environment preset: "+p.name(),next.revision()); return next;
    }
    @Transactional
    public World traffic(Traffic t) {
        if(t.demandRps()==null || t.demandRps()<1 || t.demandRps()>400 || t.expectedRevision()==null)
            throw ApiProblem.bad("Traffic must be between 1 and 400 requests/s and expectedRevision is required.");
        lockWorld(); World w=world(); expected(w,t.expectedRevision());
        World next=new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),w.badDeploy(),w.checkoutBRunning(),t.demandRps(),w.cacheRunning(),w.cacheHitPercent());
        saveWorld(next);activity(null,"TRAFFIC","Incoming checkout demand set to "+t.demandRps()+" requests/s.",next.revision());return next;
    }
    @Transactional
    public World cache(CacheSettings c) {
        if(c.running()==null || c.hitPercent()==null || c.hitPercent()<0 || c.hitPercent()>100 || c.expectedRevision()==null)
            throw ApiProblem.bad("Cache running, hitPercent from 0 to 100, and expectedRevision are required.");
        lockWorld(); World w=world(); expected(w,c.expectedRevision());
        World next=new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),w.badDeploy(),w.checkoutBRunning(),w.demandRps(),c.running(),c.hitPercent());
        saveWorld(next);activity(null,"CACHE","Cache "+(c.running()?"online":"offline")+"; configured hit rate "+c.hitPercent()+"%.",next.revision());return next;
    }
    public List<Incident> incidents() {
        return db.query("select * from incident order by created_at desc limit 100",(rs,n)->new Incident(rs.getString("id"),rs.getString("title"),rs.getString("status"),rs.getString("created_at"),rs.getString("last_run_id")));
    }
    public Incident incident(String id) {
        var rows=db.query("select * from incident where id=?",(rs,n)->new Incident(rs.getString("id"),rs.getString("title"),rs.getString("status"),rs.getString("created_at"),rs.getString("last_run_id")),id);
        if(rows.isEmpty()) throw ApiProblem.missing(); return rows.getFirst();
    }
    @Transactional
    public Incident create(NewIncident input) {
        lockWorld(); World w=world(); var check=Simulation.checkout(w);
        String title=input.title()==null||input.title().isBlank()?Simulation.symptom(w):input.title().trim();
        if(title.length()>300) throw ApiProblem.bad("Use a title of at most 300 characters.");
        String id=id(); db.update("insert into incident values (?,?, 'OPEN',?,null)",id,title,now());
        activity(id,"OPENED","Incident opened. "+Simulation.symptom(w),w.revision()); return incident(id);
    }
    public List<Receipt> receipts(String runId) {
        return db.query("select receipt_json from evidence where run_id=? order by created_at,id",(rs,n)->decode(rs.getString(1),Receipt.class),runId);
    }
    public Run run(String id) {
        var rows=db.query("select investigation.*,coalesce(operation.mode,'RECOMMEND') as execution_mode from investigation left join operation on operation.id=investigation.operation_id where investigation.id=?",(rs,n)->new Run(rs.getString("id"),rs.getString("incident_id"),rs.getString("status"),rs.getString("created_at"),decode(rs.getString("snapshot_json"),World.class),decode(rs.getString("report_json"),Report.class),rs.getString("session_id"),List.of(decode(rs.getString("events_json"),ExecutionEvent[].class)),rs.getString("error_message"),receipts(id),rs.getString("execution_mode"),decode(rs.getString("correction_json"),Correction.class),Simulation.measurement(decode(rs.getString("snapshot_json"),World.class))),id);
        if(rows.isEmpty()) throw ApiProblem.missing(); return rows.getFirst();
    }
    public List<Activity> activities(String incidentId) {
        String clause=incidentId==null?"":" where incident_id=?";
        Object[] args=incidentId==null?new Object[]{}:new Object[]{incidentId};
        return db.query("select * from activity"+clause+" order by id desc limit 100",(rs,n)->new Activity(rs.getLong("id"),rs.getString("incident_id"),rs.getString("created_at"),rs.getString("kind"),rs.getString("message"),rs.getInt("revision"),rs.getString("run_id"),decode(rs.getString("measurement_json"),Measurement.class),rs.getString("actor")),args);
    }
    public Detail detail(String id) {
        Incident i=incident(id);
        var runs=db.query("select id from investigation where incident_id=? order by created_at desc",(rs,n)->run(rs.getString(1)),id);
        return new Detail(i,runs,activities(id),operations(id));
    }
    public State state() { World w=world(); return new State(w,Simulation.checkout(w),Simulation.capacity(w),Simulation.dataLoad(w),incidents(),activities(null)); }
    @Transactional
    public Run begin(String incidentId) {
        lockWorld(); lockIncident(incidentId); idle(incidentId);
        return beginInternal(incidentId);
    }
    private Run beginInternal(String incidentId) {
        Incident i=incident(incidentId);
        if("RESOLVED".equals(i.status())) throw ApiProblem.conflict("This incident is resolved. Open a new incident for a new failure.");
        if("INVESTIGATING".equals(i.status())) throw ApiProblem.conflict("An investigation is already running for this incident.");
        World w=world(); String runId=id();
        db.update("insert into investigation(id,incident_id,status,created_at,snapshot_json) values (?,?,'QUEUED',?,?)",runId,incidentId,now(),encode(w));
        db.update("update incident set status='INVESTIGATING',last_run_id=? where id=?",runId,incidentId);
        activity(incidentId,"INVESTIGATION","Investigation queued against environment revision "+w.revision(),w.revision());
        return run(runId);
    }
    public boolean markRunning(String id) { return db.update("update investigation set status='RUNNING' where id=? and status='QUEUED'",id)==1; }
    @Transactional
    public Receipt probe(String runId,String probe) {
        // The row lock fences application evidence writes against completion and failure.
        var status=db.query("select status from investigation where id=? for update",(rs,n)->rs.getString(1),runId);
        if(status.isEmpty()) throw ApiProblem.missing();
        if(!"RUNNING".equals(status.getFirst())) throw ApiProblem.conflict("This investigation no longer accepts probe results.");
        World w=run(runId).snapshot(); var observed=Simulation.probe(w,probe);
        // Short, database-unique handles survive nested model copying more reliably than UUIDs.
        // Authorization still checks membership in this run and the exact action on the receipt.
        String receiptId="evidence-"+db.queryForObject("select next value for receipt_sequence",Long.class);
        var receipt=new Receipt(receiptId,runId,w.revision(),now(),probe,observed.text(),observed.actions());
        db.update("insert into evidence values (?,?,?,?)",receipt.id(),runId,receipt.observedAt(),encode(receipt)); return receipt;
    }
    @Transactional
    public void complete(String runId, Report report, String sessionId, List<ExecutionEvent> events) {
        Run r=run(runId); lockIncident(r.incidentId());
        String status=db.queryForObject("select status from investigation where id=? for update",String.class,runId);
        if(!"RUNNING".equals(status)) return;
        validate(report,receipts(runId));
        if(r.mode().equals("OBSERVE")) report=new Report(report.summary(),report.likelyCause(),report.confidence(),report.evidenceIds(),List.of(),"Observation only. Start a Recommend or Auto-repair investigation to authorize repairs.");
        db.update("update investigation set status='COMPLETE',report_json=?,session_id=?,events_json=? where id=?",encode(report),sessionId,encode(events),runId);
        db.update("update incident set status='DIAGNOSED' where id=? and last_run_id=?",r.incidentId(),runId);
        activity(r.incidentId(),"DIAGNOSIS","Investigation completed with "+report.recommendations().size()+" proposed repair(s).",r.snapshot().revision());
    }
    static void validate(Report report, List<Receipt> receipts) {
        if(report==null || blank(report.summary()) || blank(report.likelyCause()) || blank(report.nextStep()) || !Set.of("low","medium","high").contains(report.confidence()==null?"":report.confidence())
            || report.evidenceIds()==null || report.evidenceIds().isEmpty() || report.recommendations()==null || report.recommendations().size()>4) throw ApiProblem.bad("The investigation returned an incomplete report.");
        Map<String,Receipt> byId=new HashMap<>(); receipts.forEach(r->byId.put(r.id(),r));
        if(report.evidenceIds().stream().anyMatch(id->!byId.containsKey(id))) throw ApiProblem.bad("The report cites evidence outside this investigation.");
        if(report.recommendations().isEmpty() && receipts.stream().anyMatch(r->!r.actions().isEmpty()))
            throw ApiProblem.bad("Recorded probes offer supported repairs. Include at least one useful supported repair with its exact receipt reference.");
        Set<String> actions=new HashSet<>();
        for(var recommendation:report.recommendations()) {
            if(recommendation==null || blank(recommendation.reason())) throw ApiProblem.bad("The repair needs a reason.");
            Receipt receipt=byId.get(recommendation.evidenceId());
            if(receipt==null || !report.evidenceIds().contains(receipt.id()) || receipt.actions().stream().noneMatch(a->a.id().equals(recommendation.actionId())) || !actions.add(recommendation.actionId()))
                throw ApiProblem.bad("A proposed repair is not supported by its cited probe result.");
        }
    }
    private static boolean blank(String s) { return s==null || s.isBlank(); }
    @Transactional
    public void fail(String runId,String message,String sessionId,List<ExecutionEvent> events) {
        Run r=run(runId); lockIncident(r.incidentId());
        if(db.update("update investigation set status='FAILED',error_message=?,session_id=?,events_json=? where id=? and status in ('QUEUED','RUNNING')",message,sessionId,encode(events),runId)==0) return;
        db.update("update incident set status='OPEN' where id=? and last_run_id=?",r.incidentId(),runId);
        activity(r.incidentId(),"FAILED",message,r.snapshot().revision());
    }
    @Transactional
    public World repair(String incidentId, RepairRequest request) {
        lockWorld(); lockIncident(incidentId); idle(incidentId);
        return repairInternal(incidentId,request);
    }
    private World repairInternal(String incidentId, RepairRequest request) {
        OperatorSecurity.requireRepair(request.actionId());
        Incident i=incident(incidentId); Run r=run(request.runId());
        if("OBSERVE".equals(r.mode())) throw ApiProblem.conflict("Observe mode cannot authorize repairs. Start a Recommend or Auto-repair investigation.");
        if(!r.incidentId().equals(incidentId)) throw ApiProblem.bad("The investigation belongs to another incident.");
        // Retrying an already committed request cannot mutate the world twice.
        if(db.queryForObject("select count(*) from repair where run_id=? and action_id=?",Integer.class,r.id(),request.actionId())>0) return world();
        if(!"COMPLETE".equals(r.status()) || !r.id().equals(i.lastRunId()) || !"DIAGNOSED".equals(i.status())) throw ApiProblem.conflict("Use the latest completed investigation before applying a repair.");
        World w=world(); expected(w,r.snapshot().revision());
        var rec=r.report().recommendations().stream().filter(a->a.actionId().equals(request.actionId())).findFirst().orElseThrow(()->ApiProblem.bad("This action was not recommended by the investigation."));
        var receipt=r.evidence().stream().filter(e->e.id().equals(rec.evidenceId())).findFirst().orElseThrow(ApiProblem::missing);
        var action=receipt.actions().stream().filter(a->a.id().equals(rec.actionId())).findFirst().orElseThrow(ApiProblem::missing);
        World next=Simulation.repair(w,action.id()); saveWorld(next);
        db.update("insert into repair values (?,?,?)",r.id(),action.id(),next.revision());
        db.update("update incident set status='MITIGATED' where id=?",incidentId);
        activity(incidentId,"REPAIR",action.label()+" applied. Recovery verification is still required.",next.revision()); return next;
    }
    @Transactional
    public RecoveryResult verify(String incidentId) {
        lockWorld(); lockIncident(incidentId); idle(incidentId);
        return verifyInternal(incidentId);
    }
    private RecoveryResult verifyInternal(String incidentId) {
        Incident i=incident(incidentId);
        if("INVESTIGATING".equals(i.status())) throw ApiProblem.conflict("Wait for the investigation before verifying recovery.");
        World w=world(); var result=Simulation.recovery(w);
        db.update("update incident set status=? where id=?",result.success()?"RESOLVED":"OPEN",incidentId);
        activity(incidentId,"VERIFICATION",(result.success()?"Recovery verified. ":"Recovery failed; further investigation needed. ")+result.message(),w.revision()); return result;
    }
    @Transactional
    public void recoverInterrupted() {
        for(String operationId:db.query("select id from operation where status='ACTIVE'",(rs,n)->rs.getString(1)))
            stopOperation(operationId,"STOPPED","Application restarted; automatic work will not resume without a new operator request.");
        var ids=db.query("select id from investigation where status in ('RUNNING','QUEUED')",(rs,n)->rs.getString(1));
        for(String id:ids) fail(id,"The application stopped during this investigation. Retry to capture a fresh snapshot.",null,List.of());
    }

    public static final Set<String> REPAIR_ACTIONS=Set.of("START_CHECKOUT_A","START_CHECKOUT_B","START_DATABASE","RESTORE_DB_LINK","ROLLBACK_CHECKOUT","START_CACHE","RESTORE_CACHE_HIT_RATE");
    private void idle(String incidentId) {
        if(db.queryForObject("select count(*) from operation where incident_id=? and status='ACTIVE'",Integer.class,incidentId)>0)
            throw ApiProblem.conflict("An operation is active. Stop it before starting manual work.");
    }
    public List<Operation> operations(String incidentId) {
        return db.query("select * from operation where incident_id=? order by created_at desc",(rs,n)->new Operation(rs.getString("id"),incidentId,rs.getString("created_at"),rs.getString("mode"),List.of(decode(rs.getString("allowed_actions_json"),String[].class)),rs.getInt("max_repairs"),rs.getInt("repairs"),rs.getString("status"),rs.getString("current_run_id"),rs.getInt("expected_revision"),rs.getString("message")),incidentId);
    }
    public Operation operation(String id) {
        String incidentId=db.query("select incident_id from operation where id=?",(rs,n)->rs.getString(1),id).stream().findFirst().orElseThrow(ApiProblem::missing);
        return operations(incidentId).stream().filter(o->o.id().equals(id)).findFirst().orElseThrow(ApiProblem::missing);
    }
    @Transactional
    public Operation startOperation(String incidentId,InvestigationOptions input) {
        String mode=input==null||input.mode()==null?"RECOMMEND":input.mode();
        if(mode==null || !Set.of("OBSERVE","RECOMMEND","AUTO").contains(mode)) throw ApiProblem.bad("Choose OBSERVE, RECOMMEND or AUTO mode.");
        List<String> allowed=input==null||input.allowedActions()==null?List.of():input.allowedActions();
        int limit=input==null||input.maxRepairs()==null?2:input.maxRepairs();
        if(limit<1||limit>3||allowed.stream().anyMatch(a->a==null||!REPAIR_ACTIONS.contains(a))||new HashSet<>(allowed).size()!=allowed.size()) throw ApiProblem.bad("Choose unique supported repairs and a limit from 1 to 3.");
        if(!mode.equals("AUTO")) allowed=List.of();
        lockWorld();lockIncident(incidentId);idle(incidentId);
        Run r=beginInternal(incidentId);String id=id();
        db.update("insert into operation(id,incident_id,created_at,mode,allowed_actions_json,max_repairs,status,current_run_id,expected_revision,message) values (?,?,?,?,?,?,'ACTIVE',?,?,?)",id,incidentId,now(),mode,encode(allowed),limit,r.id(),r.snapshot().revision(),"Investigating the saved environment snapshot.");
        db.update("update investigation set operation_id=? where id=?",id,r.id());
        activity(incidentId,"POLICY","Started "+mode+" operation; permitted repairs: "+allowed+"; repair limit "+limit+".",r.snapshot().revision());return operation(id);
    }
    @Transactional
    public void correction(String runId,Correction correction) {
        Run r=run(runId);lockIncident(r.incidentId());
        if(db.update("update investigation set correction_json=? where id=? and status='RUNNING'",encode(correction),runId)==1)
            activity(r.incidentId(),"REPORT_CORRECTION","Report correction "+correction.status().toLowerCase()+": "+correction.reason(),r.snapshot().revision());
    }
    private void finishOperation(Operation o,String status,String message) {
        db.update("update operation set status=?,message=? where id=? and status='ACTIVE'",status,message,o.id());
        activity(o.incidentId(),"OPERATION",message,world().revision());
    }
    @Transactional
    public void stopOperation(String id,String status,String message) {
        Operation o=operation(id);lockWorld();lockIncident(o.incidentId());o=operation(id);
        if(!o.status().equals("ACTIVE")) return;
        fail(o.currentRunId(),message,null,List.of());
        finishOperation(o,status,message);
    }
    @Transactional
    public void stop(String incidentId,String operationId) {
        if(!operation(operationId).incidentId().equals(incidentId)) throw ApiProblem.bad("Operation belongs to another incident.");
        stopOperation(operationId,"STOPPED","Stopped by operator. No further automatic repairs will run.");
    }
    /** One atomic decision/repair/verification/next-snapshot transition, fenced by the world lock. */
    @Transactional
    public String advanceOperation(String operationId) {
        Operation o=operation(operationId);lockWorld();lockIncident(o.incidentId());o=operation(operationId);
        if(!o.status().equals("ACTIVE")) return null;
        Run r=run(o.currentRunId());
        if(r.status().equals("FAILED")) {finishOperation(o,"FAILED","Investigation failed; no automatic repair was authorized.");return null;}
        if(!r.status().equals("COMPLETE")) return null;
        if(!o.mode().equals("AUTO")) {finishOperation(o,"COMPLETED",o.mode().equals("OBSERVE")?"Observation complete. No repairs authorized.":"Recommendations ready for operator review.");return null;}
        if(world().revision()!=o.expectedRevision()) {finishOperation(o,"STALE","Environment changed outside this operation. Start a fresh operation to reassess.");return null;}
        if(Simulation.recovery(world()).success()) {verifyInternal(o.incidentId());finishOperation(o,"RESOLVED","Recovery verified; no repair was needed.");return null;}
        if(o.repairs()>=o.maxRepairs()) {finishOperation(o,"LIMIT_REACHED","Repair limit reached. Operator review is required.");return null;}
        // Policy order is explicit and deterministic; recommendations never expand permissions.
        String action=null;
        for(String permitted:o.allowedActions()) if(r.report().recommendations().stream().anyMatch(rec->rec.actionId().equals(permitted))) {action=permitted;break;}
        if(action==null) {finishOperation(o,r.report().recommendations().isEmpty()?"NO_REPAIR":"POLICY_BLOCKED","No recommended repair is permitted by this operation. Operator review is required.");return null;}
        if(!OperatorSecurity.canRepair(action)) {finishOperation(o,"ROLE_BLOCKED","The initiating operator cannot authorize this repair. An incident commander must review the evidence and apply the repair or start a new operation.");return null;}
        repairInternal(o.incidentId(),new RepairRequest(r.id(),action));
        db.update("update operation set repairs=repairs+1,expected_revision=? where id=?",world().revision(),o.id());
        if(verifyInternal(o.incidentId()).success()) {finishOperation(o,"RESOLVED","Automatic repair completed and recovery verified.");return null;}
        if(o.repairs()+1>=o.maxRepairs()) {finishOperation(o,"LIMIT_REACHED","Recovery still fails and the repair limit was reached. Operator review is required.");return null;}
        Run next=beginInternal(o.incidentId());
        db.update("update investigation set operation_id=? where id=?",o.id(),next.id());
        db.update("update operation set current_run_id=?,message=? where id=?",next.id(),"Verification failed; investigating a fresh snapshot.",o.id());
        activity(o.incidentId(),"ADAPTATION","Recovery still fails. Reinvestigating revision "+next.snapshot().revision()+" before another repair.",next.snapshot().revision());return next.id();
    }
}
