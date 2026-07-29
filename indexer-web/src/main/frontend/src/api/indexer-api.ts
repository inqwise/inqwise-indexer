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
  return response.status === 204;
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
