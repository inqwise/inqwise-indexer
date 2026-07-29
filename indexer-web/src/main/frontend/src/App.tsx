import { useCallback, useEffect, useMemo, useState } from "react";

import {
  activateIndexer,
  deactivateIndexer,
  isReady,
  listIndexers,
  listTargets,
  reconcileIndexer,
  recoverTargetProvisioning,
  runtimeStatus,
} from "./api/indexer-api";
import type {
  Indexer,
  RuntimeIndexer,
  Target,
} from "./api/indexer-api";
import CatalogDetailPanel from "./components/CatalogDetailPanel";

type HealthState = "checking" | "online" | "offline";
type IndexerRuntimeFilter = Indexer["runtime_state"] | "ALL";
type IndexerPublicationFilter = Indexer["publication_state"] | "ALL";
type TargetProvisioningFilter = Target["provisioning_state"] | "ALL";

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
  const [indexerSearch, setIndexerSearch] = useState("");
  const [indexerRuntimeFilter, setIndexerRuntimeFilter] =
    useState<IndexerRuntimeFilter>("ALL");
  const [indexerPublicationFilter, setIndexerPublicationFilter] =
    useState<IndexerPublicationFilter>("ALL");
  const [targetSearch, setTargetSearch] = useState("");
  const [targetProvisioningFilter, setTargetProvisioningFilter] =
    useState<TargetProvisioningFilter>("ALL");
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(null);
  const [selectedIndexerId, setSelectedIndexerId] = useState<number | null>(
    null,
  );

  const load = useCallback(async (signal: AbortSignal) => {
    try {
      const [health, targetResult, indexerResult, runtimeResult] =
        await Promise.all([
          isReady(signal),
          listTargets(signal),
          listIndexers(signal),
          runtimeStatus(signal),
        ]);

      setData({
        health: health ? "online" : "offline",
        targets: targetResult,
        indexers: indexerResult,
        runtimeIndexers: runtimeResult,
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
      data.indexers.filter((indexer) => indexer.runtime_state === "ACTIVE")
        .length,
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

  const filteredIndexers = useMemo(() => {
    const query = indexerSearch.trim().toLowerCase();
    return data.indexers.filter(
      (indexer) =>
        (!query ||
          [indexer.index_name, indexer.target_name, indexer.uid].some((value) =>
            value.toLowerCase().includes(query),
          )) &&
        (indexerRuntimeFilter === "ALL" ||
          indexer.runtime_state === indexerRuntimeFilter) &&
        (indexerPublicationFilter === "ALL" ||
          indexer.publication_state === indexerPublicationFilter),
    );
  }, [
    data.indexers,
    indexerPublicationFilter,
    indexerRuntimeFilter,
    indexerSearch,
  ]);

  const filteredTargets = useMemo(() => {
    const query = targetSearch.trim().toLowerCase();
    return data.targets.filter(
      (target) =>
        (!query ||
          [target.target_name, target.uid, target.period_key ?? ""].some(
            (value) => value.toLowerCase().includes(query),
          )) &&
        (targetProvisioningFilter === "ALL" ||
          target.provisioning_state === targetProvisioningFilter),
    );
  }, [data.targets, targetProvisioningFilter, targetSearch]);

  const selectedTarget =
    selectedTargetId === null
      ? null
      : (data.targets.find((target) => target.id === selectedTargetId) ?? null);
  const selectedIndexer =
    selectedIndexerId === null
      ? null
      : (data.indexers.find((indexer) => indexer.id === selectedIndexerId) ??
        null);
  const selectedRuntimeIndexer =
    selectedIndexerId === null
      ? null
      : (data.runtimeIndexers.find(
          (indexer) => indexer.indexer_id === selectedIndexerId,
        ) ?? null);

  const changeIndexerRuntimeState = useCallback(
    async (indexer: Indexer, desiredState: Indexer["runtime_state"]) => {
      let mutationFailure: unknown;
      try {
        if (desiredState === "ACTIVE") {
          await activateIndexer(indexer.id, indexer.version);
        } else {
          await deactivateIndexer(indexer.id, indexer.version);
        }
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      setRefreshing(true);
      await load(controller.signal);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const recoverTarget = useCallback(
    async (target: Target) => {
      let mutationFailure: unknown;
      try {
        await recoverTargetProvisioning(target.id, target.version);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      setRefreshing(true);
      await load(controller.signal);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const reconcileRuntime = useCallback(
    async (indexer: Indexer) => {
      let mutationFailure: unknown;
      try {
        await reconcileIndexer(indexer.id);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      setRefreshing(true);
      await load(controller.signal);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
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
                  : `${data.targets.filter((target) => target.provisioning_state === "READY").length} ready`
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
                <span className="panel__count">
                  {filteredIndexers.length} of {data.indexers.length}
                </span>
              </div>

              <div className="filters" aria-label="Indexer filters">
                <label className="filter-field filter-field--search">
                  <span>Search</span>
                  <input
                    onChange={(event) => setIndexerSearch(event.target.value)}
                    placeholder="Name, target, or UID"
                    type="search"
                    value={indexerSearch}
                  />
                </label>
                <label className="filter-field">
                  <span>Runtime</span>
                  <select
                    onChange={(event) =>
                      setIndexerRuntimeFilter(
                        event.target.value as IndexerRuntimeFilter,
                      )
                    }
                    value={indexerRuntimeFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="ACTIVE">Active</option>
                    <option value="NON_ACTIVE">Non active</option>
                  </select>
                </label>
                <label className="filter-field">
                  <span>Publication</span>
                  <select
                    onChange={(event) =>
                      setIndexerPublicationFilter(
                        event.target.value as IndexerPublicationFilter,
                      )
                    }
                    value={indexerPublicationFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="PUBLISHED">Published</option>
                    <option value="UNPUBLISHED">Unpublished</option>
                    <option value="RETIRED">Retired</option>
                  </select>
                </label>
              </div>

              {filteredIndexers.length > 0 ? (
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
                      {filteredIndexers.map((indexer) => (
                        <tr key={indexer.id}>
                          <td>
                            <button
                              className="entity-link"
                              onClick={() => {
                                setSelectedTargetId(null);
                                setSelectedIndexerId(indexer.id);
                              }}
                              type="button"
                            >
                              <strong>{indexer.index_name}</strong>
                              <small>
                                #{indexer.id} · {indexer.role}
                              </small>
                            </button>
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
                  <strong>
                    {data.indexers.length === 0
                      ? "No indexers yet"
                      : "No indexers match these filters"}
                  </strong>
                  <p>
                    {data.indexers.length === 0
                      ? "Indexers will appear here after a target is provisioned."
                      : "Adjust the search, runtime, or publication filters."}
                  </p>
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
                <span className="panel__count">
                  {filteredTargets.length} of {data.targets.length}
                </span>
              </div>

              <div className="filters" aria-label="Target filters">
                <label className="filter-field filter-field--search">
                  <span>Search</span>
                  <input
                    onChange={(event) => setTargetSearch(event.target.value)}
                    placeholder="Name, period, or UID"
                    type="search"
                    value={targetSearch}
                  />
                </label>
                <label className="filter-field">
                  <span>Provisioning</span>
                  <select
                    onChange={(event) =>
                      setTargetProvisioningFilter(
                        event.target.value as TargetProvisioningFilter,
                      )
                    }
                    value={targetProvisioningFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="READY">Ready</option>
                    <option value="PROVISIONING">Provisioning</option>
                    <option value="FAILED">Failed</option>
                  </select>
                </label>
              </div>

              <div className="target-list">
                {filteredTargets.map((target) => (
                  <button
                    className="target-card"
                    key={target.id}
                    onClick={() => {
                      setSelectedIndexerId(null);
                      setSelectedTargetId(target.id);
                    }}
                    type="button"
                  >
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
                  </button>
                ))}
                {filteredTargets.length === 0 && (
                  <div className="empty-state empty-state--compact">
                    <span aria-hidden="true">◎</span>
                    <strong>
                      {data.targets.length === 0
                        ? "No targets configured"
                        : "No targets match these filters"}
                    </strong>
                    <p>
                      {data.targets.length === 0
                        ? "Create a target through the internal Admin API."
                        : "Adjust the search or provisioning filter."}
                    </p>
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
      <CatalogDetailPanel
        indexer={selectedIndexer}
        onClose={() => {
          setSelectedIndexerId(null);
          setSelectedTargetId(null);
        }}
        onRuntimeReconcile={reconcileRuntime}
        onRuntimeStateChange={changeIndexerRuntimeState}
        onTargetRecovery={recoverTarget}
        runtimeIndexer={selectedRuntimeIndexer}
        target={selectedTarget}
      />
    </main>
  );
}
