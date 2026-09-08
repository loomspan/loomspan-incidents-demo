import React, { useEffect, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import type {
  State,
  Detail,
  Incident,
  Run,
  Receipt,
  Check,
  Recovery,
} from "./types";
import "./style.css";

async function api<T>(path: string, body?: unknown): Promise<T> {
  const response = await fetch(
    "/api" + path,
    body === undefined
      ? {}
      : {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        },
  );
  const value = await response.json();
  if (!response.ok)
    throw new Error(value.message || "The request could not be completed.");
  return value as T;
}
const time = (s: string) =>
  new Date(s).toLocaleTimeString([], {
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
const statusText = (s: string) => s.toLowerCase().replaceAll("_", " ");
const probeName = (s: string) =>
  ({
    inspectApplication: "Application probe",
    inspectNetwork: "Network probe",
    inspectDatabase: "Database & cache probe",
    inspectCapacity: "Capacity probe",
  })[s] || s;
function Pill({
  good,
  children,
}: {
  good?: boolean;
  children: React.ReactNode;
}) {
  return (
    <span
      className={
        "pill " + (good === undefined ? "neutral" : good ? "good" : "bad")
      }
    >
      <i />
      {children}
    </span>
  );
}
function App() {
  const [state, setState] = useState<State | null>(null),
    [detail, setDetail] = useState<Detail | null>(null);
  const [selected, setSelected] = useState<string | null>(null),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [title, setTitle] = useState(""),
    [tab, setTab] = useState<"evidence" | "coordination" | "history">(
      "evidence",
    );
  const selectedRef = useRef<string | null>(null);
  async function refresh() {
    const next = await api<State>("/state");
    setState(next);
    const id = selectedRef.current;
    if (id) {
      const d = await api<Detail>("/incidents/" + id);
      if (selectedRef.current === id) setDetail(d);
    }
  }
  useEffect(() => {
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        await refresh();
      } catch (e) {
        if (active) setError((e as Error).message);
      } finally {
        if (active) timer = setTimeout(poll, 2000);
      }
    }
    void poll();
    return () => {
      active = false;
      clearTimeout(timer);
    };
  }, []);
  function select(id: string) {
    selectedRef.current = id;
    setSelected(id);
    setDetail(null);
    setTab("evidence");
    void refresh().catch((e) => setError(e.message));
  }
  async function act(fn: () => Promise<unknown>) {
    setBusy(true);
    setError("");
    setNotice("");
    try {
      await fn();
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  if (!state)
    return (
      <div className="loading">
        <div className="brandmark">r</div>
        <h1>Opening Relay</h1>
        <p>{error || "Connecting to the incident workspace…"}</p>
      </div>
    );
  const w = state.environment,
    check = state.checkout,
    capacity = state.capacity,
    dataLoad = state.dataLoad,
    run = detail?.runs[0],
    incident = detail?.incident;
  const running = incident?.status === "INVESTIGATING",
    resolved = incident?.status === "RESOLVED";
  const stale = !!run && run.snapshot.revision !== w.revision;
  const activeCount = state.incidents.filter(
    (i) => i.status !== "RESOLVED",
  ).length;
  async function preset(name: string) {
    await act(() =>
      api("/environment/preset", { name, expectedRevision: w.revision }),
    );
  }
  async function control(name: string, enabled: boolean) {
    await act(() =>
      api("/environment/control", {
        control: name,
        enabled,
        expectedRevision: w.revision,
      }),
    );
  }
  async function cache(running: boolean, hitPercent: number) {
    await act(() =>
      api("/environment/cache", {
        running,
        hitPercent,
        expectedRevision: w.revision,
      }),
    );
  }
  async function openIncident() {
    await act(async () => {
      const i = await api<Incident>("/incidents", { title });
      select(i.id);
      setTitle("");
      setNotice("Incident opened. Start an investigation to collect evidence.");
    });
  }
  const toggle = (name: string, on: boolean, label: string) => (
    <button
      className={"switch " + (on ? "on" : "")}
      role="switch"
      aria-checked={on}
      aria-label={label}
      disabled={busy}
      onClick={() => void control(name, !on)}
    >
      <span />
    </button>
  );
  return (
    <div className="app">
      <aside className="sidebar">
        <a className="brand" href="/" aria-label="Relay home">
          <span className="brandmark">r</span>
          <span>
            relay<span className="brand-dot">.</span>
          </span>
        </a>
        <div className="workspace-label">OPERATIONS WORKSPACE</div>
        <div className="nav-active">
          <span>◈</span> Incident desk <b>{activeCount}</b>
        </div>
        <div className="sidebar-heading">
          INCIDENTS <span>{state.incidents.length}</span>
        </div>
        <div className="incident-list">
          {state.incidents.length === 0 ? (
            <p className="sidebar-empty">
              No incidents yet.
              <br />
              Create a fault to get started.
            </p>
          ) : (
            state.incidents.map((i, index) => (
              <button
                key={i.id}
                className={
                  "incident-link " + (selected === i.id ? "selected" : "")
                }
                onClick={() => select(i.id)}
              >
                <div>
                  <span
                    className={
                      "status-dot " +
                      (i.status === "RESOLVED" ? "green" : "orange")
                    }
                  />
                  <small>
                    INC-
                    {String(state.incidents.length - index).padStart(3, "0")}
                  </small>
                </div>
                <strong>{i.title}</strong>
                <span>{statusText(i.status)}</span>
              </button>
            ))
          )}
        </div>
        <div className="sidebar-footer">
          <span className="tiny-light" /> LOCAL SIMULATION
          <p>
            Powered by Loomspan
            <br />
            State persists between sessions.
          </p>
        </div>
      </aside>
      <main>
        <header>
          <div className="breadcrumb">
            Relay <span>/</span> Incident operations
          </div>
          <div className="header-meta">
            <span className="tiny-light" /> Demo environment{" "}
            <span className="avatar">OP</span>
          </div>
        </header>
        <div className="page">
          <div className="page-title">
            <div>
              <div className="eyebrow">
                BREAK SOMETHING. FOLLOW THE EVIDENCE.
              </div>
              <h1>Incident desk</h1>
              <p>
                A small system. Real skill coordination. A repair you can
                verify.
              </p>
            </div>
            <div className="revision">
              <span>ENVIRONMENT</span>
              <strong>rev {String(w.revision).padStart(3, "0")}</strong>
            </div>
          </div>
          {error && (
            <div role="alert" className="banner error">
              {error}
              <button aria-label="Dismiss error" onClick={() => setError("")}>
                ×
              </button>
            </div>
          )}
          {notice && (
            <div role="status" className="banner notice">
              {notice}
              <button
                aria-label="Dismiss notification"
                onClick={() => setNotice("")}
              >
                ×
              </button>
            </div>
          )}
          <section className="system-panel">
            <div className="section-head">
              <div>
                <span className="eyebrow">01 / THE ENVIRONMENT</span>
                <h2>Northstar storefront</h2>
              </div>
              <Pill good={check.success}>
                {check.success
                  ? "Customer journey healthy"
                  : "Customer journey failing"}
              </Pill>
            </div>
            <div
              className="topology"
              aria-label="Gateway to checkout to database dependency map"
            >
              <div className="node">
                <div className="node-top">
                  <span className="node-icon">↗</span>
                  <Pill good>Online</Pill>
                </div>
                <h3>Gateway</h3>
                <p>store.northstar.local</p>
                <div className="node-bottom">
                  <span>Balances online instances</span>
                  <span className="tiny-light" />
                </div>
              </div>
              <div className="connector">
                <span>HTTPS</span>
                <div>→</div>
              </div>
              <div
                className={
                  "node checkout-pool " +
                  (!capacity.redundancyRestored || w.badDeploy
                    ? "affected"
                    : "")
                }
              >
                <div className="pool-heading">
                  <h3>Checkout pool</h3>
                  <Pill good={capacity.redundancyRestored}>
                    {capacity.onlineInstances}/2 online
                  </Pill>
                </div>
                <p>
                  checkout-{w.badDeploy ? "2.0 · regression" : "1.9 · stable"}
                </p>
                <div className="instance-row">
                  <span
                    className={
                      "status-dot " + (w.checkoutRunning ? "green" : "orange")
                    }
                  />
                  <div>
                    <strong>Checkout A</strong>
                    <small>
                      {w.checkoutRunning
                        ? "100 req/s capacity"
                        : "Stopped · 0 req/s"}
                    </small>
                  </div>
                  {toggle("checkout", w.checkoutRunning, "Checkout A running")}
                </div>
                <div className="instance-row">
                  <span
                    className={
                      "status-dot " + (w.checkoutBRunning ? "green" : "orange")
                    }
                  />
                  <div>
                    <strong>Checkout B</strong>
                    <small>
                      {w.checkoutBRunning
                        ? "100 req/s capacity"
                        : "Stopped · 0 req/s"}
                    </small>
                  </div>
                  {toggle(
                    "checkoutB",
                    !!w.checkoutBRunning,
                    "Checkout B running",
                  )}
                </div>
              </div>
              <div className={"connector " + (!w.linkAllowed ? "blocked" : "")}>
                <span>{w.linkAllowed ? "TCP 5432" : "BLOCKED"}</span>
                <div>{w.linkAllowed ? "→" : "×"}</div>
              </div>
              <div className={"node " + (!w.databaseRunning ? "affected" : "")}>
                <div className="node-top">
                  <span className="node-icon">▤</span>
                  <Pill good={w.databaseRunning}>
                    {w.databaseRunning ? "Running" : "Stopped"}
                  </Pill>
                </div>
                <h3>Database</h3>
                <p>orders-db · primary</p>
                <div className="node-bottom">
                  <label>Service power</label>
                  {toggle(
                    "database",
                    w.databaseRunning,
                    "Database service running",
                  )}
                </div>
              </div>
            </div>
            <div className="capacity-panel">
              <div className="capacity-heading">
                <div>
                  <strong>Traffic & capacity</strong>
                  <p>
                    Each online instance handles 100 requests/s. Availability
                    target: two online instances.
                  </p>
                </div>
                <Pill good={capacity.status === "HEALTHY"}>
                  {
                    {
                      HEALTHY: "Fully available",
                      AT_RISK: "Redundancy lost",
                      DEGRADED: "Partially failing",
                      OUTAGE: "Customer outage",
                    }[capacity.status]
                  }
                </Pill>
              </div>
              <div className="capacity-metrics">
                <div>
                  <span>INCOMING DEMAND</span>
                  <strong>
                    {capacity.demandRps}
                    <small> req/s</small>
                  </strong>
                </div>
                <div>
                  <span>ONLINE CAPACITY</span>
                  <strong>
                    {capacity.capacityRps}
                    <small> req/s</small>
                  </strong>
                </div>
                <div>
                  <span>SUCCESSFUL</span>
                  <strong>
                    {capacity.successfulRps}
                    <small> req/s</small>
                  </strong>
                </div>
                <div className={capacity.failedRps ? "failed-metric" : ""}>
                  <span>FAILED</span>
                  <strong>
                    {capacity.failedRps}
                    <small> req/s</small>
                  </strong>
                </div>
              </div>
              <div className="traffic-control">
                <label htmlFor="traffic-level">Incoming traffic</label>
                <select
                  id="traffic-level"
                  value={w.demandRps}
                  disabled={busy}
                  onChange={(e) =>
                    void act(() =>
                      api("/environment/traffic", {
                        demandRps: Number(e.target.value),
                        expectedRevision: w.revision,
                      }),
                    )
                  }
                >
                  <option value={60}>Normal · 60 req/s</option>
                  <option value={150}>Peak · 150 req/s</option>
                  <option value={240}>Beyond pool capacity · 240 req/s</option>
                  {![60, 150, 240].includes(w.demandRps) && (
                    <option value={w.demandRps}>
                      Custom · {w.demandRps} req/s
                    </option>
                  )}
                </select>
                <span>
                  Simulated demand; changes do not repair a stopped instance.
                </span>
              </div>
            </div>
            <div className="capacity-panel cache-panel">
              <div className="capacity-heading">
                <div>
                  <strong>Cache & database load</strong>
                  <p>
                    Every checkout writes to the database. Cache misses add a
                    read.
                  </p>
                </div>
                <Pill good={!dataLoad.saturated}>
                  {dataLoad.saturated
                    ? "Database saturated"
                    : "Database load within capacity"}
                </Pill>
              </div>
              <div className="capacity-metrics">
                <div>
                  <span>EFFECTIVE CACHE HIT RATE</span>
                  <strong>
                    {dataLoad.effectiveHitPercent}
                    <small>%</small>
                  </strong>
                </div>
                <div>
                  <span>CACHE HITS</span>
                  <strong>
                    {dataLoad.cacheHitsRps}
                    <small> /s</small>
                  </strong>
                </div>
                <div className={dataLoad.saturated ? "failed-metric" : ""}>
                  <span>DATABASE DEMAND</span>
                  <strong>
                    {dataLoad.databaseDemandOps}
                    <small> ops/s</small>
                  </strong>
                </div>
                <div>
                  <span>DATABASE CAPACITY</span>
                  <strong>
                    {dataLoad.databaseCapacityOps}
                    <small> ops/s</small>
                  </strong>
                </div>
              </div>
              <div className="traffic-control">
                <label htmlFor="cache-hit-rate">Cache hit rate</label>
                <select
                  id="cache-hit-rate"
                  disabled={busy}
                  value={w.cacheHitPercent ?? 90}
                  onChange={(e) =>
                    void cache(!!w.cacheRunning, Number(e.target.value))
                  }
                >
                  <option value={90}>Healthy · 90%</option>
                  <option value={20}>Degraded · 20%</option>
                  <option value={0}>All reads miss · 0%</option>
                  {![90, 20, 0].includes(w.cacheHitPercent ?? 90) && (
                    <option value={w.cacheHitPercent!}>
                      Custom · {w.cacheHitPercent}%
                    </option>
                  )}
                </select>
                <button
                  className={"switch " + (w.cacheRunning ? "on" : "")}
                  role="switch"
                  aria-checked={!!w.cacheRunning}
                  aria-label="Cache service running"
                  disabled={busy}
                  onClick={() =>
                    void cache(!w.cacheRunning, w.cacheHitPercent ?? 90)
                  }
                >
                  <span />
                </button>
                <span>
                  Cache {w.cacheRunning ? "online" : "offline"} ·{" "}
                  {dataLoad.admittedRps} admitted requests/s. Database demand is
                  offered load, not completed operations.
                </span>
              </div>
            </div>
            <div className="environment-controls">
              <div>
                <span className="control-icon">⇄</span>
                <div>
                  <strong>Database connection</strong>
                  <small>Checkout → database firewall rule</small>
                </div>
                {toggle(
                  "link",
                  w.linkAllowed,
                  "Allow checkout to database connection",
                )}
              </div>
              <div>
                <span className="control-icon">↥</span>
                <div>
                  <strong>Broken deployment</strong>
                  <small>Introduce an order validation regression</small>
                </div>
                {toggle(
                  "deployment",
                  w.badDeploy,
                  "Broken checkout deployment",
                )}
              </div>
            </div>
            <div className="presets">
              <span>TRY A SCENARIO</span>
              {[
                ["connection", "Blocked connection"],
                ["deployment", "Bad deployment"],
                ["compound", "Two faults"],
                ["redundancy", "Lost redundancy"],
                ["overload", "Overloaded instance"],
                ["cache", "Cache outage"],
                ["cache-degraded", "Cache degradation"],
              ].map(([name, label]) => (
                <button
                  disabled={busy}
                  key={name}
                  onClick={() => void preset(name)}
                >
                  {label}
                  <span>↗</span>
                </button>
              ))}
              <button
                className="restore"
                disabled={busy}
                onClick={() => void preset("healthy")}
              >
                Restore healthy
              </button>
            </div>
            <div
              className={
                "customer-check " + (check.success ? "healthy" : "failing")
              }
            >
              <span className="check-symbol">{check.success ? "✓" : "!"}</span>
              <div>
                <strong>Customer checkout batch</strong>
                <p>{check.message}</p>
              </div>
              <span className="latency">
                {check.latencyMs.toLocaleString()} ms
              </span>
              <button
                disabled={busy}
                onClick={() =>
                  void act(async () => {
                    const r = await api<Check>("/checkout", {});
                    setNotice(
                      `Customer check at revision ${r.revision}: ${r.message}`,
                    );
                  })
                }
              >
                Run check ↗
              </button>
            </div>
          </section>
          <div className="work-grid">
            <section className="investigation-panel">
              <div className="section-head">
                <div>
                  <span className="eyebrow">02 / THE INVESTIGATION</span>
                  <h2>
                    {incident ? "Incident workspace" : "Start with a symptom"}
                  </h2>
                </div>
                {incident && (
                  <Pill good={resolved ? true : undefined}>
                    {statusText(incident.status)}
                  </Pill>
                )}
              </div>
              {!incident ? (
                <div className="empty-workspace">
                  <span className="empty-icon">◎</span>
                  <h3>Let the evidence lead.</h3>
                  <p>
                    Change a component above, open an incident, and let the
                    commander choose the specialists needed to investigate.
                  </p>
                  <label htmlFor="incident-title">
                    Incident description <span>(optional)</span>
                  </label>
                  <input
                    id="incident-title"
                    maxLength={300}
                    value={title}
                    onChange={(e) => setTitle(e.target.value)}
                    placeholder="Use the current customer symptom"
                  />
                  <button
                    className="primary"
                    disabled={busy}
                    onClick={() => void openIncident()}
                  >
                    Open incident <span>→</span>
                  </button>
                </div>
              ) : (
                <>
                  <div className="incident-intro">
                    <small>
                      {incident.id.slice(0, 8).toUpperCase()} · OPENED{" "}
                      {time(incident.createdAt)}
                    </small>
                    <h3>{incident.title}</h3>
                    <div className="workflow-actions">
                      <button
                        className="primary"
                        disabled={busy || running || resolved}
                        onClick={() =>
                          void act(() =>
                            api(`/incidents/${incident.id}/investigate`, {}),
                          )
                        }
                      >
                        {running ? (
                          <>
                            <span className="spinner" /> Investigating…
                          </>
                        ) : run ? (
                          "Reinvestigate with Loomspan ↗"
                        ) : (
                          "Investigate with Loomspan ↗"
                        )}
                      </button>
                      <button
                        disabled={busy || running || resolved}
                        onClick={() =>
                          void act(async () => {
                            const r = await api<Recovery>(
                              `/incidents/${incident.id}/verify`,
                              {},
                            );
                            setNotice(r.message);
                          })
                        }
                      >
                        Verify recovery
                      </button>
                    </div>
                  </div>
                  {resolved && (
                    <div className="resolved-message">
                      <span>✓</span>
                      <div>
                        <strong>Recovery verified</strong>
                        <p>
                          The saved recovery checks passed when this incident
                          was resolved. The current environment can still change
                          independently.
                        </p>
                      </div>
                    </div>
                  )}
                  {run && stale && !resolved && (
                    <div className="inline-warning">
                      Environment changed from revision {run.snapshot.revision}{" "}
                      to {w.revision}.{" "}
                      {incident.status === "MITIGATED"
                        ? "Verify checkout and instance availability, then reinvestigate if either check fails."
                        : "This assessment is historical. Reinvestigate before applying a repair."}
                    </div>
                  )}
                  {run?.error && (
                    <div className="inline-warning">{run.error}</div>
                  )}
                  {running && (
                    <div className="running-message">
                      <span className="spinner" />
                      <div>
                        <strong>Specialists are gathering evidence</strong>
                        <p>
                          Probe receipts appear as they arrive. The observed
                          skill timeline is available after a successful
                          Loomspan execution.
                        </p>
                      </div>
                    </div>
                  )}
                  {run?.report ? (
                    <div className="report">
                      <div className="report-label">
                        <span>
                          {stale || resolved
                            ? `HISTORICAL ASSESSMENT · REV ${run.snapshot.revision}`
                            : "COMMANDER’S ASSESSMENT"}
                        </span>
                        <Pill>{run.report.confidence} confidence</Pill>
                      </div>
                      <h3>{run.report.likelyCause}</h3>
                      <p>{run.report.summary}</p>
                      <div className="recommendations">
                        {run.report.recommendations.map((rec) => {
                          const receipt = run.evidence.find(
                            (e) => e.id === rec.evidenceId,
                          );
                          const action = receipt?.actions.find(
                            (a) => a.id === rec.actionId,
                          );
                          return (
                            <div className="recommendation" key={rec.actionId}>
                              <div className="repair-icon">↳</div>
                              <div>
                                <h4>{action?.label || rec.actionId}</h4>
                                <p>{rec.reason}</p>
                                <small>{action?.effect}</small>
                                <button
                                  className="repair-button"
                                  disabled={
                                    busy ||
                                    stale ||
                                    incident.status !== "DIAGNOSED"
                                  }
                                  onClick={() =>
                                    void act(async () => {
                                      await api(
                                        `/incidents/${incident.id}/repair`,
                                        {
                                          runId: run.id,
                                          actionId: rec.actionId,
                                        },
                                      );
                                      setNotice(
                                        "Simulated repair applied. Verify recovery before closing the incident.",
                                      );
                                    })
                                  }
                                >
                                  Apply simulated repair →
                                </button>
                              </div>
                            </div>
                          );
                        })}
                      </div>
                      {run.report.recommendations.length === 0 && (
                        <p className="muted">
                          No repair was supported by the collected evidence.
                        </p>
                      )}
                      <div className="next-step">
                        <strong>Next step</strong>
                        <p>{run.report.nextStep}</p>
                      </div>
                    </div>
                  ) : (
                    !running &&
                    !run?.error &&
                    !resolved && (
                      <div className="waiting">
                        <span>◌</span>
                        <p>
                          Ready to investigate. The commander receives the
                          customer symptom and can ask specialists for evidence.
                        </p>
                      </div>
                    )
                  )}
                </>
              )}
            </section>
            <section className="evidence-panel">
              <div className="section-head">
                <div>
                  <span className="eyebrow">03 / THE RECORD</span>
                  <h2>Follow the work</h2>
                </div>
                <span className="record-icon">≋</span>
              </div>
              <div
                className="tabs"
                role="tablist"
                aria-label="Investigation records"
              >
                {(["evidence", "coordination", "history"] as const).map((t) => (
                  <button
                    role="tab"
                    aria-selected={tab === t}
                    className={tab === t ? "active" : ""}
                    key={t}
                    onClick={() => setTab(t)}
                  >
                    {t}
                    {t === "evidence" && run ? (
                      <span>{run.evidence.length}</span>
                    ) : null}
                  </button>
                ))}
              </div>
              {tab === "evidence" &&
                (run?.evidence.length ? (
                  <div className="receipts">
                    {run.evidence.map((e) => (
                      <Evidence
                        key={e.id}
                        receipt={e}
                        cited={!!run.report?.evidenceIds.includes(e.id)}
                      />
                    ))}
                  </div>
                ) : (
                  <div className="record-empty">
                    <span>⌕</span>
                    <h3>No evidence collected yet</h3>
                    <p>
                      Each probe records what it observed, when it ran, and
                      which environment revision it examined.
                    </p>
                  </div>
                ))}
              {tab === "coordination" && <Coordination run={run} />}
              {tab === "history" && (
                <div className="activity-list">
                  {(detail?.activity || state.activity).length ? (
                    (detail?.activity || state.activity).map((a) => (
                      <div className="activity" key={a.id}>
                        <span
                          className={
                            "activity-dot " +
                            (a.kind === "VERIFICATION" ? "green" : "")
                          }
                        />
                        <div>
                          <small>
                            {time(a.createdAt)} · REV {a.revision} · {a.kind}
                          </small>
                          <p>{a.message}</p>
                        </div>
                      </div>
                    ))
                  ) : (
                    <div className="record-empty">
                      <h3>A clean slate</h3>
                      <p>
                        Environment changes and incident actions will be
                        recorded here.
                      </p>
                    </div>
                  )}
                  {detail && detail.runs.length > 1 && (
                    <div className="previous-runs">
                      <h4>Previous investigations</h4>
                      {detail.runs.slice(1).map((r) => (
                        <details key={r.id}>
                          <summary>
                            Revision {r.snapshot.revision} ·{" "}
                            {statusText(r.status)} · {time(r.createdAt)}
                          </summary>
                          <p>
                            {r.report?.summary ||
                              r.error ||
                              "No report available."}
                          </p>
                          {r.evidence.map((e) => (
                            <Evidence key={e.id} receipt={e} cited={false} />
                          ))}
                        </details>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </section>
          </div>
          {incident && (
            <div className="new-incident">
              <p>A new failure deserves its own record.</p>
              <button
                disabled={busy}
                onClick={() => {
                  selectedRef.current = null;
                  setSelected(null);
                  setDetail(null);
                  setTitle("");
                  setNotice("");
                }}
              >
                Open another incident +
              </button>
            </div>
          )}
          <footer>
            <span>
              RELAY <span className="footer-separator">/</span> A Loomspan
              framework demo
            </span>
            <span>
              Simulated infrastructure · Persistent evidence · Model-driven
              investigation
            </span>
          </footer>
        </div>
      </main>
    </div>
  );
}
function Evidence({ receipt: e, cited }: { receipt: Receipt; cited: boolean }) {
  return (
    <article className="receipt">
      <div className="receipt-head">
        <span className="receipt-icon">⌁</span>
        <strong>{probeName(e.probe)}</strong>
        {cited && <span className="cited">CITED</span>}
      </div>
      <p>{e.observation}</p>
      <div className="receipt-meta">
        <span>
          {time(e.observedAt)} · REV {e.revision}
        </span>
        <code title={e.id}>
          {e.id.startsWith("evidence-") ? e.id : e.id.slice(0, 8)}
        </code>
      </div>
    </article>
  );
}
function Coordination({ run }: { run?: Run }) {
  if (!run?.events.length)
    return (
      <div className="record-empty">
        <span>⑂</span>
        <h3>The actual skill execution</h3>
        <p>
          After a successful execution, this view shows observed starts and
          finishes from Loomspan. Probe receipts remain available even if an
          investigation fails.
        </p>
      </div>
    );
  const starts = run.events.filter((e) => e.type === "SKILL_STARTED");
  const startTime = Math.min(...starts.map((e) => Date.parse(e.timestamp)));
  const endTime = Math.max(...run.events.map((e) => Date.parse(e.timestamp)));
  const total = Math.max(1, endTime - startTime);
  return (
    <div className="coordination">
      <div className="session">
        <span>LOOMSPAN SESSION</span>
        <code>{run.sessionId}</code>
        <small>Observed execution · {(total / 1000).toFixed(1)} s</small>
      </div>
      {starts.map((e, index) => {
        const end = run.events.find(
          (x) => x.type === "SKILL_FINISHED" && x.frameId === e.frameId,
        );
        const elapsed = end
          ? Date.parse(end.timestamp) - Date.parse(e.timestamp)
          : null;
        return (
          <div className="execution" key={`${e.frameId}-${index}`}>
            <div>
              <strong title={e.route || ""}>
                {e.route?.split("/").at(-1) || "Skill"}
              </strong>
              <span>
                {elapsed === null ? "—" : (elapsed / 1000).toFixed(1) + " s"}
              </span>
            </div>
            <div className="duration-track">
              <span
                style={{
                  marginLeft:
                    ((Date.parse(e.timestamp) - startTime) / total) * 100 + "%",
                  width: Math.max(1, ((elapsed || 0) / total) * 100) + "%",
                }}
              />
            </div>
            <small>
              {time(e.timestamp)} →{" "}
              {end ? time(end.timestamp) : "finish not reported"}
            </small>
          </div>
        );
      })}
      <p className="timeline-note">
        Bars use recorded event timestamps. Overlap is shown only when it
        occurred.
      </p>
    </div>
  );
}
createRoot(document.getElementById("root")!).render(<App />);
