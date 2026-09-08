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
        return db.queryForObject("select * from environment where id=1",(rs,n)->new World(rs.getInt("revision"),rs.getBoolean("checkout_running"),rs.getBoolean("database_running"),rs.getBoolean("link_allowed"),rs.getBoolean("bad_deploy")));
    }
    private void saveWorld(World w) {
        db.update("update environment set revision=?,checkout_running=?,database_running=?,link_allowed=?,bad_deploy=? where id=1",
            w.revision(),w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),w.badDeploy());
    }
    private void expected(World w,int revision) {
        if(w.revision()!=revision) throw ApiProblem.conflict("The environment changed. Refresh and investigate its current state before applying this action.");
    }
    private void activity(String incident,String kind,String message,int revision) {
        db.update("insert into activity(incident_id,created_at,kind,message,revision) values (?,?,?,?,?)",incident,now(),kind,message,revision);
    }
    @Transactional
    public World control(Control c) {
        if(c.enabled()==null || c.expectedRevision()==null) throw ApiProblem.bad("Control value and expectedRevision are required.");
        lockWorld(); World w=world(); expected(w,c.expectedRevision());
        World next=switch(c.control()==null?"":c.control()) {
            case "checkout" -> new World(w.revision()+1,c.enabled(),w.databaseRunning(),w.linkAllowed(),w.badDeploy());
            case "database" -> new World(w.revision()+1,w.checkoutRunning(),c.enabled(),w.linkAllowed(),w.badDeploy());
            case "link" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),c.enabled(),w.badDeploy());
            case "deployment" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),c.enabled());
            default -> throw ApiProblem.bad("Unknown environment control.");
        };
        saveWorld(next); activity(null,"CONTROL",c.control()+" changed to "+(c.enabled()?"enabled":"disabled"),next.revision()); return next;
    }
    @Transactional
    public World preset(Preset p) {
        if(p.expectedRevision()==null) throw ApiProblem.bad("expectedRevision is required.");
        lockWorld(); World w=world(); expected(w,p.expectedRevision());
        World next=switch(p.name()==null?"":p.name()) {
            case "healthy" -> new World(w.revision()+1,true,true,true,false);
            case "connection" -> new World(w.revision()+1,true,true,false,false);
            case "deployment" -> new World(w.revision()+1,true,true,true,true);
            case "compound" -> new World(w.revision()+1,true,true,false,true);
            default -> throw ApiProblem.bad("Unknown preset.");
        };
        saveWorld(next); activity(null,"PRESET","Applied environment preset: "+p.name(),next.revision()); return next;
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
        String title=input.title()==null||input.title().isBlank()?check.message():input.title().trim();
        if(title.length()>300) throw ApiProblem.bad("Use a title of at most 300 characters.");
        String id=id(); db.update("insert into incident values (?,?, 'OPEN',?,null)",id,title,now());
        activity(id,"OPENED","Incident opened. Customer check: "+check.message(),w.revision()); return incident(id);
    }
    public List<Receipt> receipts(String runId) {
        return db.query("select receipt_json from evidence where run_id=? order by created_at,id",(rs,n)->decode(rs.getString(1),Receipt.class),runId);
    }
    public Run run(String id) {
        var rows=db.query("select * from investigation where id=?",(rs,n)->new Run(rs.getString("id"),rs.getString("incident_id"),rs.getString("status"),rs.getString("created_at"),decode(rs.getString("snapshot_json"),World.class),decode(rs.getString("report_json"),Report.class),rs.getString("session_id"),List.of(decode(rs.getString("events_json"),ExecutionEvent[].class)),rs.getString("error_message"),receipts(id)),id);
        if(rows.isEmpty()) throw ApiProblem.missing(); return rows.getFirst();
    }
    public List<Activity> activities(String incidentId) {
        String clause=incidentId==null?"":" where incident_id=?";
        Object[] args=incidentId==null?new Object[]{}:new Object[]{incidentId};
        return db.query("select * from activity"+clause+" order by id desc limit 100",(rs,n)->new Activity(rs.getLong("id"),rs.getString("incident_id"),rs.getString("created_at"),rs.getString("kind"),rs.getString("message"),rs.getInt("revision")),args);
    }
    public Detail detail(String id) {
        Incident i=incident(id);
        var runs=db.query("select id from investigation where incident_id=? order by created_at desc",(rs,n)->run(rs.getString(1)),id);
        return new Detail(i,runs,activities(id));
    }
    public State state() { World w=world(); return new State(w,Simulation.checkout(w),incidents(),activities(null)); }
    @Transactional
    public Run begin(String incidentId) {
        lockWorld(); lockIncident(incidentId); Incident i=incident(incidentId);
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
        var receipt=new Receipt(id(),runId,w.revision(),now(),probe,observed.text(),observed.actions());
        db.update("insert into evidence values (?,?,?,?)",receipt.id(),runId,receipt.observedAt(),encode(receipt)); return receipt;
    }
    @Transactional
    public void complete(String runId, Report report, String sessionId, List<ExecutionEvent> events) {
        Run r=run(runId); lockIncident(r.incidentId());
        String status=db.queryForObject("select status from investigation where id=? for update",String.class,runId);
        if(!"RUNNING".equals(status)) return;
        validate(report,receipts(runId));
        db.update("update investigation set status='COMPLETE',report_json=?,session_id=?,events_json=? where id=?",encode(report),sessionId,encode(events),runId);
        db.update("update incident set status='DIAGNOSED' where id=? and last_run_id=?",r.incidentId(),runId);
        activity(r.incidentId(),"DIAGNOSIS","Investigation completed with "+report.recommendations().size()+" proposed repair(s).",r.snapshot().revision());
    }
    static void validate(Report report, List<Receipt> receipts) {
        if(report==null || blank(report.summary()) || blank(report.likelyCause()) || blank(report.nextStep()) || !Set.of("low","medium","high").contains(report.confidence()==null?"":report.confidence())
            || report.evidenceIds()==null || report.evidenceIds().isEmpty() || report.recommendations()==null || report.recommendations().size()>4) throw ApiProblem.bad("The investigation returned an incomplete report.");
        Map<String,Receipt> byId=new HashMap<>(); receipts.forEach(r->byId.put(r.id(),r));
        if(report.evidenceIds().stream().anyMatch(id->!byId.containsKey(id))) throw ApiProblem.bad("The report cites evidence outside this investigation.");
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
        lockWorld(); lockIncident(incidentId); Incident i=incident(incidentId); Run r=run(request.runId());
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
    public TransactionResult verify(String incidentId) {
        lockWorld(); lockIncident(incidentId); Incident i=incident(incidentId);
        if("INVESTIGATING".equals(i.status())) throw ApiProblem.conflict("Wait for the investigation before verifying recovery.");
        World w=world(); var result=Simulation.checkout(w);
        db.update("update incident set status=? where id=?",result.success()?"RESOLVED":"OPEN",incidentId);
        activity(incidentId,"VERIFICATION",(result.success()?"Recovery verified. ":"Recovery failed; further investigation needed. ")+result.message(),w.revision()); return result;
    }
    @Transactional
    public void recoverInterrupted() {
        var ids=db.query("select id from investigation where status in ('RUNNING','QUEUED')",(rs,n)->rs.getString(1));
        for(String id:ids) fail(id,"The application stopped during this investigation. Retry to capture a fresh snapshot.",null,List.of());
    }
}
