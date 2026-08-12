import type { Indexer, Target } from "../api/indexer-api";
import type { LoadView } from "../api/load-query-api";
import EntityLink from "./EntityLink";

type Props = {
  diagnostic: {
    state: "checking" | "online" | "degraded";
    error: string | null;
  };
  indexers: Indexer[];
  loads: LoadView[];
  onSelectIndexer: (id: number) => void;
  onSelectTarget: (id: number) => void;
  targets: Target[];
};

const MAX_VISIBLE_LOADS = 25;
const MAX_TEXT = 180;

export default function LoadOperationsView({
  diagnostic,
  indexers,
  loads,
  onSelectIndexer,
  onSelectTarget,
  targets,
}: Props) {
  const targetsById = new Map(targets.map((target) => [target.id, target]));
  const indexersById = new Map(indexers.map((indexer) => [indexer.id, indexer]));
  const visible = loads.slice(0, MAX_VISIBLE_LOADS);

  return (
    <section aria-label="Load operations" className="loads-view" id="loads">
      <div className="loads-view__header">
        <div>
          <span className="eyebrow">Workflow visibility</span>
          <h3>Load operations</h3>
          <p>
            Active and retained load workflow state from the load-owned
            read-side contract. This view does not create or mutate loads.
          </p>
        </div>
        <span className={`loads-state loads-state--${diagnostic.state}`}>
          {diagnostic.state === "checking"
            ? "Loading"
            : diagnostic.state === "online"
              ? `${loads.length} visible`
              : "Unavailable"}
        </span>
      </div>

      {diagnostic.state === "degraded" && loads.length === 0 ? (
        <p className="loads-empty loads-empty--warn">
          {diagnostic.error ?? "Load workflow state is unavailable."}
        </p>
      ) : visible.length === 0 ? (
        <p className="loads-empty">There are no active or retained loads.</p>
      ) : (
        <div className="loads-list">
          {visible.map((load) => (
            <article key={load.indexer_id}>
              <div className="load-card__header">
                <div>
                  <span>{safe(load.provider_id)}</span>
                  <strong>Load writer #{load.indexer_id}</strong>
                </div>
                <span className={`load-state load-state--${stateTone(load.state)}`}>
                  {format(load.state)} · v{load.version}
                </span>
              </div>

              <div className="load-relations">
                <Relationship
                  id={load.target_id}
                  kind="target"
                  label="Target"
                  known={targetsById.has(load.target_id)}
                  onNavigate={onSelectTarget}
                />
                <Relationship
                  id={load.indexer_id}
                  kind="indexer"
                  label="Load writer"
                  known={indexersById.has(load.indexer_id)}
                  onNavigate={onSelectIndexer}
                />
                {load.live_indexer_id != null && (
                  <Relationship
                    id={load.live_indexer_id}
                    kind="indexer"
                    label="Live writer"
                    known={indexersById.has(load.live_indexer_id)}
                    onNavigate={onSelectIndexer}
                  />
                )}
              </div>

              <dl className="load-facts">
                <Fact label="Live policy" value={format(load.live_writer_policy)} />
                <Fact label="Review" value={load.review_required ? "Required" : "Not required"} />
                <Fact label="Source window" value={windowLabel(load.source_from, load.source_to)} />
                <Fact label="Updated" value={dateLabel(load.updated_at)} />
                <Fact label="Barrier" value={barrierLabel(load)} />
                <Fact label="Approved" value={dateLabel(load.approved_at)} />
              </dl>

              {load.failure_reason && (
                <p className="load-card__failure">
                  <strong>Failure</strong> {safe(load.failure_reason)}
                </p>
              )}
              {load.source_playbook_id && (
                <p className="load-card__context">
                  Playbook: <strong>{safe(load.source_playbook_id)}</strong>
                </p>
              )}
            </article>
          ))}
        </div>
      )}

      {loads.length > MAX_VISIBLE_LOADS && (
        <p className="loads-truncated">
          Showing the first {MAX_VISIBLE_LOADS} of {loads.length} load records.
        </p>
      )}
      {diagnostic.state === "degraded" && loads.length > 0 && (
        <p className="loads-truncated">
          Retaining the last successful load snapshot. {diagnostic.error}
        </p>
      )}
    </section>
  );
}

function Relationship({
  id,
  kind,
  known,
  label,
  onNavigate,
}: {
  id: number;
  kind: "target" | "indexer";
  known: boolean;
  label: string;
  onNavigate: (id: number) => void;
}) {
  return (
    <div>
      <span>{label}</span>
      {known ? (
        <EntityLink destination={{ kind, id }} onNavigate={() => onNavigate(id)}>
          #{id}
        </EntityLink>
      ) : (
        <strong className="entity-reference-missing">Missing #{id}</strong>
      )}
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}

function barrierLabel(load: LoadView): string {
  if (load.last_barrier_reached_at) return `Reached ${dateLabel(load.last_barrier_reached_at)}`;
  if (load.last_barrier_timestamp) return `Requested ${dateLabel(load.last_barrier_timestamp)}`;
  return "Not requested";
}

function windowLabel(
  from: string | null | undefined,
  to: string | null | undefined,
): string {
  if (!from && !to) return "Not constrained";
  return `${dateLabel(from)} → ${dateLabel(to)}`;
}

function dateLabel(value: string | null | undefined): string {
  return value ? new Date(value).toLocaleString() : "Not set";
}

function stateTone(value: string): "good" | "warn" | "neutral" {
  if (["PUBLISHED", "CATCH_UP_READY", "APPROVED"].includes(value)) return "good";
  if (["FAILED", "CANCELLED"].includes(value)) return "warn";
  return "neutral";
}

function format(value: string): string {
  return value.replaceAll("_", " ").toLowerCase();
}

function safe(value: string): string {
  return value.slice(0, MAX_TEXT);
}
