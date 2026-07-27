import { useCallback, useEffect, useMemo, useState } from "react";

type HealthState = "checking" | "online" | "offline";

type Target = {
  id: number;
  target_name: string;
  period_key: string;
  status: string;
  provisioning_state: string;
  version: number;
};

type Indexer = {
  id: number;
  target_name: string;
  index_name: string;
  role: string;
  status: string;
  provisioning_state: string;
  runtime_state: string;
  publication_state: string;
  version: number;
};

type RuntimeIndexer = {
  indexer_id: number;
  target_name: string;
  index_name: string;
  runtime_state: string;
};

type DashboardData = {
  health: HealthState;
  targets: Target[];
  indexers: Indexer[];
  runtimeIndexers: RuntimeIndexer[];
  error: string | null;
};

const INITIAL_DATA: DashboardData = {
  health: "checking",
  targets: [],
  indexers: [],
  runtimeIndexers: [],
  error: null,
};

async function isReady(signal: AbortSignal): Promise<boolean> {
  const response = await fetch("/api/health/health/ready", { signal });
  return response.status === 204;
}

async function getJson<T>(path: string, signal: AbortSignal): Promise<T> {
  const response = await fetch(path, {
    headers: { accept: "application/json" },
    signal,
  });
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`.trim());
  }
  return response.json() as Promise<T>;
}

function statusTone(status: string): "good" | "warn" | "neutral" {
  if (["ACTIVE", "READY", "PUBLISHED", "COMPLETED"].includes(status)) {
    return "good";
  }
  if (["FAILED", "DELETING", "INACTIVE", "NON_ACTIVE"].includes(status)) {
    return "warn";
  }
  return "neutral";
}

function StatusPill({ value }: { value: string }) {
  return (
    <span className={`status-pill status-pill--${statusTone(value)}`}>
      <span aria-hidden="true" className="status-pill__dot" />
      {value.replaceAll("_", " ").toLowerCase()}
    </span>
  );
}

function Metric({
  label,
  value,
  detail,
  accent,
}: {
  label: string;
  value: number | string;
  detail: string;
  accent: "cyan" | "violet" | "amber";
}) {
  return (
    <article className={`metric metric--${accent}`}>
      <div className="metric__topline">
        <span>{label}</span>
        <span className="metric__mark" aria-hidden="true" />
      </div>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  );
}

export default function App() {
  const [data, setData] = useState<DashboardData>(INITIAL_DATA);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const [health, targetResult, indexerResult, runtimeResult] =
        await Promise.all([
          isReady(signal),
          getJson<{ targets?: Target[] }>("/api/admin/admin/targets", signal),
          getJson<{ indexers?: Indexer[] }>("/api/admin/admin/indexers", signal),
          getJson<{ indexers?: RuntimeIndexer[] }>(
            "/api/runtime/runtime/status",
            signal,
          ),
        ]);

      setData({
        health: health ? "online" : "offline",
        targets: targetResult.targets ?? [],
        indexers: indexerResult.indexers ?? [],
        runtimeIndexers: runtimeResult.indexers ?? [],
        error: null,
      });
      setLastUpdated(new Date());
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        return;
      }
      setData((current) => ({
        ...current,
        health: "offline",
        error:
          "The local indexer node is unavailable. Start the local deployment to see live operational data.",
      }));
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    const initial = window.setTimeout(() => {
      void load(controller.signal);
    }, 0);
    const interval = window.setInterval(() => {
      void load(controller.signal);
    }, 15_000);

    return () => {
      controller.abort();
      window.clearTimeout(initial);
      window.clearInterval(interval);
    };
  }, [load]);

  const activeIndexers = useMemo(
    () =>
      data.indexers.filter(
        (indexer) =>
          indexer.runtime_state === "ACTIVE" || indexer.status === "ACTIVE",
      ).length,
    [data.indexers],
  );

  const publishedIndexers = useMemo(
    () =>
      data.indexers.filter(
        (indexer) => indexer.publication_state === "PUBLISHED",
      ).length,
    [data.indexers],
  );

  const provisioningIssues = useMemo(
    () =>
      data.targets.filter((target) => target.provisioning_state === "FAILED")
        .length +
      data.indexers.filter(
        (indexer) => indexer.provisioning_state === "FAILED",
      ).length,
    [data.indexers, data.targets],
  );

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="Inqwise Indexer home">
          <span className="brand__glyph">IQ</span>
          <span>
            <strong>Inqwise</strong>
            <small>Indexer console</small>
          </span>
        </a>

        <nav className="nav" aria-label="Primary navigation">
          <a className="nav__item nav__item--active" href="#overview">
            <span aria-hidden="true">⌁</span>
            Overview
          </a>
          <a className="nav__item" href="#targets">
            <span aria-hidden="true">◎</span>
            Targets
          </a>
          <a className="nav__item" href="#indexers">
            <span aria-hidden="true">◇</span>
            Indexers
          </a>
          <a className="nav__item" href="#runtime">
            <span aria-hidden="true">↯</span>
            Runtime
          </a>
        </nav>

        <div className="sidebar__foot">
          <span className="eyebrow">Environment</span>
          <div className="environment">
            <span className="environment__pulse" />
            Local development
          </div>
          <p>Internal APIs only · Gateway disabled</p>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <span className="eyebrow">Operations / Local node</span>
            <h1>Indexer overview</h1>
          </div>
          <div className="topbar__actions">
            <div className={`node-state node-state--${data.health}`}>
              <span aria-hidden="true" />
              {data.health === "online" ? "Node ready" : "Node offline"}
            </div>
            <button
              className="refresh-button"
              type="button"
              disabled={refreshing}
              onClick={() => {
                const controller = new AbortController();
                setRefreshing(true);
                void load(controller.signal);
              }}
            >
              <span aria-hidden="true" className={refreshing ? "spin" : ""}>
                ↻
              </span>
              {refreshing ? "Refreshing" : "Refresh"}
            </button>
          </div>
        </header>

        <div className="content" id="overview">
          {data.error && (
            <div className="notice" role="status">
              <span className="notice__icon" aria-hidden="true">
                !
              </span>
              <div>
                <strong>Waiting for the indexer node</strong>
                <p>{data.error}</p>
              </div>
              <code>./run-local.sh</code>
            </div>
          )}

          <section className="hero">
            <div>
              <span className="hero__kicker">Control plane</span>
              <h2>Everything indexed, visible at a glance.</h2>
              <p>
                Monitor target readiness, publication state, and local runtime
                convergence from one focused operational view.
              </p>
            </div>
            <div className="hero__signal" aria-hidden="true">
              <span />
              <span />
              <span />
              <i />
            </div>
          </section>

          <section className="metrics" aria-label="Indexer metrics">
            <Metric
              label="Targets"
              value={data.targets.length}
              detail={
                data.targets.length === 0
                  ? "No catalog records"
                  : `${data.targets.filter((target) => target.status === "READY").length} ready`
              }
              accent="cyan"
            />
            <Metric
              label="Active indexers"
              value={activeIndexers}
              detail={`${data.runtimeIndexers.length} attached to this node`}
              accent="violet"
            />
            <Metric
              label="Published"
              value={publishedIndexers}
              detail={
                provisioningIssues === 0
                  ? "No provisioning issues"
                  : `${provisioningIssues} need attention`
              }
              accent="amber"
            />
          </section>

          <div className="dashboard-grid">
            <section className="panel panel--wide" id="indexers">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Catalog</span>
                  <h3>Indexers</h3>
                </div>
                <span className="panel__count">{data.indexers.length} total</span>
              </div>

              {data.indexers.length > 0 ? (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Indexer</th>
                        <th>Target</th>
                        <th>Runtime</th>
                        <th>Publication</th>
                        <th>Version</th>
                      </tr>
                    </thead>
                    <tbody>
                      {data.indexers.slice(0, 6).map((indexer) => (
                        <tr key={indexer.id}>
                          <td>
                            <strong>{indexer.index_name}</strong>
                            <small>
                              #{indexer.id} · {indexer.role}
                            </small>
                          </td>
                          <td>{indexer.target_name}</td>
                          <td>
                            <StatusPill value={indexer.runtime_state} />
                          </td>
                          <td>
                            <StatusPill value={indexer.publication_state} />
                          </td>
                          <td className="mono">v{indexer.version}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <div className="empty-state">
                  <span aria-hidden="true">◇</span>
                  <strong>No indexers yet</strong>
                  <p>Indexers will appear here after a target is provisioned.</p>
                </div>
              )}
            </section>

            <section className="panel" id="runtime">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Local node</span>
                  <h3>Runtime</h3>
                </div>
                <span className={`live-dot live-dot--${data.health}`} />
              </div>

              <div className="runtime-summary">
                <div className="runtime-summary__ring">
                  <strong>{data.runtimeIndexers.length}</strong>
                  <span>attached</span>
                </div>
                <div>
                  <p>Reconciler state</p>
                  <strong>
                    {data.health === "online" ? "Converged" : "Unavailable"}
                  </strong>
                  <small>Polled every 15 seconds</small>
                </div>
              </div>

              <div className="runtime-list">
                {data.runtimeIndexers.slice(0, 4).map((indexer) => (
                  <div key={indexer.indexer_id}>
                    <span className="runtime-list__icon">↯</span>
                    <span>
                      <strong>{indexer.index_name}</strong>
                      <small>{indexer.target_name}</small>
                    </span>
                    <StatusPill value={indexer.runtime_state} />
                  </div>
                ))}
                {data.runtimeIndexers.length === 0 && (
                  <p className="runtime-list__empty">
                    No indexers are attached to this node.
                  </p>
                )}
              </div>
            </section>

            <section className="panel panel--targets" id="targets">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Routing destinations</span>
                  <h3>Targets</h3>
                </div>
                <span className="panel__count">{data.targets.length} total</span>
              </div>

              <div className="target-list">
                {data.targets.slice(0, 5).map((target) => (
                  <article key={target.id}>
                    <span className="target-list__glyph">
                      {target.target_name.slice(0, 1).toUpperCase()}
                    </span>
                    <div>
                      <strong>{target.target_name}</strong>
                      <small>
                        {target.period_key} · #{target.id}
                      </small>
                    </div>
                    <StatusPill value={target.provisioning_state} />
                  </article>
                ))}
                {data.targets.length === 0 && (
                  <div className="empty-state empty-state--compact">
                    <span aria-hidden="true">◎</span>
                    <strong>No targets configured</strong>
                    <p>Create a target through the internal Admin API.</p>
                  </div>
                )}
              </div>
            </section>
          </div>

          <footer>
            <span>
              Last updated{" "}
              {lastUpdated
                ? lastUpdated.toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                  })
                : "—"}
            </span>
            <span>Internal operator console · v0.1</span>
          </footer>
        </div>
      </section>
    </main>
  );
}
