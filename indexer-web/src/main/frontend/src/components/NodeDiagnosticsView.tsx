import type {
  Indexer,
  InfrastructureStatus,
  InvalidRouteList,
  NodeStatus,
  Target,
  TargetInvalidationList,
} from "../api/indexer-api";
import EntityLink from "./EntityLink";

const MAX_NODE_SERVICES = 32;
const MAX_ROUTING_ITEMS = 12;
const MAX_INFRASTRUCTURE_ITEMS = 40;
const MAX_DETAILS_PER_ITEM = 8;
const MAX_TEXT = 240;
const SENSITIVE_DETAIL = /(password|secret|token|credential|private|api[_-]?key)/i;

export type DiagnosticServiceState = {
  name: string;
  label: string;
  state: "checking" | "online" | "degraded";
  error: string | null;
};

export default function NodeDiagnosticsView({
  indexers,
  infrastructure,
  invalidRoutes,
  node,
  onSelectIndexer,
  onSelectTarget,
  services,
  targetInvalidations,
  targets,
}: {
  indexers: Indexer[];
  infrastructure: InfrastructureStatus | null;
  invalidRoutes: InvalidRouteList | null;
  node: NodeStatus | null;
  onSelectIndexer: (id: number) => void;
  onSelectTarget: (id: number) => void;
  services: DiagnosticServiceState[];
  targetInvalidations: TargetInvalidationList | null;
  targets: Target[];
}) {
  const targetsById = new Map(targets.map((target) => [target.id, target] as const));
  const indexersById = new Map(
    indexers.map((indexer) => [indexer.id, indexer] as const),
  );
  const degraded = services.filter((service) => service.state === "degraded");
  const nodeServices = [...(node?.services ?? [])]
    .sort((left, right) =>
      left.group.localeCompare(right.group) || left.name.localeCompare(right.name),
    )
    .slice(0, MAX_NODE_SERVICES);
  const infrastructureItems = [...(infrastructure?.items ?? [])]
    .sort((left, right) =>
      left.category.localeCompare(right.category) || left.name.localeCompare(right.name),
    )
    .slice(0, MAX_INFRASTRUCTURE_ITEMS);

  return (
    <section
      aria-label="Node diagnostics"
      className="diagnostics-view"
      id="diagnostics"
    >
      <div className="diagnostics-view__header">
        <div>
          <span className="eyebrow">Node internals</span>
          <h3>Node diagnostics</h3>
          <p>
            Read-only lifecycle, composition, routing-memory, and infrastructure
            facts from the current node.
          </p>
        </div>
        <span className={`diagnostics-health${degraded.length > 0 ? " diagnostics-health--warn" : ""}`}>
          {degraded.length > 0 ? `${degraded.length} unavailable` : "All available"}
        </span>
      </div>

      <div className="diagnostics-services" aria-label="Diagnostic services">
        {services.map((service) => (
          <article className={`diagnostics-service diagnostics-service--${service.state}`} key={service.name}>
            <span aria-hidden="true" />
            <div>
              <strong>{service.label}</strong>
              <small>
                {service.state === "checking"
                  ? "Loading"
                  : service.state === "online"
                    ? "Available"
                    : bounded(service.error ?? "Unavailable")}
              </small>
            </div>
          </article>
        ))}
      </div>

      <div className="diagnostics-grid">
        <section className="diagnostics-block diagnostics-block--node">
          <BlockHeader eyebrow="Lifecycle" title="Node state" />
          {node ? (
            <>
              <div className="node-facts">
                <Fact label="Started" value={node.started ? "Yes" : "No"} warn={!node.started} />
                <Fact label="Ready" value={node.ready ? "Yes" : "No"} warn={!node.ready} />
                <Fact label="Recovery only" value={node.recovery_only ? "Yes" : "No"} warn={node.recovery_only} />
                <Fact label="Stopping" value={node.stopping ? "Yes" : "No"} warn={node.stopping} />
                <Fact label="Mode" value={node.clustered ? "Clustered" : "Standalone"} />
                <Fact label="Deployments" value={String(node.deployment_count)} />
                <Fact
                  label="Plane split"
                  value={`${node.control_plane_deployments} control · ${node.data_plane_deployments} data · ${node.infrastructure_deployments} infrastructure`}
                />
                <Fact label="Lifecycle events" value={bounded(node.lifecycle_event_namespace)} />
                <Fact label="Invalidation provider" value={bounded(node.target_invalidation_provider)} />
                <Fact label="Invalidation namespace" value={bounded(node.target_invalidation_namespace)} />
                <Fact label="Invalidation capacity" value={node.target_invalidation_max_targets.toLocaleString()} />
              </div>
              <div className="node-services-table">
                <div className="node-services-table__head">
                  <span>Service</span><span>Group</span><span>Instances</span>
                </div>
                {nodeServices.map((service) => (
                  <div key={`${service.group}-${service.name}`}>
                    <span>{service.name}</span>
                    <span>{service.group}</span>
                    <span className={service.enabled && service.deployed_instances !== service.configured_instances ? "is-warn" : ""}>
                      {service.enabled
                        ? `${service.deployed_instances}/${service.configured_instances}`
                        : "disabled"}
                    </span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <Unavailable state={services.find((service) => service.name === "node")} />
          )}
        </section>

        <section className="diagnostics-block">
          <BlockHeader eyebrow="Routing memory" title="Invalid routes" />
          {invalidRoutes ? (
            invalidRoutes.invalid_routes.length === 0 ? (
              <Empty>There are no cached invalid routes.</Empty>
            ) : (
              <div className="diagnostic-list">
                {invalidRoutes.invalid_routes.slice(0, MAX_ROUTING_ITEMS).map((route, index) => {
                  const destination = routeDestination(
                    route.signature,
                    targets,
                    targetsById,
                    indexersById,
                  );
                  return (
                    <article key={`${route.last_seen_at}-${index}`}>
                      <div>
                        <span>{route.signature.action_type.replaceAll("_", " ")}</span>
                        <strong>{bounded(route.reason)}</strong>
                        <small>
                          {route.count.toLocaleString()} occurrence{route.count === 1 ? "" : "s"}
                          {route.signature.period_key ? ` · ${bounded(route.signature.period_key)}` : ""}
                          {route.signature.index_name ? ` · ${bounded(route.signature.index_name)}` : ""}
                        </small>
                      </div>
                      <DiagnosticDestination
                        destination={destination}
                        onSelectIndexer={onSelectIndexer}
                        onSelectTarget={onSelectTarget}
                      />
                    </article>
                  );
                })}
                {(invalidRoutes.truncated || invalidRoutes.invalid_routes.length > MAX_ROUTING_ITEMS) && (
                  <p className="diagnostic-truncated">Showing a bounded routing sample.</p>
                )}
              </div>
            )
          ) : (
            <Unavailable state={services.find((service) => service.name === "invalidRoutes")} />
          )}
        </section>

        <section className="diagnostics-block">
          <BlockHeader eyebrow="Routing memory" title="Target invalidations" />
          {targetInvalidations ? (
            targetInvalidations.target_invalidations.length === 0 ? (
              <Empty>There are no pending target invalidations.</Empty>
            ) : (
              <div className="diagnostic-list">
                {targetInvalidations.target_invalidations
                  .slice(0, MAX_ROUTING_ITEMS)
                  .map((invalidation) => {
                    const target = targetsById.get(invalidation.target_id);
                    return (
                      <article key={`${invalidation.target_id}-${invalidation.version}`}>
                        <div>
                          <span>Target invalidation</span>
                          <strong>{target?.target_name ?? `Missing target #${invalidation.target_id}`}</strong>
                          <small>Version {invalidation.version} · expires {formatDate(invalidation.expires_at)}</small>
                        </div>
                        {target ? (
                          <EntityLink
                            destination={{ kind: "target", id: target.id }}
                            onNavigate={() => onSelectTarget(target.id)}
                          >
                            Inspect
                          </EntityLink>
                        ) : (
                          <span className="diagnostic-reference-missing">Unresolved</span>
                        )}
                      </article>
                    );
                  })}
                {(targetInvalidations.truncated ||
                  targetInvalidations.target_invalidations.length > MAX_ROUTING_ITEMS) && (
                  <p className="diagnostic-truncated">Showing a bounded invalidation sample.</p>
                )}
              </div>
            )
          ) : (
            <Unavailable state={services.find((service) => service.name === "targetInvalidations")} />
          )}
        </section>

        <section className="diagnostics-block diagnostics-block--infrastructure">
          <BlockHeader eyebrow="Composition" title="Infrastructure adapters" />
          {infrastructure ? (
            infrastructureItems.length === 0 ? (
              <Empty>No infrastructure facts are available.</Empty>
            ) : (
              <div className="infrastructure-list">
                {infrastructureItems.map((item) => (
                  <article key={`${item.category}-${item.name}`}>
                    <span>{item.category}</span>
                    <strong>{item.name}</strong>
                    <small>{bounded(item.implementation)}</small>
                    {safeDetails(item.details).length > 0 && (
                      <dl>
                        {safeDetails(item.details).map(([name, value]) => (
                          <div key={name}>
                            <dt>{name.replaceAll("_", " ")}</dt>
                            <dd>{value}</dd>
                          </div>
                        ))}
                      </dl>
                    )}
                  </article>
                ))}
              </div>
            )
          ) : (
            <Unavailable state={services.find((service) => service.name === "infrastructure")} />
          )}
        </section>
      </div>
    </section>
  );
}

function BlockHeader({ eyebrow, title }: { eyebrow: string; title: string }) {
  return (
    <header className="diagnostics-block__header">
      <span className="eyebrow">{eyebrow}</span>
      <h4>{title}</h4>
    </header>
  );
}

function Fact({ label, value, warn = false }: { label: string; value: string; warn?: boolean }) {
  return (
    <div className={warn ? "is-warn" : undefined}>
      <span>{label}</span><strong>{value}</strong>
    </div>
  );
}

function Empty({ children }: { children: string }) {
  return <p className="diagnostic-empty">{children}</p>;
}

function Unavailable({ state }: { state?: DiagnosticServiceState }) {
  return (
    <p className="diagnostic-empty diagnostic-empty--warn">
      {state?.state === "checking" ? "Loading diagnostics…" : bounded(state?.error ?? "Diagnostics unavailable")}
    </p>
  );
}

type RouteDestination =
  | { kind: "target"; id: number; label: string }
  | { kind: "indexer"; id: number; label: string }
  | { kind: "unresolved"; label: string }
  | null;

function routeDestination(
  signature: InvalidRouteList["invalid_routes"][number]["signature"],
  targets: Target[],
  targetsById: Map<number, Target>,
  indexersById: Map<number, Indexer>,
): RouteDestination {
  if (signature.indexer_id !== null && signature.indexer_id !== undefined) {
    const indexer = indexersById.get(signature.indexer_id);
    return indexer
      ? { kind: "indexer", id: indexer.id, label: indexer.index_name }
      : { kind: "unresolved", label: `Missing indexer #${signature.indexer_id}` };
  }
  if (signature.target_id !== null && signature.target_id !== undefined) {
    const target = targetsById.get(signature.target_id);
    return target
      ? { kind: "target", id: target.id, label: target.target_name }
      : { kind: "unresolved", label: `Missing target #${signature.target_id}` };
  }
  if (signature.target_name) {
    const matches = targets.filter(
      (target) =>
        target.target_name === signature.target_name &&
        (!signature.period_key || target.period_key === signature.period_key),
    );
    if (matches.length === 1) {
      return {
        kind: "target",
        id: matches[0].id,
        label: matches[0].target_name,
      };
    }
    return {
      kind: "unresolved",
      label: matches.length > 1
        ? `Ambiguous target ${signature.target_name}`
        : `Unresolved target ${signature.target_name}`,
    };
  }
  return null;
}

function DiagnosticDestination({
  destination,
  onSelectIndexer,
  onSelectTarget,
}: {
  destination: RouteDestination;
  onSelectIndexer: (id: number) => void;
  onSelectTarget: (id: number) => void;
}) {
  if (!destination) {
    return null;
  }
  if (destination.kind === "unresolved") {
    return <span className="diagnostic-reference-missing">{destination.label}</span>;
  }
  return (
    <EntityLink
      destination={{ kind: destination.kind, id: destination.id }}
      onNavigate={() =>
        destination.kind === "target"
          ? onSelectTarget(destination.id)
          : onSelectIndexer(destination.id)
      }
    >
      {destination.label}
    </EntityLink>
  );
}

function safeDetails(details: Record<string, unknown>): [string, string][] {
  return Object.entries(details)
    .filter(([name, value]) =>
      !SENSITIVE_DETAIL.test(name) &&
      (value === null || ["string", "number", "boolean"].includes(typeof value)),
    )
    .sort(([left], [right]) => left.localeCompare(right))
    .slice(0, MAX_DETAILS_PER_ITEM)
    .map(([name, value]) => [name, bounded(value === null ? "null" : String(value))]);
}

function bounded(value: string): string {
  return value.length > MAX_TEXT ? `${value.slice(0, MAX_TEXT)}…` : value;
}

function formatDate(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "unknown" : date.toLocaleString();
}
