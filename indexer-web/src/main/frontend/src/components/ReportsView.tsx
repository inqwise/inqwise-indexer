import { useEffect, useMemo, useState } from "react";

import {
  discoverReports,
  executeReport,
} from "../api/reports-api";
import {
  buildParameters,
  scalarType,
  validatePresentation,
  validateResultPayload,
} from "../report-schema";
import type {
  ArraySchema,
  ScalarSchema,
  ValidatedReportPresentation,
} from "../report-schema";

const MAX_RENDERED_ROWS = 100;
const MAX_RENDERED_TEXT = 2_048;
const MAX_DISCOVERED_REPORTS = 256;

type FieldValue = string | boolean;
type ReportResult = Record<string, unknown>;

export default function ReportsView() {
  const [reports, setReports] = useState<ValidatedReportPresentation[]>([]);
  const [selectedName, setSelectedName] = useState("");
  const [values, setValues] = useState<Record<string, FieldValue>>({});
  const [result, setResult] = useState<ReportResult | null>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [rejectedCount, setRejectedCount] = useState(0);

  const selected = useMemo(
    () => reports.find((report) => report.name === selectedName) ?? null,
    [reports, selectedName],
  );

  useEffect(() => {
    const controller = new AbortController();
    void discoverReports(controller.signal)
      .then((discovered) => {
        const accepted: ValidatedReportPresentation[] = [];
        let rejected = Math.max(0, discovered.length - MAX_DISCOVERED_REPORTS);
        for (const report of discovered.slice(0, MAX_DISCOVERED_REPORTS)) {
          try {
            accepted.push(validatePresentation(report));
          } catch {
            rejected += 1;
          }
        }
        setReports(accepted);
        setRejectedCount(rejected);
        setSelectedName((current) =>
          accepted.some((report) => report.name === current)
            ? current
            : (accepted[0]?.name ?? ""),
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

  useEffect(() => {
    setValues(selected ? defaultValues(selected) : {});
    setResult(null);
    setError(null);
  }, [selected]);

  async function runReport() {
    if (!selected) {
      return;
    }
    const controller = new AbortController();
    setRunning(true);
    setError(null);
    setResult(null);
    try {
      const parameters = buildParameters(selected.parameters_schema, values);
      const payload = await executeReport(selected.name, parameters, controller.signal);
      validateResultPayload(selected.result_schema, payload);
      setResult(payload);
    } catch (failure) {
      setError(message(failure));
    } finally {
      setRunning(false);
    }
  }

  return (
    <section aria-label="Reports" className="reports-view" id="reports">
      <div className="reports-view__header">
        <div>
          <span className="eyebrow">On-demand analysis</span>
          <h3>Reports</h3>
          <p>
            Forms and results are generated from the neutral report discovery
            contract. Consumer code and physical index identities stay server-side.
          </p>
        </div>
        <span className="reports-view__contract">Schema-driven</span>
      </div>

      {loading ? (
        <div className="report-state" role="status">Loading report catalog…</div>
      ) : reports.length === 0 ? (
        <div className="report-state" role="status">
          <strong>No compatible reports</strong>
          <p>{error ?? "No report presentations match the supported schema subset."}</p>
        </div>
      ) : (
        <div className="reports-layout">
          <aside className="report-catalog" aria-label="Available reports">
            {reports.map((report) => (
              <button
                className={report.name === selectedName ? "is-active" : ""}
                key={report.name}
                onClick={() => setSelectedName(report.name)}
                type="button"
              >
                <strong>{report.title}</strong>
                <span>{report.description || report.name}</span>
              </button>
            ))}
            {rejectedCount > 0 && (
              <small>{rejectedCount} unsupported presentation{rejectedCount === 1 ? "" : "s"} hidden</small>
            )}
          </aside>

          {selected && (
            <div className="report-workspace">
              <header>
                <span className="eyebrow">{selected.name}</span>
                <h4>{selected.title}</h4>
                {selected.description && <p>{selected.description}</p>}
              </header>

              <form
                className="report-form"
                onSubmit={(event) => {
                  event.preventDefault();
                  void runReport();
                }}
              >
                <div className="report-fields">
                  {Object.entries(selected.parameters_schema.properties).map(
                    ([name, schema]) => (
                      <ReportField
                        key={name}
                        name={name}
                        onChange={(value) =>
                          setValues((current) => ({ ...current, [name]: value }))
                        }
                        required={selected.parameters_schema.required.includes(name)}
                        schema={schema}
                        value={values[name] ?? ""}
                      />
                    ),
                  )}
                </div>
                <button className="report-run" disabled={running} type="submit">
                  {running ? "Running…" : "Run report"}
                </button>
              </form>

              {error && <div className="report-error" role="alert">{error}</div>}
              {result && (
                <ReportResultView result={result} schema={selected.result_schema} />
              )}
            </div>
          )}
        </div>
      )}
    </section>
  );
}

function ReportField({
  name,
  onChange,
  required,
  schema,
  value,
}: {
  name: string;
  onChange: (value: FieldValue) => void;
  required: boolean;
  schema: ScalarSchema;
  value: FieldValue;
}) {
  const type = scalarType(schema);
  const label = schema.title ?? name.replaceAll("_", " ");
  if (schema.format === "date-time") {
    return (
      <ReportDateTimeField
        description={schema.description}
        label={label}
        onChange={onChange}
        required={required}
        value={String(value)}
      />
    );
  }
  if (type === "boolean") {
    return (
      <label className="report-field report-field--boolean">
        <input
          checked={value === true}
          onChange={(event) => onChange(event.target.checked)}
          type="checkbox"
        />
        <span><strong>{label}</strong>{schema.description && <small>{schema.description}</small>}</span>
      </label>
    );
  }
  if (schema.enum) {
    return (
      <label className="report-field">
        <span>{label}{required && <em>Required</em>}</span>
        <select
          onChange={(event) => onChange(event.target.value)}
          required={required}
          value={String(value)}
        >
          {!required && <option value="">Not set</option>}
          {schema.enum.map((option) => <option key={option} value={option}>{option.replaceAll("_", " ")}</option>)}
        </select>
        {schema.description && <small>{schema.description}</small>}
      </label>
    );
  }
  const inputType = type === "integer" || type === "number" ? "number" : "text";
  return (
    <label className="report-field">
      <span>{label}{required && <em>Required</em>}</span>
      <input
        max={schema.maximum}
        maxLength={schema.maxLength ?? (type === "string" ? MAX_RENDERED_TEXT : undefined)}
        min={schema.minimum}
        minLength={schema.minLength}
        onChange={(event) => onChange(event.target.value)}
        required={required}
        step={type === "integer" ? 1 : type === "number" ? "any" : undefined}
        type={inputType}
        value={String(value)}
      />
      {schema.description && <small>{schema.description}</small>}
    </label>
  );
}

function ReportDateTimeField({
  description,
  label,
  onChange,
  required,
  value,
}: {
  description?: string;
  label: string;
  onChange: (value: FieldValue) => void;
  required: boolean;
  value: string;
}) {
  const [date = "", time = ""] = value.split("T", 2);
  const effectiveTime = time || "00:00";
  return (
    <fieldset className="report-field report-field--date-time">
      <legend>{label}{required && <em>Required</em>}</legend>
      <div className="report-date-time-inputs">
        <label>
          <span>Date</span>
          <input
            aria-label={`${label} date`}
            onChange={(event) => onChange(
              event.target.value ? `${event.target.value}T${effectiveTime}` : "",
            )}
            required={required}
            type="date"
            value={date}
          />
        </label>
        <label>
          <span>Time</span>
          <input
            aria-label={`${label} time`}
            disabled={!date}
            onChange={(event) => onChange(`${date}T${event.target.value || "00:00"}`)}
            type="time"
            value={effectiveTime}
          />
        </label>
      </div>
      {description && <small>{description}</small>}
    </fieldset>
  );
}

function ReportResultView({
  result,
  schema,
}: {
  result: ReportResult;
  schema: ValidatedReportPresentation["result_schema"];
}) {
  return (
    <section aria-label="Report result" className="report-result">
      <div className="report-result__header">
        <span className="eyebrow">Result</span>
        <strong>Completed</strong>
      </div>
      {Object.entries(schema.properties).map(([name, property]) =>
        property.type === "array" ? (
          <ResultTable
            key={name}
            name={name}
            rows={Array.isArray(result[name]) ? result[name] : []}
            schema={property}
          />
        ) : (
          <div className="report-result__scalar" key={name}>
            <span>{property.title ?? name.replaceAll("_", " ")}</span>
            <ResultValue schema={property} value={result[name]} />
          </div>
        ),
      )}
    </section>
  );
}

function ResultTable({
  name,
  rows,
  schema,
}: {
  name: string;
  rows: unknown[];
  schema: ArraySchema;
}) {
  const columns = Object.entries(schema.items.properties) as [string, ScalarSchema][];
  const visible = rows.slice(0, MAX_RENDERED_ROWS) as Record<string, unknown>[];
  return (
    <div className="report-table-block">
      <div className="report-table-block__title">
        <strong>{schema.title ?? name.replaceAll("_", " ")}</strong>
        <span>{rows.length} row{rows.length === 1 ? "" : "s"}</span>
      </div>
      {visible.length === 0 ? (
        <p className="report-result__empty">No rows returned.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead><tr>{columns.map(([key, column]) => <th key={key}>{column.title ?? key.replaceAll("_", " ")}</th>)}</tr></thead>
            <tbody>
              {visible.map((row, index) => (
                <tr key={index}>
                  {columns.map(([key, column]) => <td key={key}><ResultValue schema={column} value={row[key]} /></td>)}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {rows.length > MAX_RENDERED_ROWS && <small>Showing the first {MAX_RENDERED_ROWS} rows.</small>}
    </div>
  );
}

function ResultValue({ schema, value }: { schema: ScalarSchema; value: unknown }) {
  if (value === null || value === undefined) {
    return <span className="report-value--empty">—</span>;
  }
  if (schema.format === "uri" && typeof value === "string" && safeHttpUrl(value)) {
    return <a href={value} rel="noreferrer" target="_blank">{boundedText(value)}</a>;
  }
  if (schema.format === "date-time" && typeof value === "string") {
    return <time dateTime={value}>{new Date(value).toLocaleString()}</time>;
  }
  if (typeof value === "boolean") {
    return <span>{value ? "Yes" : "No"}</span>;
  }
  return <span>{boundedText(String(value))}</span>;
}

function defaultValues(
  report: ValidatedReportPresentation,
): Record<string, FieldValue> {
  return Object.fromEntries(
    Object.entries(report.parameters_schema.properties).map(([name, schema]) => {
      if (schema.default === undefined) {
        return [name, scalarType(schema) === "boolean" ? false : ""];
      }
      if (schema.format === "date-time" && typeof schema.default === "string") {
        return [name, localDateTime(schema.default)];
      }
      return [name, typeof schema.default === "boolean" ? schema.default : String(schema.default)];
    }),
  );
}

function localDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function safeHttpUrl(value: string): boolean {
  try {
    const url = new URL(value);
    return url.protocol === "http:" || url.protocol === "https:";
  } catch {
    return false;
  }
}

function boundedText(value: string): string {
  return value.length > MAX_RENDERED_TEXT
    ? `${value.slice(0, MAX_RENDERED_TEXT)}…`
    : value;
}

function message(value: unknown): string {
  return value instanceof Error ? value.message : "Report request failed";
}
