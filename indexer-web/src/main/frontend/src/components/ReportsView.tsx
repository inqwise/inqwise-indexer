import { useEffect, useMemo, useState } from "react";

import type { OperationalMetrics } from "../api/indexer-api";
import { discoverReports } from "../api/reports-api";
import type { ReportPresentation } from "../api/reports-api";

const MAX_DISCOVERED_REPORTS = 256;
const MAX_TEXT = 512;

type ReportSummary = {
  name: string;
  title: string;
  description?: string;
};

export default function ReportsView({
  metrics,
  metricsState,
}: {
  metrics: OperationalMetrics | null;
  metricsState: "checking" | "online" | "degraded";
}) {
  const [reports, setReports] = useState<ReportSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    void discoverReports(controller.signal)
      .then((discovered) => {
        setReports(
          discovered
            .slice(0, MAX_DISCOVERED_REPORTS)
            .flatMap(validateSummary),
        );
        setError(null);
      })
      .catch((failure: unknown) => {
        if (!controller.signal.aborted) {
          setError(message(failure));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) {
          setLoading(false);
        }
      });
    return () => controller.abort();
  }, []);

  const activity = useMemo(() => {
    const byName = new Map(
      (metrics?.reports ?? []).map((metric) => [metric.reportName, metric]),
    );
    return reports.map((report) => ({
      report,
      metric: byName.get(report.name) ?? emptyMetric(report.name),
    }));
  }, [metrics, reports]);
  const totals = (metrics?.reports ?? []).reduce(
    (current, metric) => ({
      succeeded: current.succeeded + metric.succeeded,
      invalid: current.invalid + metric.invalid,
      failed: current.failed + metric.failed,
      active: current.active + metric.active,
      durationSeconds: current.durationSeconds + metric.durationSeconds,
    }),
    { succeeded: 0, invalid: 0, failed: 0, active: 0, durationSeconds: 0 },
  );
  const completed = totals.succeeded + totals.invalid + totals.failed;

  return (
    <section aria-label="Report activity" className="reports-view" id="reports">
      <div className="reports-view__header">
        <div>
          <span className="eyebrow">Read-only operations</span>
          <h3>Report activity</h3>
          <p>
            Provider-neutral execution signals for the reports registered in this
            node. Parameters, results, consumer fields, and physical indexes remain
            outside the operator console.
          </p>
        </div>
        <span className="reports-view__contract">Process lifetime</span>
      </div>

      <div className="report-activity__metrics">
        <ActivityMetric
          detail="discovered through the neutral catalog"
          label="Available reports"
          value={loading ? "…" : String(reports.length)}
        />
        <ActivityMetric
          detail="completed since this process started"
          label="Executions observed"
          value={formatCount(completed)}
        />
        <ActivityMetric
          detail={`${formatCount(totals.invalid)} invalid · ${formatCount(totals.failed)} failed`}
          label="Outcomes"
          value={`${formatCount(totals.succeeded)} succeeded`}
          warn={totals.invalid + totals.failed > 0}
        />
        <ActivityMetric
          detail={`${formatCount(totals.active)} active now`}
          label="Average duration"
          value={
            completed > 0
              ? formatDuration(totals.durationSeconds / completed)
              : "No samples"
          }
        />
      </div>

      {error ? (
        <div className="report-state" role="alert">
          Report discovery unavailable: {error}
        </div>
      ) : reports.length === 0 && !loading ? (
        <div className="report-state" role="status">
          No reports are currently available.
        </div>
      ) : (
        <div className="report-activity__table-wrap">
          <table className="report-activity__table">
            <thead>
              <tr>
                <th>Report</th>
                <th>Executions</th>
                <th>Outcomes</th>
                <th>Active</th>
                <th>Avg duration</th>
              </tr>
            </thead>
            <tbody>
              {activity.map(({ report, metric }) => {
                const count = metric.succeeded + metric.invalid + metric.failed;
                return (
                  <tr key={report.name}>
                    <td>
                      <strong>{report.title}</strong>
                      <small>{report.description || report.name}</small>
                      <code>{report.name}</code>
                    </td>
                    <td data-label="Executions">{formatCount(count)}</td>
                    <td data-label="Outcomes">
                      <span
                        className={
                          metric.invalid + metric.failed > 0
                            ? "report-activity__outcome--warn"
                            : ""
                        }
                      >
                        {formatCount(metric.succeeded)} / {formatCount(metric.invalid)} / {formatCount(metric.failed)}
                      </span>
                      <small>succeeded / invalid / failed</small>
                    </td>
                    <td data-label="Active">{formatCount(metric.active)}</td>
                    <td data-label="Avg duration">
                      {count > 0
                        ? formatDuration(metric.durationSeconds / count)
                        : "—"}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
      {metricsState !== "online" && (
        <p className="report-activity__notice">
          {metricsState === "checking"
            ? "Waiting for operational metrics."
            : "Metrics are degraded; showing the last available sample."}
        </p>
      )}
    </section>
  );
}

function ActivityMetric({
  label,
  value,
  detail,
  warn = false,
}: {
  label: string;
  value: string;
  detail: string;
  warn?: boolean;
}) {
  return (
    <article
      className={`report-activity__metric${warn ? " report-activity__metric--warn" : ""}`}
    >
      <span>{label}</span>
      <strong>{value}</strong>
      <small>{detail}</small>
    </article>
  );
}

function validateSummary(report: ReportPresentation): ReportSummary[] {
  if (
    typeof report.name !== "string" ||
    !report.name ||
    report.name.length > MAX_TEXT ||
    typeof report.title !== "string" ||
    !report.title ||
    report.title.length > MAX_TEXT
  ) {
    return [];
  }
  const summary: ReportSummary = { name: report.name, title: report.title };
  if (typeof report.description === "string" && report.description) {
    summary.description = report.description.slice(0, MAX_TEXT);
  }
  return [summary];
}

function emptyMetric(reportName: string) {
  return {
    reportName,
    succeeded: 0,
    invalid: 0,
    failed: 0,
    active: 0,
    durationSeconds: 0,
  };
}

function formatCount(value: number): string {
  return Math.max(0, value).toLocaleString();
}

function formatDuration(seconds: number): string {
  return seconds < 1
    ? `${Math.round(seconds * 1_000)} ms`
    : `${seconds.toFixed(seconds < 10 ? 2 : 1)} s`;
}

function message(error: unknown): string {
  return error instanceof Error ? error.message : "Unknown error";
}
