import createClient from "openapi-fetch";

import type {
  components as LoadComponents,
  paths as LoadPaths,
} from "../generated/load-query-api";

export type LoadView = LoadComponents["schemas"]["LoadView"];

const client = createClient<LoadPaths>({
  baseUrl: "/api/loads",
  headers: { accept: "application/json" },
});

export async function listLoads(signal: AbortSignal): Promise<LoadView[]> {
  const { data, error, response } = await client.GET("/admin/loads", {
    params: { query: { max: 50 } },
    signal,
  });
  if (!data) {
    throw new Error(loadError(response.status, error));
  }
  return data.loads;
}

function loadError(status: number, error: unknown): string {
  if (error && typeof error === "object" && "detail" in error) {
    const detail = (error as { detail?: unknown }).detail;
    if (typeof detail === "string") {
      return `Load query failed (${status}): ${detail}`;
    }
  }
  return `Load query failed with status ${status}`;
}
