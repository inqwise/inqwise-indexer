import createClient from "openapi-fetch";

import type {
  components as ReportsComponents,
  paths as ReportsPaths,
} from "../generated/reports-api";

type GeneratedPresentation = ReportsComponents["schemas"]["ReportPresentation"];

export type JsonSchema = Record<string, unknown>;
export type ReportPresentation = Omit<
  GeneratedPresentation,
  "parameters_schema" | "result_schema"
> & {
  parameters_schema: JsonSchema;
  result_schema: JsonSchema;
};

const reportsClient = createClient<ReportsPaths>({
  baseUrl: "/api/reports",
  headers: { accept: "application/json" },
});

export async function discoverReports(
  signal: AbortSignal,
): Promise<ReportPresentation[]> {
  const { data, error, response } = await reportsClient.GET("/reports", {
    signal,
  });
  if (!data) {
    throw requestError(response.status, error);
  }
  return data.reports as ReportPresentation[];
}

function requestError(status: number, error: unknown): Error {
  if (error && typeof error === "object") {
    const detail = "detail" in error ? error.detail : undefined;
    const message = "message" in error ? error.message : undefined;
    if (typeof detail === "string" && detail) {
      return new Error(detail);
    }
    if (typeof message === "string" && message) {
      return new Error(message);
    }
  }
  return new Error(`Reports request failed with status ${status}`);
}
