import { useState } from "react";
import type { Run } from "./types";

export function ConsoleInspection({ run }: { run: Run }) {
  const [message, setMessage] = useState("");
  async function copy() {
    try {
      await navigator.clipboard.writeText(run.sessionId!);
      setMessage("Session ID copied.");
    } catch {
      setMessage(
        "Copy unavailable. Select the session ID below and copy it manually.",
      );
    }
  }
  return (
    <details className="console-inspection">
      <summary>Inspect this execution in Loomspan Console</summary>
      <p>
        Relay shows recorded skill starts and finishes. Console can inspect the
        plan, model calls, structured output and retries when its trace is
        available.
      </p>
      <p>
        Session:{" "}
        <code>{run.sessionId || "Not available from the public observer"}</code>
      </p>
      {run.sessionId && (
        <button onClick={() => void copy()}>Copy session ID</button>
      )}
      {message && <p role="status">{message}</p>}
      <ol>
        <li>
          Connect Console to this Relay application's address, using its
          configured observability key. The Relay sign-in password is a separate
          credential.
        </li>
        <li>
          Find the execution or retained trace matching this session. Resolve
          its trace ID in Console; a session ID is not a trace ID.
        </li>
        <li>
          Inspect the selected skills and plan dependencies. Check recorded
          overlap before claiming concurrency, then follow the evidence reader
          and final structured output.
        </li>
      </ol>
      <p>
        Console access is optional and disabled by default. Enable Relay
        observability and configure Console before presenting; see the
        repository's framework-tour guide. This panel does not test the Console
        connection or trace availability.
      </p>
      <p>
        A correction, when present, has a separate session shown below its
        correction record. A new investigation also starts a new session.
      </p>
    </details>
  );
}
