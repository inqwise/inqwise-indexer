import type {
  Indexer,
  OperationalMetrics,
  Target,
} from "../api/indexer-api";

const MAX_VISIBLE_ACTIVE_ISSUES = 12;

export type RuntimeDriftIssue = {
  indexerId: number;
  indexName: string;
  targetName: string;
  kind: "MISSING_ATTACHMENT" | "UNEXPECTED_ATTACHMENT";
};

export type ServiceIssue = {
  name: string;
  label: string;
  state: "checking" | "online" | "degraded";
  error: string | null;
};

type IssueDestination =
  | { section: "overview" | "runtime" | "metrics" }
  | { section: "targets"; id: number }
  | { section: "indexers"; id: number };

type OperationalIssue = {
  id: string;
  severity: "critical" | "warning";
  category: string;
  title: string;
  detail: string;
  destination: IssueDestination;
};

type ObservedSignal = {
  id: string;
  label: string;
  value: number;
  detail: string;
};

export default function OperationalIssuesView({
  indexers,
  metrics,
  onSelectIndexer,
  onSelectTarget,
  runtimeComparisonAvailable,
  runtimeDrifts,
  services,
  targets,
}: {
  indexers: Indexer[];
  metrics: OperationalMetrics | null;
  onSelectIndexer: (id: number) => void;
  onSelectTarget: (id: number) => void;
  runtimeComparisonAvailable: boolean;
  runtimeDrifts: RuntimeDriftIssue[];
  services: ServiceIssue[];
  targets: Target[];
}) {
  const activeIssues = buildActiveIssues({
    indexers,
    metrics,
    runtimeComparisonAvailable,
    runtimeDrifts,
    services,
    targets,
  });
  const observedSignals = buildObservedSignals(metrics);
  const visibleIssues = activeIssues.slice(0, MAX_VISIBLE_ACTIVE_ISSUES);

  function select(destination: IssueDestination) {
    if (destination.section === "targets") {
      onSelectTarget(destination.id);
    } else if (destination.section === "indexers") {
      onSelectIndexer(destination.id);
    }
  }

  return (
    <section aria-label="Operational issues" className="issues-view" id="issues">
      <div className="issues-view__header">
        <div>
          <span className="eyebrow">Attention queue</span>
          <h3>Operational issues</h3>
          <p>
            Current catalog, runtime, and service conditions that may require an
            operator response.
          </p>
        </div>
        <span className={`issues-count${activeIssues.length > 0 ? " issues-count--warn" : ""}`}>
          {activeIssues.length} active
        </span>
      </div>

      {activeIssues.length === 0 ? (
        <div className="issues-empty" role="status">
          <span aria-hidden="true">✓</span>
          <div>
            <strong>No active operational issues</strong>
            <p>Catalog state, runtime convergence, and internal services are healthy.</p>
          </div>
        </div>
      ) : (
        <div className="issues-list">
          {visibleIssues.map((issue) => (
            <article className={`issue-card issue-card--${issue.severity}`} key={issue.id}>
              <span className="issue-card__mark" aria-hidden="true">!</span>
              <div>
                <span>{issue.category}</span>
                <strong>{issue.title}</strong>
                <p>{issue.detail}</p>
              </div>
              <a
                href={`#${issue.destination.section}`}
                onClick={() => select(issue.destination)}
              >
                Inspect
              </a>
            </article>
          ))}
          {activeIssues.length > visibleIssues.length && (
            <p className="issues-truncated">
              {activeIssues.length - visibleIssues.length} additional issues are
              available in the relevant catalog views.
            </p>
          )}
        </div>
      )}

      {observedSignals.length > 0 && (
        <div className="observed-signals">
          <div>
            <strong>Observed since process start</strong>
            <p>Cumulative counters are signals, not necessarily active incidents.</p>
          </div>
          <div className="observed-signals__grid">
            {observedSignals.map((signal) => (
              <a href="#metrics" key={signal.id}>
                <span>{signal.label}</span>
                <strong>{signal.value.toLocaleString()}</strong>
                <small>{signal.detail}</small>
              </a>
            ))}
          </div>
        </div>
      )}
    </section>
  );
}

function buildActiveIssues({
  indexers,
  metrics,
  runtimeComparisonAvailable,
  runtimeDrifts,
  services,
  targets,
}: {
  indexers: Indexer[];
  metrics: OperationalMetrics | null;
  runtimeComparisonAvailable: boolean;
  runtimeDrifts: RuntimeDriftIssue[];
  services: ServiceIssue[];
  targets: Target[];
}): OperationalIssue[] {
  const issues: OperationalIssue[] = [];
  for (const service of services) {
    if (service.state === "degraded") {
      issues.push({
        id: `service-${service.name}`,
        severity: "critical",
        category: "Internal service",
        title: `${service.label} unavailable`,
        detail: service.error ?? "The last refresh did not complete successfully.",
        destination: { section: "overview" },
      });
    }
  }
  for (const target of targets) {
    if (target.provisioning_state === "FAILED") {
      issues.push({
        id: `target-${target.id}`,
        severity: "critical",
        category: "Target provisioning",
        title: `${target.target_name} failed`,
        detail: "Provisioning must be recovered before this target can become ready.",
        destination: { section: "targets", id: target.id },
      });
    }
  }
  for (const indexer of indexers) {
    if (indexer.provisioning_state === "FAILED") {
      issues.push({
        id: `indexer-${indexer.id}`,
        severity: "critical",
        category: "Indexer provisioning",
        title: `${indexer.index_name} failed`,
        detail: `The indexer for ${indexer.target_name} is not ready for runtime work.`,
        destination: { section: "indexers", id: indexer.id },
      });
    }
  }
  for (const drift of runtimeDrifts) {
    issues.push({
      id: `runtime-${drift.indexerId}-${drift.kind}`,
      severity: "warning",
      category: "Runtime convergence",
      title: drift.kind === "MISSING_ATTACHMENT"
        ? `${drift.indexName} is not attached`
        : `${drift.indexName} is unexpectedly attached`,
      detail: `${drift.targetName} differs from the desired catalog runtime state.`,
      destination: { section: "runtime" },
    });
  }
  if ((metrics?.lifecyclePending ?? 0) > 0) {
    issues.push({
      id: "lifecycle-pending",
      severity: "warning",
      category: "Lifecycle commands",
      title: `${metrics?.lifecyclePending ?? 0} operations pending`,
      detail: "Durable lifecycle work has not reached a terminal outcome.",
      destination: { section: "metrics" },
    });
  }
  if (
    !runtimeComparisonAvailable &&
    (metrics?.runtimeDrift ?? 0) > 0
  ) {
    issues.push({
      id: "runtime-drift-metric",
      severity: "warning",
      category: "Runtime convergence",
      title: `${metrics?.runtimeDrift ?? 0} runtime drift`,
      detail: "The detailed catalog comparison is unavailable; inspect runtime metrics.",
      destination: { section: "metrics" },
    });
  }
  return issues;
}

function buildObservedSignals(metrics: OperationalMetrics | null): ObservedSignal[] {
  if (!metrics) {
    return [];
  }
  return [
    {
      id: "rejected-actions",
      label: "Rejected actions",
      value: metrics.rejectedActions,
      detail: "intake validation or routing",
    },
    {
      id: "failed-processing",
      label: "Failed processing",
      value: metrics.processedFailed,
      detail: "indexing outcomes",
    },
    {
      id: "failed-lifecycle",
      label: "Failed lifecycle",
      value: metrics.lifecycleFailed,
      detail: "command outcomes",
    },
    {
      id: "retrying-lifecycle",
      label: "Retry signals",
      value: metrics.lifecycleRetrying,
      detail: "lifecycle retries",
    },
  ].filter((signal) => signal.value > 0);
}
