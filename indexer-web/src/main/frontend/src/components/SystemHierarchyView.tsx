import type { Indexer, RuntimeIndexer, Target } from "../api/indexer-api";
import EntityLink from "./EntityLink";

type Props = {
  hotIndexerIds: ReadonlySet<number>;
  hotTargetIds: ReadonlySet<number>;
  indexers: Indexer[];
  onSelectIndexer: (id: number) => void;
  onSelectTarget: (id: number) => void;
  runtimeIndexers: RuntimeIndexer[];
  targets: Target[];
};

const MAX_VISIBLE_TARGETS = 30;
const MAX_VISIBLE_INDEXERS = 8;

export default function SystemHierarchyView({
  hotIndexerIds,
  hotTargetIds,
  indexers,
  onSelectIndexer,
  onSelectTarget,
  runtimeIndexers,
  targets,
}: Props) {
  const attachedIds = new Set(
    runtimeIndexers.map((indexer) => indexer.indexer_id),
  );
  const knownTargetIds = new Set(targets.map((target) => target.id));
  const visibleTargets = [...targets]
    .sort(
      (left, right) =>
        left.target_name.localeCompare(right.target_name) || left.id - right.id,
    )
    .slice(0, MAX_VISIBLE_TARGETS);
  const indexersByTarget = new Map<number, Indexer[]>();

  for (const indexer of indexers) {
    const siblings = indexersByTarget.get(indexer.target_id) ?? [];
    siblings.push(indexer);
    indexersByTarget.set(indexer.target_id, siblings);
  }
  for (const siblings of indexersByTarget.values()) {
    siblings.sort(
      (left, right) =>
        left.index_name.localeCompare(right.index_name) || left.id - right.id,
    );
  }

  const orphanIndexers = indexers.filter(
    (indexer) => !knownTargetIds.has(indexer.target_id),
  );

  return (
    <section
      aria-labelledby="system-hierarchy-title"
      className="system-hierarchy"
      id="targets"
    >
      <div className="system-hierarchy__header">
        <div>
          <span className="eyebrow">System catalog</span>
          <h2 id="system-hierarchy-title">Targets and their indexers</h2>
          <p>
            Follow the system from its logical routing destination to each
            indexer and its attachment on this node.
          </p>
        </div>
        <div className="system-hierarchy__totals" aria-label="Catalog totals">
          <span><strong>{targets.length}</strong> targets</span>
          <span><strong>{indexers.length}</strong> indexers</span>
        </div>
      </div>

      <div className="entity-tree">
        {visibleTargets.map((target) => {
          const childIndexers = indexersByTarget.get(target.id) ?? [];
          const visibleIndexers = childIndexers.slice(0, MAX_VISIBLE_INDEXERS);
          return (
            <article className="entity-tree__target" key={target.id}>
              <div className="entity-tree__target-row">
                <span className="entity-tree__branch" aria-hidden="true">◎</span>
                <EntityLink
                  className="entity-tree__identity"
                  destination={{ kind: "target", id: target.id }}
                  onNavigate={() => onSelectTarget(target.id)}
                >
                  <strong>{target.target_name}</strong>
                  <small>{target.period_key ?? "No period"} · target #{target.id}</small>
                </EntityLink>
                <span className="entity-tree__states">
                  {hotTargetIds.has(target.id) && (
                    <span className="hierarchy-pill hierarchy-pill--hot">Hot</span>
                  )}
                  <span className={`hierarchy-pill hierarchy-pill--${tone(target.provisioning_state)}`}>
                    {label(target.provisioning_state)}
                  </span>
                  <span className="entity-tree__child-count">
                    {childIndexers.length} indexer{childIndexers.length === 1 ? "" : "s"}
                  </span>
                </span>
              </div>

              <div className="entity-tree__children">
                {visibleIndexers.map((indexer) => {
                  const attached = attachedIds.has(indexer.id);
                  return (
                    <EntityLink
                      className="entity-tree__indexer"
                      destination={{ kind: "indexer", id: indexer.id }}
                      key={indexer.id}
                      onNavigate={() => onSelectIndexer(indexer.id)}
                    >
                      <span className="entity-tree__connector" aria-hidden="true">◇</span>
                      <span className="entity-tree__identity">
                        <strong>{indexer.index_name}</strong>
                        <small>{indexer.role} · indexer #{indexer.id}</small>
                      </span>
                      <span className="entity-tree__states">
                        {hotIndexerIds.has(indexer.id) && (
                          <span className="hierarchy-pill hierarchy-pill--hot">Hot</span>
                        )}
                        <span className={`hierarchy-pill hierarchy-pill--${tone(indexer.publication_state)}`}>
                          {label(indexer.publication_state)}
                        </span>
                        <span className={`hierarchy-pill hierarchy-pill--${attached ? "good" : indexer.runtime_state === "ACTIVE" ? "warn" : "muted"}`}>
                          {attached
                            ? "Attached here"
                            : indexer.runtime_state === "ACTIVE"
                              ? "Not attached"
                              : "Inactive"}
                        </span>
                      </span>
                    </EntityLink>
                  );
                })}
                {childIndexers.length === 0 && (
                  <p className="entity-tree__empty">No indexers belong to this target.</p>
                )}
                {childIndexers.length > MAX_VISIBLE_INDEXERS && (
                  <p className="entity-tree__more">
                    +{childIndexers.length - MAX_VISIBLE_INDEXERS} more in the catalog explorer
                  </p>
                )}
              </div>
            </article>
          );
        })}

        {targets.length === 0 && (
          <div className="entity-tree__zero">
            <span aria-hidden="true">◎</span>
            <strong>No targets configured</strong>
            <p>Targets will appear here when they enter the system catalog.</p>
          </div>
        )}

        {orphanIndexers.length > 0 && (
          <article className="entity-tree__orphans" role="status">
            <strong>{orphanIndexers.length} indexers reference missing targets</strong>
            <p>Open the catalog explorer below to inspect the affected records.</p>
          </article>
        )}
      </div>

      {targets.length > MAX_VISIBLE_TARGETS && (
        <p className="system-hierarchy__truncated">
          Showing {MAX_VISIBLE_TARGETS} of {targets.length} targets. Use the
          catalog explorer for filtering and pagination.
        </p>
      )}
    </section>
  );
}

function tone(value: string): "good" | "warn" | "muted" {
  if (["READY", "PUBLISHED", "ACTIVE"].includes(value)) {
    return "good";
  }
  if (["FAILED", "DELETING", "NON_ACTIVE"].includes(value)) {
    return "warn";
  }
  return "muted";
}

function label(value: string): string {
  return value.replaceAll("_", " ").toLowerCase();
}
