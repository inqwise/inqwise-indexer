import type {
  IndexerDefinition,
  Target,
  TargetDefinition,
} from "../api/indexer-api";
import EntityLink from "./EntityLink";

type DefinitionService = {
  name: "targets" | "indexers";
  label: string;
  state: "checking" | "online" | "degraded";
  error: string | null;
};

type Props = {
  indexerDefinitions: IndexerDefinition[];
  onSelectTarget: (id: number) => void;
  services: DefinitionService[];
  targetDefinitions: TargetDefinition[];
  targets: Target[];
};

const MAX_DEFINITIONS = 24;
const MAX_CONFIGURATION_KEYS = 12;
const MAX_KEY_LENGTH = 80;
const SENSITIVE_KEY = /(password|secret|token|credential|private|api[_-]?key)/i;

export default function DefinitionsView({
  indexerDefinitions,
  onSelectTarget,
  services,
  targetDefinitions,
  targets,
}: Props) {
  const visibleTargets = [...targetDefinitions]
    .sort((left, right) => left.target_name.localeCompare(right.target_name))
    .slice(0, MAX_DEFINITIONS);
  const visibleIndexers = [...indexerDefinitions]
    .sort((left, right) => left.name.localeCompare(right.name))
    .slice(0, MAX_DEFINITIONS);

  return (
    <section
      aria-label="Definitions and capabilities"
      className="definitions-view"
      id="definitions"
    >
      <div className="definitions-view__header">
        <div>
          <span className="eyebrow">Loaded configuration</span>
          <h3>Definitions &amp; capabilities</h3>
          <p>
            Read-only provider snapshots describing what this node can
            provision. Definitions are configuration, not catalog entities.
          </p>
        </div>
        <span className="definitions-view__count">
          {targetDefinitions.length + indexerDefinitions.length} loaded
        </span>
      </div>

      <div aria-label="Definition services" className="definitions-services">
        {services.map((service) => (
          <article
            className={`definitions-service definitions-service--${service.state}`}
            key={service.name}
          >
            <span aria-hidden="true" />
            <div>
              <strong>{service.label}</strong>
              <small>
                {service.state === "checking"
                  ? "Loading"
                  : service.state === "online"
                    ? "Available"
                    : service.error ?? "Unavailable"}
              </small>
            </div>
          </article>
        ))}
      </div>

      <div className="definitions-grid">
        <section className="definitions-block" aria-labelledby="target-definitions-title">
          <div className="definitions-block__header">
            <div>
              <span className="eyebrow">Routing behavior</span>
              <h4 id="target-definitions-title">Target definitions</h4>
            </div>
            <span>{targetDefinitions.length}</span>
          </div>
          {visibleTargets.length === 0 ? (
            <p className="definition-empty">
              {serviceUnavailable(services, "targets")
                ? "Target definitions are unavailable."
                : "No target definitions are loaded."}
            </p>
          ) : (
            <div className="definition-list">
              {visibleTargets.map((definition) => {
                const matches = targets.filter(
                  (target) => target.target_name === definition.target_name,
                );
                return (
                  <article key={definition.target_name}>
                    <div className="definition-card__title">
                      <strong>{definition.target_name}</strong>
                      <span>{formatValue(definition.period_strategy)}</span>
                    </div>
                    <dl>
                      <Fact
                        label="Provision on write"
                        value={yesNo(definition.auto_provision_on_write)}
                      />
                      <Fact
                        label="Publish on write"
                        value={yesNo(definition.auto_publish_on_write)}
                      />
                    </dl>
                    <div className="definition-card__relationship">
                      {matches.length === 1 ? (
                        <EntityLink
                          destination={{ kind: "target", id: matches[0].id }}
                          onNavigate={() => onSelectTarget(matches[0].id)}
                        >
                          Open catalog target #{matches[0].id}
                        </EntityLink>
                      ) : (
                        <span>
                          {matches.length === 0
                            ? "No catalog targets"
                            : `${matches.length} catalog targets · no single link`}
                        </span>
                      )}
                    </div>
                  </article>
                );
              })}
            </div>
          )}
          {targetDefinitions.length > MAX_DEFINITIONS && (
            <p className="definition-truncated">
              Showing the first {MAX_DEFINITIONS} target definitions.
            </p>
          )}
        </section>

        <section className="definitions-block" aria-labelledby="indexer-definitions-title">
          <div className="definitions-block__header">
            <div>
              <span className="eyebrow">Resource shape</span>
              <h4 id="indexer-definitions-title">Indexer definitions</h4>
            </div>
            <span>{indexerDefinitions.length}</span>
          </div>
          {visibleIndexers.length === 0 ? (
            <p className="definition-empty">
              {serviceUnavailable(services, "indexers")
                ? "Indexer definitions are unavailable."
                : "No indexer definitions are loaded."}
            </p>
          ) : (
            <div className="definition-list">
              {visibleIndexers.map((definition) => (
                <article key={definition.name}>
                  <div className="definition-card__title">
                    <strong>{definition.name}</strong>
                    <span>
                      {definition.index.schema_name} · {definition.index.schema_version}
                    </span>
                  </div>
                  <ConfigurationKeys
                    label="Index settings"
                    value={definition.index.settings}
                  />
                  <ConfigurationKeys
                    label="Mapping fields"
                    value={definition.index.mappings}
                  />
                  <ConfigurationKeys
                    label="Queue settings"
                    value={definition.queue.settings}
                  />
                  <p className="definition-card__note">
                    Catalog indexers do not expose a definition identity, so no
                    relationship is inferred from names or schema values.
                  </p>
                </article>
              ))}
            </div>
          )}
          {indexerDefinitions.length > MAX_DEFINITIONS && (
            <p className="definition-truncated">
              Showing the first {MAX_DEFINITIONS} indexer definitions.
            </p>
          )}
        </section>
      </div>
    </section>
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

function ConfigurationKeys({
  label,
  value,
}: {
  label: string;
  value: Record<string, unknown>;
}) {
  const keys = Object.keys(value)
    .filter((key) => !SENSITIVE_KEY.test(key))
    .sort((left, right) => left.localeCompare(right))
    .slice(0, MAX_CONFIGURATION_KEYS)
    .map((key) => key.slice(0, MAX_KEY_LENGTH));
  return (
    <div className="configuration-keys">
      <span>{label}</span>
      <strong>{keys.length === 0 ? "No configured keys" : keys.join(" · ")}</strong>
    </div>
  );
}

function serviceUnavailable(
  services: DefinitionService[],
  name: DefinitionService["name"],
): boolean {
  return services.find((service) => service.name === name)?.state === "degraded";
}

function yesNo(value: boolean): string {
  return value ? "Yes" : "No";
}

function formatValue(value: string): string {
  return value.replaceAll("_", " ").toLowerCase();
}
