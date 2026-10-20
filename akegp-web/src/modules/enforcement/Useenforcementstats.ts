import { useCallback, useEffect, useRef, useState } from "react";
import { getEnforcementStats, getRunDetails } from "./Enforcementapi";
import type { EnforcementStats, RunDetail } from "./Enforcementapi";

const REFRESH_MS = 4000;

export function useEnforcementStats(repo?: string, enabled = true) {
  const [stats, setStats] = useState<EnforcementStats | null>(null);
  const [details, setDetails] = useState<Map<number, RunDetail>>(new Map());
  const [failed, setFailed] = useState(false);
  const [updatedAt, setUpdatedAt] = useState<Date | null>(null);

  const reload = useRef<() => Promise<void>>(async () => undefined);

  useEffect(() => {
    const controller = new AbortController();
    setStats(null); setDetails(new Map()); setFailed(false);
    if (!enabled) return () => controller.abort();

    async function load() {
      try {
        const next = await getEnforcementStats(controller.signal, repo);
        if (controller.signal.aborted) return;
        setStats(next);
        setFailed(false);
        setUpdatedAt(new Date());
      } catch {
        if (!controller.signal.aborted) setFailed(true);
        return;
      }
      try {
        // Details are optional: if this call fails the table still works, just without the Details buttons.
        const runs = await getRunDetails(controller.signal, repo);
        if (controller.signal.aborted) return;
        setDetails(new Map(runs.map((run) => [run.runId, run])));
      } catch {
        if (!controller.signal.aborted) setDetails(new Map());
      }
    }

    reload.current = load;
    void load();
    const timer = setInterval(() => void load(), REFRESH_MS);
    return () => {
      controller.abort();
      clearInterval(timer);
    };
  }, [repo, enabled]);

  /** Loads the latest data now (used after a decision, so the page does not wait for the next refresh). */
  const refresh = useCallback(() => reload.current(), []);

  return { stats, details, failed, updatedAt, refresh };
}
