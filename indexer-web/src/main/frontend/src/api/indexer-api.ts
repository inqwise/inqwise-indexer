import createClient from "openapi-fetch";

import type {
  components as AdminComponents,
  paths as AdminPaths,
} from "../generated/admin-api";
import type {
  components as RuntimeComponents,
  paths as RuntimePaths,
} from "../generated/runtime-api";

export type Target = AdminComponents["schemas"]["AdminTargetView"];
export type Indexer = AdminComponents["schemas"]["AdminIndexerView"];
export type RuntimeIndexer =
  RuntimeComponents["schemas"]["RuntimeIndexerStatus"];
export type NodeStatus = AdminComponents["schemas"]["AdminNodeStatus"];
export type InfrastructureStatus =
  AdminComponents["schemas"]["AdminInfrastructureStatus"];
export type InvalidRouteList =
  AdminComponents["schemas"]["AdminInvalidRouteList"];
export type TargetInvalidationList =
  AdminComponents["schemas"]["AdminTargetInvalidationList"];
export type HotTargetList = AdminComponents["schemas"]["AdminHotTargetList"];
export type HotTarget = AdminComponents["schemas"]["AdminHotTargetView"];
export type TargetDefinition =
  AdminComponents["schemas"]["AdminTargetDefinitionView"];
export type IndexerDefinition =
  AdminComponents["schemas"]["AdminIndexerDefinitionView"];
export type OperationalMetrics = {
  acceptedPutActions: number;
  acceptedRemoveActions: number;
  rejectedActions: number;
  processedSucceeded: number;
  processedFailed: number;
  runtimeDesired: number;
  runtimeAttached: number;
  runtimeDrift: number;
  lifecyclePending: number;
  lifecycleSucceeded: number;
  lifecycleFailed: number;
  lifecycleRetrying: number;
  reports: ReportOperationalMetric[];
};

export type ReportOperationalMetric = {
  reportName: string;
  succeeded: number;
  invalid: number;
  failed: number;
  active: number;
  durationSeconds: number;
};

type PrometheusSample = {
  name: string;
  labels: Record<string, string>;
  value: number;
};

const adminClient = createClient<AdminPaths>({
  baseUrl: "/api/admin",
  headers: { accept: "application/json" },
});
const runtimeClient = createClient<RuntimePaths>({
  baseUrl: "/api/runtime",
  headers: { accept: "application/json" },
});

export async function isReady(signal: AbortSignal): Promise<boolean> {
  const response = await fetch("/api/health/health/ready", { signal });
  return response.ok;
}

export async function operationalMetrics(
  signal: AbortSignal,
): Promise<OperationalMetrics> {
  const response = await fetch("/api/metrics/metrics", {
    headers: { accept: "text/plain" },
    signal,
  });
  if (!response.ok) {
    throw new Error(`Metrics request failed with status ${response.status}`);
  }
  return parseOperationalMetrics(await response.text());
}

export async function listTargets(signal: AbortSignal): Promise<Target[]> {
  const { data, error, response } = await adminClient.GET("/admin/targets", {
    signal,
  });
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.targets;
}

export async function recoverTargetProvisioning(
  targetId: number,
  expectedVersion: number,
): Promise<Target> {
  const { data, error, response } = await adminClient.POST(
    "/admin/targets/{id}/recover-provisioning",
    {
      params: {
        path: { id: targetId },
        query: { expected_version: expectedVersion },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.target;
}

export async function listIndexers(signal: AbortSignal): Promise<Indexer[]> {
  const { data, error, response } = await adminClient.GET("/admin/indexers", {
    signal,
  });
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexers;
}

export async function listTargetDefinitions(
  signal: AbortSignal,
): Promise<TargetDefinition[]> {
  const { data, error, response } = await adminClient.GET(
    "/admin/definitions/targets",
    { signal },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.target_definitions;
}

export async function listIndexerDefinitions(
  signal: AbortSignal,
): Promise<IndexerDefinition[]> {
  const { data, error, response } = await adminClient.GET(
    "/admin/definitions/indexers",
    { signal },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexer_definitions;
}

export async function nodeStatus(signal: AbortSignal): Promise<NodeStatus> {
  const { data, error, response } = await adminClient.GET("/admin/node/status", {
    signal,
  });
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function recoverNode(): Promise<NodeStatus> {
  const { data, error, response } = await adminClient.POST(
    "/admin/node/recover",
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function infrastructureStatus(
  signal: AbortSignal,
): Promise<InfrastructureStatus> {
  const { data, error, response } = await adminClient.GET(
    "/admin/infrastructure/status",
    { signal },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function invalidRoutes(
  signal: AbortSignal,
): Promise<InvalidRouteList> {
  const { data, error, response } = await adminClient.GET(
    "/admin/routing/invalid-routes",
    {
      params: { query: { max: 50 } },
      signal,
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function targetInvalidations(
  signal: AbortSignal,
): Promise<TargetInvalidationList> {
  const { data, error, response } = await adminClient.GET(
    "/admin/routing/target-invalidations",
    {
      params: { query: { max: 50 } },
      signal,
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function hotTargets(
  signal: AbortSignal,
): Promise<HotTargetList> {
  const { data, error, response } = await adminClient.GET(
    "/admin/routing/hot-targets",
    {
      params: { query: { max: 100 } },
      signal,
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data;
}

export async function activateIndexer(
  indexerId: number,
  expectedVersion: number,
): Promise<Indexer> {
  const { data, error, response } = await adminClient.POST(
    "/admin/indexers/{id}/activate",
    {
      params: {
        path: { id: indexerId },
        query: { expected_version: expectedVersion },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexer;
}

export async function deactivateIndexer(
  indexerId: number,
  expectedVersion: number,
): Promise<Indexer> {
  const { data, error, response } = await adminClient.POST(
    "/admin/indexers/{id}/deactivate",
    {
      params: {
        path: { id: indexerId },
        query: { expected_version: expectedVersion },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexer;
}

export async function resetIndexerQueue(
  indexerId: number,
  expectedVersion: number,
): Promise<Indexer> {
  const { data, error, response } = await adminClient.POST(
    "/admin/indexers/{id}/reset-queue",
    {
      params: {
        path: { id: indexerId },
        query: { expected_version: expectedVersion },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexer;
}

export async function deleteIndexer(
  indexerId: number,
  expectedVersion: number,
): Promise<Indexer> {
  const { data, error, response } = await adminClient.DELETE(
    "/admin/indexers/{id}",
    {
      params: {
        path: { id: indexerId },
        query: { expected_version: expectedVersion },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexer;
}

export async function runtimeStatus(
  signal: AbortSignal,
): Promise<RuntimeIndexer[]> {
  const { data, error, response } = await runtimeClient.GET("/runtime/status", {
    signal,
  });
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.indexers;
}

export async function reconcileIndexer(indexerId: number): Promise<void> {
  const { data, error, response } = await runtimeClient.POST(
    "/runtime/indexers/{id}/reconcile",
    {
      params: {
        path: { id: indexerId },
      },
    },
  );
  if (!data) {
    throw requestError(response.status, error);
  }
}

function parseOperationalMetrics(source: string): OperationalMetrics {
  const samples = source
    .split("\n")
    .filter((line) => line && !line.startsWith("#"))
    .flatMap(parsePrometheusSample);
  const sum = (
    name: string,
    predicate: (sample: PrometheusSample) => boolean = () => true,
  ) =>
    samples
      .filter((sample) => sample.name === name && predicate(sample))
      .reduce((total, sample) => total + sample.value, 0);
  const labeled = (label: string, value: string) =>
    (sample: PrometheusSample) => sample.labels[label] === value;
  const intake = (actionType: string, outcome: string) =>
    sum(
      "inqwise_indexer_action_intake_total",
      (sample) =>
        sample.labels.action_type === actionType &&
        sample.labels.outcome === outcome,
    );
  const processing = (outcome: string) =>
    sum(
      "inqwise_indexer_action_processing_seconds_count",
      labeled("outcome", outcome),
    );
  const lifecycle = (outcome: string) =>
    sum(
      "inqwise_indexer_lifecycle_operations_total",
      labeled("outcome", outcome),
    );
  const reportNames = Array.from(
    new Set(
      samples
        .filter((sample) =>
          sample.name.startsWith("inqwise_indexer_report_"),
        )
        .map((sample) => sample.labels.report)
        .filter((name): name is string => Boolean(name)),
    ),
  ).sort().slice(0, 257);
  const reportMetric = (reportName: string, outcome: string) =>
    sum(
      "inqwise_indexer_report_executions_total",
      (sample) =>
        sample.labels.report === reportName &&
        sample.labels.outcome === outcome,
    );

  return {
    acceptedPutActions: intake("put_document", "accepted"),
    acceptedRemoveActions: intake("remove_document", "accepted"),
    rejectedActions: sum(
      "inqwise_indexer_action_intake_total",
      labeled("outcome", "rejected"),
    ),
    processedSucceeded: processing("succeeded"),
    processedFailed: processing("failed"),
    runtimeDesired: sum(
      "inqwise_indexer_runtime_convergence",
      labeled("state", "desired"),
    ),
    runtimeAttached: sum(
      "inqwise_indexer_runtime_convergence",
      labeled("state", "attached"),
    ),
    runtimeDrift: sum(
      "inqwise_indexer_runtime_convergence",
      labeled("state", "drift"),
    ),
    lifecyclePending: sum("inqwise_indexer_lifecycle_pending"),
    lifecycleSucceeded: lifecycle("succeeded"),
    lifecycleFailed: lifecycle("failed"),
    lifecycleRetrying: lifecycle("retrying"),
    reports: reportNames.map((reportName) => ({
      reportName,
      succeeded: reportMetric(reportName, "succeeded"),
      invalid: reportMetric(reportName, "invalid"),
      failed: reportMetric(reportName, "failed"),
      active: sum(
        "inqwise_indexer_report_executions_active",
        (sample) => sample.labels.report === reportName,
      ),
      durationSeconds: sum(
        "inqwise_indexer_report_execution_duration_seconds_total",
        (sample) => sample.labels.report === reportName,
      ),
    })),
  };
}

function parsePrometheusSample(line: string): PrometheusSample[] {
  const match = line.match(
    /^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{([^}]*)\})?\s+([^\s]+)(?:\s+\d+)?$/,
  );
  if (!match) {
    return [];
  }
  const value = Number(match[3]);
  if (!Number.isFinite(value)) {
    return [];
  }
  const labels: Record<string, string> = {};
  for (const label of (match[2] ?? "").matchAll(
    /([a-zA-Z_][a-zA-Z0-9_]*)="((?:\\.|[^"])*)"/g,
  )) {
    labels[label[1]] = label[2];
  }
  return [{ name: match[1], labels, value }];
}

function requestError(status: number, body: unknown): Error {
  let detail: string | undefined;
  if (body && typeof body === "object") {
    if ("detail" in body && typeof body.detail === "string") {
      detail = body.detail;
    } else if ("message" in body && typeof body.message === "string") {
      detail = body.message;
    }
  }
  return new Error(
    detail ? `Request failed (${status}): ${detail}` : `Request failed (${status})`,
  );
}
