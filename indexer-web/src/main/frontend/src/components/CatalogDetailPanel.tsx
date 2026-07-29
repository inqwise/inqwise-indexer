import { useEffect, useState } from "react";

import type {
  Indexer,
  RuntimeIndexer,
  Target,
} from "../api/indexer-api";

type CatalogDetailPanelProps = {
  target: Target | null;
  indexer: Indexer | null;
  runtimeIndexer: RuntimeIndexer | null;
  onClose: () => void;
  onTargetRecovery: (target: Target) => Promise<void>;
  onRuntimeReconcile: (indexer: Indexer) => Promise<void>;
  onRuntimeStateChange: (
    indexer: Indexer,
    desiredState: Indexer["runtime_state"],
  ) => Promise<void>;
};

export default function CatalogDetailPanel({
  target,
  indexer,
  runtimeIndexer,
  onClose,
  onTargetRecovery,
  onRuntimeReconcile,
  onRuntimeStateChange,
}: CatalogDetailPanelProps) {
  const [pendingRuntimeState, setPendingRuntimeState] = useState<
    Indexer["runtime_state"] | null
  >(null);
  const [mutationError, setMutationError] = useState<string | null>(null);
  const [targetRecoveryPending, setTargetRecoveryPending] = useState(false);
  const [targetMutationError, setTargetMutationError] = useState<string | null>(
    null,
  );
  const [runtimeReconcilePending, setRuntimeReconcilePending] = useState(false);
  const [runtimeReconcileError, setRuntimeReconcileError] = useState<
    string | null
  >(null);

  useEffect(() => {
    if (!target && !indexer) {
      return;
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        onClose();
      }
    };
    window.addEventListener("keydown", closeOnEscape);
    return () => window.removeEventListener("keydown", closeOnEscape);
  }, [indexer, onClose, target]);

  useEffect(() => {
    setPendingRuntimeState(null);
    setMutationError(null);
    setRuntimeReconcilePending(false);
    setRuntimeReconcileError(null);
  }, [indexer?.id]);

  useEffect(() => {
    setTargetRecoveryPending(false);
    setTargetMutationError(null);
  }, [target?.id]);

  if (!target && !indexer) {
    return null;
  }

  const title = target?.target_name ?? indexer?.index_name ?? "Catalog details";

  return (
    <aside
      className="detail-drawer"
      aria-labelledby="catalog-detail-title"
      data-testid="catalog-detail-panel"
    >
      <div className="detail-drawer__header">
        <div>
          <span className="eyebrow">
            {target ? "Target catalog" : "Indexer catalog"}
          </span>
          <h2 id="catalog-detail-title">{title}</h2>
        </div>
        <button
          aria-label="Close details"
          className="detail-drawer__close"
          onClick={onClose}
          type="button"
        >
          ×
        </button>
      </div>

      {target ? (
        <TargetDetails
          mutationError={targetMutationError}
          onRecover={async () => {
            setTargetRecoveryPending(true);
            setTargetMutationError(null);
            try {
              await onTargetRecovery(target);
            } catch (error) {
              setTargetMutationError(
                error instanceof Error
                  ? error.message
                  : "Target provisioning recovery failed",
              );
            } finally {
              setTargetRecoveryPending(false);
            }
          }}
          pending={targetRecoveryPending}
          target={target}
        />
      ) : (
        indexer && (
          <IndexerDetails
            indexer={indexer}
            mutationError={mutationError}
            onRuntimeReconcile={async () => {
              setRuntimeReconcilePending(true);
              setRuntimeReconcileError(null);
              try {
                await onRuntimeReconcile(indexer);
              } catch (error) {
                setRuntimeReconcileError(
                  error instanceof Error
                    ? error.message
                    : "Local runtime reconciliation failed",
                );
              } finally {
                setRuntimeReconcilePending(false);
              }
            }}
            onRuntimeStateChange={async (desiredState) => {
              setPendingRuntimeState(desiredState);
              setMutationError(null);
              try {
                await onRuntimeStateChange(indexer, desiredState);
              } catch (error) {
                setMutationError(
                  error instanceof Error
                    ? error.message
                    : "Indexer lifecycle request failed",
                );
              } finally {
                setPendingRuntimeState(null);
              }
            }}
            pendingRuntimeState={pendingRuntimeState}
            runtimeReconcileError={runtimeReconcileError}
            runtimeReconcilePending={runtimeReconcilePending}
            runtimeIndexer={runtimeIndexer}
          />
        )
      )}
    </aside>
  );
}

function TargetDetails({
  target,
  pending,
  mutationError,
  onRecover,
}: {
  target: Target;
  pending: boolean;
  mutationError: string | null;
  onRecover: () => Promise<void>;
}) {
  return (
    <>
      <div className="detail-drawer__summary">
        <span className="detail-drawer__glyph">
          {target.target_name.slice(0, 1).toUpperCase()}
        </span>
        <div>
          <strong>{target.target_name}</strong>
          <span>
            #{target.id} · v{target.version}
          </span>
        </div>
      </div>
      <div className="detail-grid">
        <Detail label="UID" value={target.uid} mono />
        <Detail label="Period key" value={target.period_key ?? "Not periodic"} />
        <Detail label="Lifecycle" value={humanize(target.status)} />
        <Detail
          label="Provisioning"
          value={humanize(target.provisioning_state)}
        />
        <Detail
          label="Period start"
          value={formatDateTime(target.period_start_inclusive)}
        />
        <Detail
          label="Period end"
          value={formatDateTime(target.period_end_exclusive)}
        />
        <Detail label="Created" value={formatDateTime(target.created_at)} />
        <Detail label="Updated" value={formatDateTime(target.updated_at)} />
      </div>
      {target.provisioning_state === "FAILED" && (
        <section
          className="lifecycle-control lifecycle-control--recovery"
          aria-labelledby="target-recovery-title"
        >
          <div>
            <span className="eyebrow">Provisioning recovery</span>
            <h3 id="target-recovery-title">Retry failed provisioning</h3>
            <p>
              The request uses catalog version{" "}
              <strong>v{target.version}</strong> and reloads the target before
              another recovery attempt.
            </p>
          </div>
          <button
            className="lifecycle-button"
            disabled={pending}
            onClick={() => void onRecover()}
            type="button"
          >
            {pending ? "Recovering…" : "Recover provisioning"}
          </button>
          {mutationError && (
            <p className="lifecycle-control__error" role="alert">
              {mutationError}
            </p>
          )}
        </section>
      )}
    </>
  );
}

function IndexerDetails({
  indexer,
  runtimeIndexer,
  pendingRuntimeState,
  mutationError,
  runtimeReconcilePending,
  runtimeReconcileError,
  onRuntimeReconcile,
  onRuntimeStateChange,
}: {
  indexer: Indexer;
  runtimeIndexer: RuntimeIndexer | null;
  pendingRuntimeState: Indexer["runtime_state"] | null;
  mutationError: string | null;
  runtimeReconcilePending: boolean;
  runtimeReconcileError: string | null;
  onRuntimeReconcile: () => Promise<void>;
  onRuntimeStateChange: (
    desiredState: Indexer["runtime_state"],
  ) => Promise<void>;
}) {
  const desiredState =
    indexer.runtime_state === "ACTIVE" ? "NON_ACTIVE" : "ACTIVE";
  const canChangeRuntime =
    indexer.provisioning_state === "READY" &&
    indexer.mutation_state !== "DELETING";

  return (
    <>
      <div className="detail-drawer__summary">
        <span className="detail-drawer__glyph detail-drawer__glyph--violet">
          ◇
        </span>
        <div>
          <strong>{indexer.index_name}</strong>
          <span>
            #{indexer.id} · v{indexer.version}
          </span>
        </div>
      </div>
      <div className="detail-grid">
        <Detail label="UID" value={indexer.uid} mono />
        <Detail
          label="Target"
          value={`${indexer.target_name} (#${indexer.target_id})`}
        />
        <Detail label="Queue" value={indexer.queue_name ?? "Not assigned"} mono />
        <Detail label="Type" value={humanize(indexer.type)} />
        <Detail label="Role" value={humanize(indexer.role)} />
        <Detail
          label="Index ownership"
          value={humanize(indexer.index_ownership)}
        />
        <Detail label="Catalog status" value={humanize(indexer.status)} />
        <Detail
          label="Provisioning"
          value={humanize(indexer.provisioning_state)}
        />
        <Detail label="Desired runtime" value={humanize(indexer.runtime_state)} />
        <Detail
          label="Attached locally"
          value={runtimeIndexer ? "Yes" : "No"}
        />
        <Detail
          label="Publication"
          value={humanize(indexer.publication_state)}
        />
        <Detail label="Mutations" value={humanize(indexer.mutation_state)} />
        <Detail label="Created" value={formatDateTime(indexer.created_at)} />
        <Detail label="Updated" value={formatDateTime(indexer.updated_at)} />
      </div>
      <section className="lifecycle-control" aria-labelledby="lifecycle-title">
        <div>
          <span className="eyebrow">Lifecycle</span>
          <h3 id="lifecycle-title">
            {desiredState === "ACTIVE"
              ? "Activate this indexer"
              : "Deactivate this indexer"}
          </h3>
          <p>
            The request uses catalog version <strong>v{indexer.version}</strong>{" "}
            and refreshes the catalog before another lifecycle change.
          </p>
        </div>
        <button
          className="lifecycle-button"
          disabled={
            !canChangeRuntime ||
            pendingRuntimeState !== null ||
            runtimeReconcilePending
          }
          onClick={() => void onRuntimeStateChange(desiredState)}
          type="button"
        >
          {pendingRuntimeState
            ? pendingRuntimeState === "ACTIVE"
              ? "Activating…"
              : "Deactivating…"
            : desiredState === "ACTIVE"
              ? "Activate indexer"
              : "Deactivate indexer"}
        </button>
        {!canChangeRuntime && (
          <p className="lifecycle-control__hint">
            Lifecycle changes require a ready indexer that is not deleting.
          </p>
        )}
        {mutationError && (
          <p className="lifecycle-control__error" role="alert">
            {mutationError}
          </p>
        )}
      </section>
      <section
        className="lifecycle-control lifecycle-control--runtime"
        aria-labelledby="runtime-reconcile-title"
      >
        <div>
          <span className="eyebrow">Local runtime</span>
          <h3 id="runtime-reconcile-title">Reconcile this indexer</h3>
          <p>
            Reload durable catalog state and converge this node&apos;s local
            runtime. This does not change the desired catalog state.
          </p>
        </div>
        <button
          className="lifecycle-button"
          disabled={
            !canChangeRuntime ||
            runtimeReconcilePending ||
            pendingRuntimeState !== null
          }
          onClick={() => void onRuntimeReconcile()}
          type="button"
        >
          {runtimeReconcilePending
            ? "Reconciling…"
            : "Reconcile local runtime"}
        </button>
        {!canChangeRuntime && (
          <p className="lifecycle-control__hint">
            Runtime reconciliation requires a ready indexer that is not
            deleting.
          </p>
        )}
        {runtimeReconcileError && (
          <p className="lifecycle-control__error" role="alert">
            {runtimeReconcileError}
          </p>
        )}
      </section>
    </>
  );
}

function Detail({
  label,
  value,
  mono = false,
}: {
  label: string;
  value: string;
  mono?: boolean;
}) {
  return (
    <div className="detail-field">
      <span>{label}</span>
      <strong className={mono ? "mono" : undefined}>{value}</strong>
    </div>
  );
}

function humanize(value: string): string {
  return value.replaceAll("_", " ").toLowerCase();
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return "Not set";
  }
  return new Date(value).toLocaleString();
}
