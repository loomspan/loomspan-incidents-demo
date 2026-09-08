package demo.relay;

import java.util.List;
import static demo.relay.Contracts.*;

/** The sole definition of simulated infrastructure behavior. No model calls or scenario lookup. */
public final class Simulation {
    private Simulation() {}
    public static TransactionResult checkout(World w) {
        if (!w.checkoutRunning()) return new TransactionResult(false, "Gateway returned 503: no ready checkout instance.", 20, w.revision());
        if (w.badDeploy()) return new TransactionResult(false, "Checkout returned 500: order validation exception.", 45, w.revision());
        if (!w.linkAllowed() || !w.databaseRunning()) return new TransactionResult(false, "Checkout returned 504: database connection timed out.", 3000, w.revision());
        return new TransactionResult(true, "Order accepted. Payment and inventory simulation passed.", 120, w.revision());
    }
    public record Observation(String text, List<Action> actions) {}
    private static Observation observation(String text, Action... actions) { return new Observation(text, List.of(actions)); }
    public static Observation probe(World w, String probe) {
        return switch (probe) {
            case "inspectApplication" -> {
                if (!w.checkoutRunning()) yield observation("Gateway is healthy. Checkout liveness DOWN; no ready instance. Requests return 503.",
                    new Action("START_CHECKOUT", "Start checkout service", "Starts the checkout instance; preserves its deployment and network configuration."));
                if (w.badDeploy()) yield observation("Checkout liveness UP. Version checkout-2.0 was deployed recently. Requests return 500; logs show OrderValidator exception since deployment. Previous version checkout-1.9 passed its release checks.",
                    new Action("ROLLBACK_CHECKOUT", "Roll back checkout deployment", "Restores checkout-1.9; preserves service power and network configuration."));
                if (!w.linkAllowed() || !w.databaseRunning()) yield observation("Checkout liveness UP. Version checkout-1.9, no recent deployment. Requests return 504; logs show database connection timeouts. Check the network path and database health.");
                yield observation("Checkout liveness UP. Version checkout-1.9. No recent deployment. Requests succeed and logs contain no current errors.");
            }
            case "inspectNetwork" -> w.linkAllowed()
                ? observation("DNS resolves checkout and database correctly. Firewall permits checkout → database:5432. Gateway → checkout path is permitted. " + (w.databaseRunning() ? "Database TCP handshake succeeds from the network probe." : "Database endpoint does not answer despite an allowed route; inspect database health."))
                : observation("DNS resolves correctly. Firewall rule DB-17 denies checkout → database:5432. Deny counters increase with checkout requests. Gateway → checkout path remains permitted.",
                    new Action("RESTORE_DB_LINK", "Restore database connection", "Allows checkout → database:5432 through rule DB-17; preserves database and application state."));
            case "inspectDatabase" -> w.databaseRunning()
                ? observation("Database process UP. Local readiness and SELECT 1 pass. Connections are below capacity. This local check does not establish reachability from checkout.")
                : observation("Database process DOWN. Local readiness and SELECT 1 fail; no listener on port 5432.",
                    new Action("START_DATABASE", "Start database service", "Starts the database process; preserves firewall and application configuration."));
            default -> throw new IllegalArgumentException("Unknown probe");
        };
    }
    public static World repair(World w, String action) {
        return switch (action) {
            case "START_CHECKOUT" -> new World(w.revision()+1,true,w.databaseRunning(),w.linkAllowed(),w.badDeploy());
            case "START_DATABASE" -> new World(w.revision()+1,w.checkoutRunning(),true,w.linkAllowed(),w.badDeploy());
            case "RESTORE_DB_LINK" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),true,w.badDeploy());
            case "ROLLBACK_CHECKOUT" -> new World(w.revision()+1,w.checkoutRunning(),w.databaseRunning(),w.linkAllowed(),false);
            default -> throw new IllegalArgumentException("Unknown repair");
        };
    }
}
