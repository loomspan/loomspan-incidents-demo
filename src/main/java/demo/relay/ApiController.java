package demo.relay;

import org.springframework.web.bind.annotation.*;
import static demo.relay.Contracts.*;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final IncidentStore store;
    private final InvestigationService investigations;
    public ApiController(IncidentStore store,InvestigationService investigations) { this.store=store;this.investigations=investigations; }
    @GetMapping("/state") public State state() { return store.state(); }
    @PostMapping("/environment/control") public World control(@RequestBody Control c) { return store.control(c); }
    @PostMapping("/environment/preset") public World preset(@RequestBody Preset p) { return store.preset(p); }
    @PostMapping("/environment/traffic") public World traffic(@RequestBody Traffic t) { return store.traffic(t); }
    @PostMapping("/environment/cache") public World cache(@RequestBody CacheSettings c) { return store.cache(c); }
    @PostMapping("/checkout") public TransactionResult checkout() { return Simulation.checkout(store.world()); }
    @PostMapping("/incidents") public Incident create(@RequestBody NewIncident i) { return store.create(i); }
    @GetMapping("/incidents/{id}") public Detail detail(@PathVariable String id) { return store.detail(id); }
    @PostMapping("/incidents/{id}/investigate") public Run investigate(@PathVariable String id) { return investigations.start(id); }
    @PostMapping("/incidents/{id}/repair") public World repair(@PathVariable String id,@RequestBody RepairRequest r) { return store.repair(id,r); }
    @PostMapping("/incidents/{id}/verify") public RecoveryResult verify(@PathVariable String id) { return store.verify(id); }
}
