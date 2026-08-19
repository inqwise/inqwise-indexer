import { useEffect, useState } from "react";
import type { ReactNode } from "react";

import type {
  HotTarget,
  Indexer,
  RuntimeIndexer,
  Target,
} from "../api/indexer-api";
import EntityLink from "./EntityLink";

type CatalogDetailPanelProps = {
  hotRoutingDiagnostic: {
    state: "checking" | "online" | "degraded";
    error: string | null;
  };
  hotTarget: HotTarget | null;
  target: Target | null;
  indexer: Indexer | null;
  indexerTarget: Target | null;
  relatedIndexers: Indexer[];
  runtimeIndexer: RuntimeIndexer | null;
  onClose: () => void;
  onIndexerDelete: (indexer: Indexer) => Promise<void>;
  onIndexerSelect: (id: number) => void;
  onQueueReset: (indexer: Indexer) => Promise<void>;
  onTargetRecovery: (target: Target) => Promise<void>;
  onTargetSelect: (id: number) => void;
  onRuntimeReconcile: (indexer: Indexer) => Promise<void>;
  onRuntimeStateChange: (
    indexer: Indexer,
    desiredState: Indexer["runtime_state"],
  ) => Promise<void>;
};

export default function CatalogDetailPanel({
  hotRoutingDiagnostic,
  hotTarget,
  target,
  indexer,
  indexerTarget,
  relatedIndexers,
  runtimeIndexer,
  onClose,
  onIndexerDelete,
  onIndexerSelect,
  onQueueReset,
  onTargetRecovery,
  onTargetSelect,
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
  const [queueResetConfirming, setQueueResetConfirming] = useState(false);
  const [queueResetPending, setQueueResetPending] = useState(false);
  const [queueResetError, setQueueResetError] = useState<string | null>(null);
  const [deleteConfirming, setDeleteConfirming] = useState(false);
  const [deleteConfirmationText, setDeleteConfirmationText] = useState("");
  const [deletePending, setDeletePending] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

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
    setQueueResetConfirming(false);
    setQueueResetPending(false);
    setQueueResetError(null);
    setDeleteConfirming(false);
    setDeleteConfirmationText("");
    setDeletePending(false);
    setDeleteError(null);
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
          hotRoutingDiagnostic={hotRoutingDiagnostic}
          hotTarget={hotTarget}
          relatedIndexers={relatedIndexers}
          target={target}
          onIndexerSelect={onIndexerSelect}
        />
      ) : (
        indexer && (
          <IndexerDetails
            deleteConfirmationText={deleteConfirmationText}
            deleteConfirming={deleteConfirming}
            deleteError={deleteError}
            deletePending={deletePending}
            indexer={indexer}
            indexerTarget={indexerTarget}
            mutationError={mutationError}
            onDelete={async () => {
              setDeletePending(true);
              setDeleteError(null);
              try {
                await onIndexerDelete(indexer);
                onClose();
              } catch (error) {
                setDeleteError(
                  error instanceof Error
                    ? error.message
                    : "Indexer deletion request failed",
                );
              } finally {
                setDeletePending(false);
              }
            }}
            onQueueReset={async () => {
              setQueueResetPending(true);
              setQueueResetError(null);
              try {
                await onQueueReset(indexer);
                setQueueResetConfirming(false);
              } catch (error) {
                setQueueResetError(
                  error instanceof Error
                    ? error.message
                    : "Indexer queue reset failed",
                );
              } finally {
                setQueueResetPending(false);
              }
            }}
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
            onTargetSelect={onTargetSelect}
            pendingRuntimeState={pendingRuntimeState}
            queueResetConfirming={queueResetConfirming}
            queueResetError={queueResetError}
            queueResetPending={queueResetPending}
            runtimeReconcileError={runtimeReconcileError}
            runtimeReconcilePending={runtimeReconcilePending}
            runtimeIndexer={runtimeIndexer}
            setDeleteConfirmationText={setDeleteConfirmationText}
            setDeleteConfirming={setDeleteConfirming}
            setDeleteError={setDeleteError}
            setQueueResetConfirming={setQueueResetConfirming}
            setQueueResetError={setQueueResetError}
          />
        )
      )}
    </aside>
  );
}

function TargetDetails({
  hotRoutingDiagnostic,
  hotTarget,
  target,
  relatedIndexers,
  pending,
  mutationError,
  onIndexerSelect,
  onRecover,
}: {
  hotRoutingDiagnostic: CatalogDetailPanelProps["hotRoutingDiagnostic"];
  hotTarget: HotTarget | null;
  target: Target;
  relatedIndexers: Indexer[];
  pending: boolean;
  mutationError: string | null;
  onIndexerSelect: (id: number) => void;
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
        <Detail
          label="Hot routing"
          value={hotRoutingLabel(hotRoutingDiagnostic.state, hotTarget)}
        />
      </div>
      <section className="related-entities" aria-labelledby="hot-indexers-title">
        <div className="related-entities__header">
          <div>
            <span className="eyebrow">Node-local routing</span>
            <h3 id="hot-indexers-title">Hot indexers</h3>
          </div>
          <span>
            {hotRoutingDiagnostic.state === "online" && hotTarget
              ? `${hotTarget.hot_indexer_ids.length}${hotTarget.indexers_truncated ? "+" : ""}`
              : "—"}
          </span>
        </div>
        {hotRoutingDiagnostic.state === "checking" ? (
          <p>Checking the node-local hot routing view.</p>
        ) : hotRoutingDiagnostic.state === "degraded" ? (
          <p>Hot routing state is unavailable from this node.</p>
        ) : !hotTarget ? (
          <p>This target is not loaded in the node-local hot routing view.</p>
        ) : hotTarget.hot_indexer_ids.length === 0 ? (
          <p>This target is loaded, but it has no hot indexers.</p>
        ) : (
          <div className="related-entities__list">
            {hotTarget.hot_indexer_ids.map((indexerId) => {
              const indexer = relatedIndexers.find(
                (candidate) => candidate.id === indexerId,
              );
              return indexer ? (
                <EntityLink
                  destination={{ kind: "indexer", id: indexer.id }}
                  key={indexer.id}
                  onNavigate={() => onIndexerSelect(indexer.id)}
                >
                  <span>
                    <strong>{indexer.index_name}</strong>
                    <small>#{indexer.id} · actual hot writer</small>
                  </span>
                  <span aria-hidden="true">→</span>
                </EntityLink>
              ) : (
                <span className="entity-reference-missing" key={indexerId}>
                  Hot indexer #{indexerId} is absent from the catalog response
                </span>
              );
            })}
          </div>
        )}
      </section>
      <section className="related-entities" aria-labelledby="related-indexers-title">
        <div className="related-entities__header">
          <div>
            <span className="eyebrow">Relationships</span>
            <h3 id="related-indexers-title">Related indexers</h3>
          </div>
          <span>{relatedIndexers.length}</span>
        </div>
        {relatedIndexers.length === 0 ? (
          <p>No indexers reference this target.</p>
        ) : (
          <div className="related-entities__list">
            {relatedIndexers.slice(0, 8).map((indexer) => (
              <EntityLink
                destination={{ kind: "indexer", id: indexer.id }}
                key={indexer.id}
                onNavigate={() => onIndexerSelect(indexer.id)}
              >
                <span>
                  <strong>{indexer.index_name}</strong>
                  <small>#{indexer.id} · {humanize(indexer.runtime_state)}</small>
                </span>
                <span aria-hidden="true">→</span>
              </EntityLink>
            ))}
            {relatedIndexers.length > 8 && (
              <small>{relatedIndexers.length - 8} additional indexers</small>
            )}
          </div>
        )}
      </section>
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

function hotRoutingLabel(
  state: CatalogDetailPanelProps["hotRoutingDiagnostic"]["state"],
  hotTarget: HotTarget | null,
): string {
  if (state === "checking") {
    return "Checking";
  }
  if (state === "degraded") {
    return "Unavailable";
  }
  if (!hotTarget) {
    return "Not loaded";
  }
  if (hotTarget.hot_indexer_ids.length === 0) {
    return "Loaded · no hot indexers";
  }
  return `Hot · ${hotTarget.hot_indexer_ids.length}${hotTarget.indexers_truncated ? "+" : ""} indexers`;
}

function IndexerDetails({
  deleteConfirmationText,
  deleteConfirming,
  deletePending,
  deleteError,
  indexer,
  indexerTarget,
  runtimeIndexer,
  pendingRuntimeState,
  mutationError,
  queueResetConfirming,
  queueResetPending,
  queueResetError,
  runtimeReconcilePending,
  runtimeReconcileError,
  setDeleteConfirmationText,
  setDeleteConfirming,
  setDeleteError,
  setQueueResetConfirming,
  setQueueResetError,
  onDelete,
  onQueueReset,
  onRuntimeReconcile,
  onRuntimeStateChange,
  onTargetSelect,
}: {
  deleteConfirmationText: string;
  deleteConfirming: boolean;
  deletePending: boolean;
  deleteError: string | null;
  indexer: Indexer;
  indexerTarget: Target | null;
  runtimeIndexer: RuntimeIndexer | null;
  pendingRuntimeState: Indexer["runtime_state"] | null;
  mutationError: string | null;
  queueResetConfirming: boolean;
  queueResetPending: boolean;
  queueResetError: string | null;
  runtimeReconcilePending: boolean;
  runtimeReconcileError: string | null;
  setDeleteConfirmationText: (value: string) => void;
  setDeleteConfirming: (confirming: boolean) => void;
  setDeleteError: (error: string | null) => void;
  setQueueResetConfirming: (confirming: boolean) => void;
  setQueueResetError: (error: string | null) => void;
  onDelete: () => Promise<void>;
  onQueueReset: () => Promise<void>;
  onRuntimeReconcile: () => Promise<void>;
  onRuntimeStateChange: (
    desiredState: Indexer["runtime_state"],
  ) => Promise<void>;
  onTargetSelect: (id: number) => void;
}) {
  const desiredState =
    indexer.runtime_state === "ACTIVE" ? "NON_ACTIVE" : "ACTIVE";
  const canChangeRuntime =
    indexer.provisioning_state === "READY" &&
    indexer.mutation_state !== "DELETING";
  const canResetQueue = canChangeRuntime && indexer.queue_name !== null;
  const queueResetFlowActive = queueResetConfirming || queueResetPending;
  const canDelete = indexer.mutation_state !== "DELETING";
  const deleteFlowActive = deleteConfirming || deletePending;

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
          value={indexerTarget ? (
            <EntityLink
              destination={{ kind: "target", id: indexer.target_id }}
              onNavigate={() => onTargetSelect(indexer.target_id)}
            >
              {indexer.target_name} (#{indexer.target_id})
            </EntityLink>
          ) : (
            <span className="entity-reference-missing">
              Missing target #{indexer.target_id}
            </span>
          )}
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
            runtimeReconcilePending ||
            queueResetFlowActive ||
            deleteFlowActive
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
            pendingRuntimeState !== null ||
            queueResetFlowActive ||
            deleteFlowActive
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
      <section
        className="lifecycle-control lifecycle-control--danger"
        aria-labelledby="queue-reset-title"
      >
        <div>
          <span className="eyebrow">Operational troubleshooting</span>
          <h3 id="queue-reset-title">Reset indexer queue</h3>
          <p>
            Future writes move from{" "}
            <strong>{indexer.queue_name ?? "no assigned queue"}</strong> to a
            new versioned queue. Cleanup of the retired queue continues
            asynchronously.
          </p>
        </div>
        {!queueResetConfirming ? (
          <button
            className="lifecycle-button"
            disabled={
              !canResetQueue ||
              queueResetPending ||
              pendingRuntimeState !== null ||
              runtimeReconcilePending ||
              deleteFlowActive
            }
            onClick={() => {
              setQueueResetError(null);
              setQueueResetConfirming(true);
            }}
            type="button"
          >
            Review queue reset
          </button>
        ) : (
          <div className="queue-reset-confirmation" role="group">
            <p>
              Confirm queue reset for <strong>{indexer.index_name}</strong> at
              catalog version <strong>v{indexer.version}</strong>. Old in-flight
              items are not synchronously guaranteed to stop.
            </p>
            <div className="queue-reset-confirmation__actions">
              <button
                className="lifecycle-button lifecycle-button--secondary"
                disabled={queueResetPending}
                onClick={() => {
                  setQueueResetConfirming(false);
                  setQueueResetError(null);
                }}
                type="button"
              >
                Cancel
              </button>
              <button
                className="lifecycle-button lifecycle-button--danger"
                disabled={queueResetPending}
                onClick={() => void onQueueReset()}
                type="button"
              >
                {queueResetPending ? "Resetting…" : "Confirm queue reset"}
              </button>
            </div>
          </div>
        )}
        {!canResetQueue && (
          <p className="lifecycle-control__hint">
            Queue reset requires a ready, non-deleting indexer with an assigned
            queue.
          </p>
        )}
        {queueResetError && (
          <p className="lifecycle-control__error" role="alert">
            {queueResetError}
          </p>
        )}
      </section>
      <section
        className="lifecycle-control lifecycle-control--delete"
        aria-labelledby="indexer-delete-title"
      >
        <div>
          <span className="eyebrow">Destructive operation</span>
          <h3 id="indexer-delete-title">Delete this indexer</h3>
          <p>
            Deletion immediately fences the indexer as non-active, then
            asynchronously removes its queue, any owned document index, and
            catalog metadata. This cannot be undone.
          </p>
        </div>
        {!deleteConfirming ? (
          <button
            className="lifecycle-button"
            disabled={
              !canDelete ||
              deletePending ||
              pendingRuntimeState !== null ||
              runtimeReconcilePending ||
              queueResetFlowActive
            }
            onClick={() => {
              setDeleteError(null);
              setDeleteConfirmationText("");
              setDeleteConfirming(true);
            }}
            type="button"
          >
            Review indexer deletion
          </button>
        ) : (
          <div className="delete-confirmation">
            <label htmlFor="delete-indexer-confirmation">
              Type <strong>{indexer.index_name}</strong> to confirm
            </label>
            <input
              aria-label="Type indexer name to confirm deletion"
              autoComplete="off"
              id="delete-indexer-confirmation"
              onChange={(event) =>
                setDeleteConfirmationText(event.currentTarget.value)
              }
              spellCheck={false}
              type="text"
              value={deleteConfirmationText}
            />
            <p>
              The request uses catalog version{" "}
              <strong>v{indexer.version}</strong>. Acceptance starts durable
              cleanup; it does not mean cleanup has already finished.
            </p>
            <div className="queue-reset-confirmation__actions">
              <button
                className="lifecycle-button lifecycle-button--secondary"
                disabled={deletePending}
                onClick={() => {
                  setDeleteConfirming(false);
                  setDeleteConfirmationText("");
                  setDeleteError(null);
                }}
                type="button"
              >
                Cancel
              </button>
              <button
                className="lifecycle-button lifecycle-button--danger"
                disabled={
                  deletePending ||
                  deleteConfirmationText !== indexer.index_name
                }
                onClick={() => void onDelete()}
                type="button"
              >
                {deletePending ? "Deleting…" : "Confirm indexer deletion"}
              </button>
            </div>
          </div>
        )}
        {!canDelete && (
          <p className="lifecycle-control__hint">
            This indexer is already deleting; durable cleanup is in progress.
          </p>
        )}
        {deleteError && (
          <p className="lifecycle-control__error" role="alert">
            {deleteError}
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
  value: ReactNode;
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
