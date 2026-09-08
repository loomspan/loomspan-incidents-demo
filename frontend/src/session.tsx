import React, { useEffect, useState } from "react";

export type Operator = { username: string; roles: string[] };
let csrf: { token: string; headerName: string } | null = null;
export async function csrfHeaders(): Promise<Record<string, string>> {
  if (!csrf) csrf = await (await fetch("/api/csrf")).json();
  return { [csrf!.headerName]: csrf!.token };
}
export function Session({
  children,
}: {
  children: (operator: Operator, logout: () => void) => React.ReactNode;
}) {
  const [operator, setOperator] = useState<Operator | null>(null);
  const [ready, setReady] = useState(false);
  const [username, setUsername] = useState("presenter");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  async function refresh() {
    const response = await fetch("/api/session");
    if (!response.ok) throw new Error("Could not read the operator session.");
    const value = await response.json();
    setOperator(value.username ? value : null);
    setReady(true);
  }
  useEffect(() => {
    const expired = () => {
      csrf = null;
      setOperator(null);
      setError("Your session ended. Sign in to continue.");
    };
    window.addEventListener("relay:unauthorized", expired);
    void refresh().catch((e) => {
      setError(e.message);
      setReady(true);
    });
    return () => window.removeEventListener("relay:unauthorized", expired);
  }, []);
  async function login(e: React.FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const response = await fetch("/api/login", {
        method: "POST",
        headers: {
          ...(await csrfHeaders()),
          "Content-Type": "application/x-www-form-urlencoded",
        },
        body: new URLSearchParams({ username, password }),
      });
      if (!response.ok)
        throw new Error("Sign-in failed. Check your username and password.");
      csrf = null;
      setPassword("");
      await refresh();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }
  async function logout() {
    try {
      const response = await fetch("/api/logout", {
        method: "POST",
        headers: await csrfHeaders(),
      });
      if (!response.ok)
        throw new Error("Sign-out failed. Refresh and try again.");
      csrf = null;
      setOperator(null);
    } catch (e) {
      setError((e as Error).message);
    }
  }
  if (operator)
    return (
      <>
        {error && <p role="alert">{error}</p>}
        {children(operator, () => void logout())}
      </>
    );
  return (
    <main className="sign-in">
      <form onSubmit={(e) => void login(e)}>
        <div className="brandmark">r</div>
        <span className="eyebrow">RELAY / OPERATOR ACCESS</span>
        <h1>Every repair has an owner.</h1>
        <p>
          Sign in to explore incidents, coordinate an investigation, or
          authorize recovery.
        </p>
        <label htmlFor="username">Demo account</label>
        <select
          id="username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
        >
          <option value="presenter">
            Presenter — prepare faults and walkthroughs
          </option>
          <option value="viewer">Viewer — inspect saved evidence</option>
          <option value="responder">
            Responder — investigate and apply routine repairs
          </option>
          <option value="commander">
            Incident commander — authorize all repairs
          </option>
        </select>
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <small>
          Local demo accounts all use <code>relay-demo</code>. Start as
          Presenter to prepare a scenario, then sign in as Responder.
        </small>
        {error && <p role="alert">{error}</p>}
        <button className="primary" disabled={!ready || busy}>
          {busy ? "Signing in…" : "Sign in →"}
        </button>
      </form>
    </main>
  );
}
