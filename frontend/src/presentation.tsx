import type {
  Activity,
  Detail,
  Measurement,
  Operation,
  Run,
  State,
} from "./types";

export const scenarios = [
  {
    id: "cache-compound",
    title: "Two-stage cache recovery",
    tag: "START HERE",
    symptom:
      "Checkout fails even though both application instances and the database are running.",
    lesson:
      "Watch a repair reveal the work still needed. The first verification fails; fresh evidence leads to a second repair.",
    mode: "AUTO",
    steps: [
      "Prepare the incident, then start the investigation with the two cache permissions and a two-repair limit.",
      "Watch the cache start. Its low hit rate still overloads the database, so verification should fail.",
      "Follow the fresh investigation and hit-rate repair. Recovery should pass with traffic unchanged.",
    ],
    watch:
      "Application logs, pool capacity and database load describe different parts of the same failure.",
  },
  {
    id: "compound",
    title: "One fault hides another",
    tag: "REASSESSMENT",
    symptom: "Checkout returns a validation error after a deployment.",
    lesson:
      "A correct first diagnosis can be incomplete. Removing one fault exposes a different customer symptom.",
    mode: "RECOMMEND",
    steps: [
      "Investigate and review the rollback proposal.",
      "Apply the rollback and verify. A database connection timeout should remain.",
      "Reinvestigate, restore the database connection, and verify again.",
    ],
    watch:
      "The commander should change which specialists it uses when the symptom changes.",
  },
  {
    id: "redundancy",
    title: "Healthy checkout, hidden risk",
    tag: "AVAILABILITY",
    symptom:
      "Customers can check out, but only one of two application instances is online.",
    lesson:
      "A passing customer check does not prove that the availability target is met.",
    mode: "RECOMMEND",
    steps: [
      "Investigate the availability alert and compare application and capacity evidence.",
      "Try verification before repairing: the missing instance should prevent resolution.",
      "Start checkout B from the supported proposal and verify recovery.",
    ],
    watch: "Keep successful traffic separate from the loss of redundancy.",
  },
  {
    id: "connection",
    title: "Healthy services, broken path",
    tag: "CORRELATION",
    symptom:
      "Checkout times out while application and database processes remain up.",
    lesson: "Local health checks cannot establish end-to-end connectivity.",
    mode: "RECOMMEND",
    steps: [
      "Compare the application, network and database findings.",
      "Apply the supported database-connection repair.",
      "Verify checkout and instance availability before resolving.",
    ],
    watch:
      "The network evidence explains why two healthy services cannot communicate.",
  },
];
export function ScenarioGuide({
  selected,
  onSelect,
  onPrepare,
  disabled,
  canPrepare,
}: {
  selected: string;
  onSelect: (id: string) => void;
  onPrepare: () => void;
  disabled: boolean;
  canPrepare: boolean;
}) {
  const scenario = scenarios.find((s) => s.id === selected) ?? scenarios[0];
  return (
    <section className="scenario-guide" aria-labelledby="scenario-heading">
      <div className="guide-heading">
        <div>
          <span className="eyebrow">GUIDED DEMOS</span>
          <h2 id="scenario-heading">See the system adapt.</h2>
          <p>
            Choose a story, introduce its faults, and follow the recorded
            evidence.
          </p>
        </div>
        <label>
          Walkthrough
          <select
            value={scenario.id}
            onChange={(e) => onSelect(e.target.value)}
            disabled={disabled}
          >
            {scenarios.map((s) => (
              <option key={s.id} value={s.id}>
                {s.title}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="guide-body">
        <div>
          <span className="guide-tag">{scenario.tag}</span>
          <h3>{scenario.title}</h3>
          <p>{scenario.symptom}</p>
          <p className="guide-lesson">{scenario.lesson}</p>
        </div>
        <div>
          <strong>What to watch for</strong>
          <p>{scenario.watch}</p>
          <details>
            <summary>Walkthrough steps · expected behavior</summary>
            <ol>
              {scenario.steps.map((step) => (
                <li key={step}>{step}</li>
              ))}
            </ol>
          </details>
        </div>
      </div>
      <div className="guide-footer">
        <button
          className="primary"
          disabled={disabled || !canPrepare}
          onClick={onPrepare}
        >
          Prepare walkthrough →
        </button>
        <span>
          Presenter sets the shared environment and opens an incident. Responder
          starts the investigation.
        </span>
      </div>
    </section>
  );
}
const healthy = (m: Measurement) =>
  m.checkout.success && m.capacity.redundancyRestored;
const at = (s: string) =>
  new Date(s).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
export function RecoveryComparison({
  detail,
  state,
}: {
  detail: Detail;
  state: State;
}) {
  const opening = [...detail.activity]
    .reverse()
    .find((a) => a.kind === "OPENED" && a.measurement);
  const first = detail.runs.at(-1);
  const before = opening?.measurement ?? first?.measurement;
  const verification = detail.activity.find((a) => a.kind === "VERIFICATION");
  const after = verification
    ? verification.measurement
    : {
        checkout: state.checkout,
        capacity: state.capacity,
        dataLoad: state.dataLoad,
      };
  const rows: [string, (m: Measurement) => string][] = [
    [
      "Successful checkout",
      (m) => `${m.capacity.successfulRps} / ${m.capacity.demandRps} req/s`,
    ],
    ["Failed checkout", (m) => `${m.capacity.failedRps} req/s`],
    [
      "Database demand",
      (m) =>
        m.dataLoad.modeled
          ? `${m.dataLoad.databaseDemandOps} / ${m.dataLoad.databaseCapacityOps} ops/s`
          : "Not modeled",
    ],
    [
      "Online instances",
      (m) => `${m.capacity.onlineInstances} / ${m.capacity.targetInstances}`,
    ],
    ["Checkout latency", (m) => `${m.checkout.latencyMs.toLocaleString()} ms`],
  ];
  return (
    <section
      className="recovery-comparison"
      aria-labelledby="comparison-heading"
    >
      <div className="section-head">
        <div>
          <span className="eyebrow">THE CUSTOMER IMPACT</span>
          <h2 id="comparison-heading">What changed?</h2>
        </div>
        <span
          className={
            "outcome-badge " +
            (after && healthy(after) && verification ? "passed" : "")
          }
        >
          {verification
            ? after
              ? healthy(after)
                ? "Recovery check passed"
                : "Recovery check failed"
              : "Historical check"
            : "Awaiting verification"}
        </span>
      </div>
      <div className="comparison-scroll">
        <table>
          <thead>
            <tr>
              <th scope="col">Measurement</th>
              <th scope="col">
                {opening ? "At incident opening" : "First investigation"}
                <small>
                  {before
                    ? `Saved · rev ${before.checkout.revision}`
                    : "No snapshot yet"}
                </small>
              </th>
              <th scope="col">
                {verification ? "Latest verification" : "Live environment"}
                <small>
                  {verification
                    ? `Recorded ${at(verification.createdAt)} · rev ${verification.revision}`
                    : `Current · rev ${state.environment.revision}`}
                </small>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map(([label, value]) => (
              <tr key={label}>
                <th scope="row">{label}</th>
                <td>{before ? value(before) : "—"}</td>
                <td>{after ? value(after) : "Not recorded"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="comparison-note">
        {verification
          ? "Verification measurements stay fixed when the presenter changes the environment."
          : "The live column can change. It becomes a saved comparison after verification."}
        {verification && !after
          ? " This older check did not save detailed measurements."
          : ""}
        {verification && state.environment.revision !== verification.revision
          ? ` Environment now: rev ${state.environment.revision}, ${state.capacity.successfulRps}/${state.capacity.demandRps} requests/s succeed.`
          : ""}
      </p>
    </section>
  );
}
const stopCopy: Record<string, [string, string]> = {
  ROLE_BLOCKED: [
    "Incident commander authorization required",
    "The initiating operator cannot execute this repair. Sign in as Incident commander, review the evidence, then apply the proposed repair or start a fresh operation.",
  ],
  POLICY_BLOCKED: [
    "Repair permission needed",
    "Review the supported proposal. Apply it manually, or start a new operation with the permission you intend to grant.",
  ],
  LIMIT_REACHED: [
    "Repair limit reached",
    "Review the failed verification, then start a fresh Recommend or Auto-repair investigation before authorizing another repair.",
  ],
  STALE: [
    "Environment changed",
    "The saved evidence describes an older revision. Start a new investigation before authorizing another repair.",
  ],
  NO_REPAIR: [
    "No supported repair available",
    "Review the evidence and capacity limits. Further action needs operator assessment; the app will not invent a repair.",
  ],
  FAILED: [
    "Investigation stopped",
    "Inspect the evidence and any report-correction result. Start a fresh operation after addressing the failure.",
  ],
  STOPPED: [
    "Automatic work stopped",
    "No more repairs will run under this operation. Start a new operation when you are ready to continue.",
  ],
};
export function OperationExplanation({ operation }: { operation: Operation }) {
  const copy = stopCopy[operation.status];
  return copy ? (
    <div className={"stop-explanation stop-" + operation.status.toLowerCase()}>
      <strong>{copy[0]}</strong>
      <p>{copy[1]}</p>
    </div>
  ) : null;
}
function linkedRun(a: Activity, runs: Run[]) {
  if (a.runId) return runs.find((r) => r.id === a.runId);
  // Older records have no explicit link. Only use an unambiguous investigation revision.
  if (!["INVESTIGATION", "DIAGNOSIS", "FAILED"].includes(a.kind))
    return undefined;
  const matches = runs.filter((r) => r.snapshot.revision === a.revision);
  return matches.length === 1 ? matches[0] : undefined;
}
const eventTitles: Record<string, string> = {
  OPENED: "Incident opened",
  POLICY: "Operating rules saved",
  INVESTIGATION: "Investigation started",
  DIAGNOSIS: "Evidence assessed",
  REPAIR: "Repair applied",
  VERIFICATION: "Recovery checked",
  ADAPTATION: "Reassessment queued",
  REPORT_CORRECTION: "Report correction",
  OPERATION: "Operation finished",
  FAILED: "Investigation failed",
};
export function RecoveryTimeline({ detail }: { detail: Detail | null }) {
  if (!detail)
    return (
      <div className="record-empty">
        <h3>A story built from evidence</h3>
        <p>
          Prepare a walkthrough or open an incident. Its investigations, repairs
          and recovery checks will appear here as they happen.
        </p>
      </div>
    );
  const items = [...detail.activity].sort((a, b) => a.id - b.id);
  return (
    <div className="recovery-story">
      <p className="timeline-note">
        Recorded steps, earliest first. Expand a step to inspect its evidence.
        Expected walkthrough steps are not marked complete automatically.
      </p>
      <ol>
        {items.map((a) => {
          const run = linkedRun(a, detail.runs);
          const measured = a.measurement;
          const passed =
            a.kind === "VERIFICATION" && measured ? healthy(measured) : null;
          return (
            <li
              key={a.id}
              className={
                passed === true
                  ? "step-passed"
                  : passed === false || a.kind === "FAILED"
                    ? "step-failed"
                    : ""
              }
            >
              <span className="story-dot" />
              <article>
                <small>
                  {at(a.createdAt)} · REV {a.revision} ·{" "}
                  {a.actor ?? "Operator not recorded"}
                </small>
                <h3>
                  {passed === true
                    ? "Recovery verified"
                    : passed === false
                      ? "Verification failed — reassess"
                      : (eventTitles[a.kind] ?? a.kind.toLowerCase())}
                </h3>
                <p>{a.message}</p>
                {measured && (
                  <div className="step-metrics">
                    <span>
                      {measured.capacity.successfulRps}/
                      {measured.capacity.demandRps} successful req/s
                    </span>
                    <span>{measured.capacity.failedRps} failed req/s</span>
                    {measured.dataLoad.modeled && (
                      <span>
                        {measured.dataLoad.databaseDemandOps} DB ops/s
                      </span>
                    )}
                  </div>
                )}
                {run && (
                  <details>
                    <summary>
                      Investigation evidence · rev {run.snapshot.revision} ·{" "}
                      {run.evidence.length} receipts
                    </summary>
                    {run.report && <p>{run.report.summary}</p>}
                    {run.error && <p>{run.error}</p>}
                    {run.correction && (
                      <p>
                        Report correction: {run.correction.status.toLowerCase()}
                        . {run.correction.reason}
                      </p>
                    )}
                    {run.evidence.length ? (
                      run.evidence.map((e) => (
                        <section className="story-receipt" key={e.id}>
                          <strong>
                            {e.probe.replace("inspect", "")} · {e.id}
                          </strong>
                          <p>{e.observation}</p>
                          {e.actions.length > 0 && (
                            <p>
                              Supported proposals:{" "}
                              {e.actions
                                .map((action) => action.label)
                                .join(", ")}
                            </p>
                          )}
                        </section>
                      ))
                    ) : (
                      <p>No receipts recorded yet.</p>
                    )}
                    <small>
                      These receipts belong to the linked investigation. They
                      may have arrived after this step was recorded.
                    </small>
                  </details>
                )}
              </article>
            </li>
          );
        })}
      </ol>
    </div>
  );
}
