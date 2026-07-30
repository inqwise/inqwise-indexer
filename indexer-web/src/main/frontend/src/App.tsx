import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import {
  activateIndexer,
  deactivateIndexer,
  deleteIndexer,
  isReady,
  listIndexers,
  listTargets,
  reconcileIndexer,
  recoverTargetProvisioning,
  resetIndexerQueue,
  runtimeStatus,
} from "./api/indexer-api";
import type {
  Indexer,
  RuntimeIndexer,
  Target,
} from "./api/indexer-api";
import CatalogDetailPanel from "./components/CatalogDetailPanel";

type HealthState = "checking" | "online" | "offline";
type IndexerRuntimeFilter = Indexer["runtime_state"] | "ALL";
type IndexerPublicationFilter = Indexer["publication_state"] | "ALL";
type TargetProvisioningFilter = Target["provisioning_state"] | "ALL";
type PollInterval = 0 | 15_000 | 30_000 | 60_000;
type DashboardSection = "overview" | "targets" | "indexers" | "runtime";
type CatalogPageSize = 10 | 25 | 50;
type IndexerSort =
  | "NAME_ASC"
  | "TARGET_ASC"
  | "UPDATED_DESC"
  | "VERSION_DESC";
type TargetSort = "NAME_ASC" | "PERIOD_DESC" | "UPDATED_DESC" | "STATUS_ASC";
type ServiceName = "readiness" | "targets" | "indexers" | "runtime";
type ServiceState = "checking" | "online" | "degraded";
type ServiceDiagnostic = {
  state: ServiceState;
  lastSuccess: Date | null;
  error: string | null;
};
type RuntimeDrift = {
  indexerId: number;
  indexName: string;
  targetName: string;
  kind: "MISSING_ATTACHMENT" | "UNEXPECTED_ATTACHMENT";
};

type DashboardData = {
  health: HealthState;
  targets: Target[];
  indexers: Indexer[];
  runtimeIndexers: RuntimeIndexer[];
  services: Record<ServiceName, ServiceDiagnostic>;
};

const INITIAL_SERVICE: ServiceDiagnostic = {
  state: "checking",
  lastSuccess: null,
  error: null,
};
const INITIAL_DATA: DashboardData = {
  health: "checking",
  targets: [],
  indexers: [],
  runtimeIndexers: [],
  services: {
    readiness: INITIAL_SERVICE,
    targets: INITIAL_SERVICE,
    indexers: INITIAL_SERVICE,
    runtime: INITIAL_SERVICE,
  },
};
const SERVICE_LABELS: Record<ServiceName, string> = {
  readiness: "Readiness",
  targets: "Target catalog",
  indexers: "Indexer catalog",
  runtime: "Local runtime",
};
const DEFAULT_POLL_INTERVAL: PollInterval = 15_000;
const POLL_INTERVALS: readonly PollInterval[] = [0, 15_000, 30_000, 60_000];
const CATALOG_PAGE_SIZES: readonly CatalogPageSize[] = [10, 25, 50];
const DASHBOARD_SECTIONS: readonly DashboardSection[] = [
  "overview",
  "targets",
  "indexers",
  "runtime",
];

function statusTone(status: string): "good" | "warn" | "neutral" {
  if (["ACTIVE", "READY", "PUBLISHED", "COMPLETED"].includes(status)) {
    return "good";
  }
  if (["FAILED", "DELETING", "INACTIVE", "NON_ACTIVE"].includes(status)) {
    return "warn";
  }
  return "neutral";
}

function StatusPill({ value }: { value: string }) {
  return (
    <span className={`status-pill status-pill--${statusTone(value)}`}>
      <span aria-hidden="true" className="status-pill__dot" />
      {value.replaceAll("_", " ").toLowerCase()}
    </span>
  );
}

function Metric({
  label,
  value,
  detail,
  accent,
}: {
  label: string;
  value: number | string;
  detail: string;
  accent: "cyan" | "violet" | "amber";
}) {
  return (
    <article className={`metric metric--${accent}`}>
      <div className="metric__topline">
        <span>{label}</span>
        <span className="metric__mark" aria-hidden="true" />
      </div>
      <strong>{value}</strong>
      <p>{detail}</p>
    </article>
  );
}

function Pagination({
  page,
  pageCount,
  onPageChange,
}: {
  page: number;
  pageCount: number;
  onPageChange: (page: number) => void;
}) {
  if (pageCount <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Catalog pages">
      <button
        disabled={page === 1}
        onClick={() => onPageChange(page - 1)}
        type="button"
      >
        Previous
      </button>
      <span>
        Page <strong>{page}</strong> of {pageCount}
      </span>
      <button
        disabled={page === pageCount}
        onClick={() => onPageChange(page + 1)}
        type="button"
      >
        Next
      </button>
    </nav>
  );
}

function queryValue(name: string): string | null {
  return new URLSearchParams(window.location.search).get(name);
}

function queryEnum<T extends string>(
  name: string,
  values: readonly T[],
  fallback: T,
): T {
  const value = queryValue(name);
  return value && values.includes(value as T) ? (value as T) : fallback;
}

function queryId(name: string): number | null {
  const value = queryValue(name);
  if (!value) {
    return null;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null;
}

function queryPositiveInteger(name: string, fallback: number): number {
  const value = queryValue(name);
  if (!value) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : fallback;
}

function queryPageSize(name: string): CatalogPageSize {
  const value = queryPositiveInteger(name, 10);
  return CATALOG_PAGE_SIZES.includes(value as CatalogPageSize)
    ? (value as CatalogPageSize)
    : 10;
}

function initialPollInterval(): PollInterval {
  const queryInterval = queryValue("poll");
  if (queryInterval === null) {
    return DEFAULT_POLL_INTERVAL;
  }
  const seconds = Number(queryInterval);
  const interval = seconds * 1_000;
  return POLL_INTERVALS.includes(interval as PollInterval)
    ? (interval as PollInterval)
    : DEFAULT_POLL_INTERVAL;
}

function currentSection(): DashboardSection {
  const section = window.location.hash.slice(1);
  return DASHBOARD_SECTIONS.includes(section as DashboardSection)
    ? (section as DashboardSection)
    : "overview";
}

function ageLabel(ageMs: number): string {
  const seconds = Math.max(0, Math.floor(ageMs / 1_000));
  if (seconds < 5) {
    return "just now";
  }
  if (seconds < 60) {
    return `${seconds}s ago`;
  }
  return `${Math.floor(seconds / 60)}m ago`;
}

function failureMessage(reason: unknown): string {
  return reason instanceof Error ? reason.message : "Request failed";
}

function serviceDiagnostic(
  result: PromiseSettledResult<unknown>,
  current: ServiceDiagnostic,
  completedAt: Date,
  unavailableMessage?: string,
): ServiceDiagnostic {
  if (result.status === "rejected") {
    return {
      ...current,
      state: "degraded",
      error: failureMessage(result.reason),
    };
  }
  if (unavailableMessage && result.value === false) {
    return {
      ...current,
      state: "degraded",
      error: unavailableMessage,
    };
  }
  return {
    state: "online",
    lastSuccess: completedAt,
    error: null,
  };
}

export default function App() {
  const [data, setData] = useState<DashboardData>(INITIAL_DATA);
  const [refreshing, setRefreshing] = useState(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);
  const [clock, setClock] = useState(Date.now());
  const [documentVisible, setDocumentVisible] = useState(
    () => document.visibilityState === "visible",
  );
  const [pollInterval, setPollInterval] = useState<PollInterval>(
    initialPollInterval,
  );
  const [activeSection, setActiveSection] =
    useState<DashboardSection>(currentSection);
  const [indexerSearch, setIndexerSearch] = useState(
    () => queryValue("iq") ?? "",
  );
  const [indexerRuntimeFilter, setIndexerRuntimeFilter] =
    useState<IndexerRuntimeFilter>(() =>
      queryEnum("ir", ["ALL", "ACTIVE", "NON_ACTIVE"], "ALL"),
    );
  const [indexerPublicationFilter, setIndexerPublicationFilter] =
    useState<IndexerPublicationFilter>(() =>
      queryEnum(
        "ip",
        ["ALL", "PUBLISHED", "UNPUBLISHED", "RETIRED"],
        "ALL",
      ),
    );
  const [indexerSort, setIndexerSort] = useState<IndexerSort>(() =>
    queryEnum(
      "isort",
      ["NAME_ASC", "TARGET_ASC", "UPDATED_DESC", "VERSION_DESC"],
      "NAME_ASC",
    ),
  );
  const [indexerPageSize, setIndexerPageSize] = useState<CatalogPageSize>(() =>
    queryPageSize("ilimit"),
  );
  const [indexerPage, setIndexerPage] = useState(() =>
    queryPositiveInteger("ipage", 1),
  );
  const [targetSearch, setTargetSearch] = useState(
    () => queryValue("tq") ?? "",
  );
  const [targetProvisioningFilter, setTargetProvisioningFilter] =
    useState<TargetProvisioningFilter>(() =>
      queryEnum("tp", ["ALL", "READY", "PROVISIONING", "FAILED"], "ALL"),
    );
  const [targetSort, setTargetSort] = useState<TargetSort>(() =>
    queryEnum(
      "tsort",
      ["NAME_ASC", "PERIOD_DESC", "UPDATED_DESC", "STATUS_ASC"],
      "NAME_ASC",
    ),
  );
  const [targetPageSize, setTargetPageSize] = useState<CatalogPageSize>(() =>
    queryPageSize("tlimit"),
  );
  const [targetPage, setTargetPage] = useState(() =>
    queryPositiveInteger("tpage", 1),
  );
  const [selectedTargetId, setSelectedTargetId] = useState<number | null>(() =>
    queryId("indexer") === null ? queryId("target") : null,
  );
  const [selectedIndexerId, setSelectedIndexerId] = useState<number | null>(
    () => queryId("indexer"),
  );
  const activeLoadRef = useRef<Promise<void> | null>(null);

  const performLoad = useCallback(async (signal: AbortSignal) => {
    setRefreshing(true);
    try {
      const [healthResult, targetResult, indexerResult, runtimeResult] =
        await Promise.allSettled([
          isReady(signal),
          listTargets(signal),
          listIndexers(signal),
          runtimeStatus(signal),
        ]);

      if (signal.aborted) {
        return;
      }

      const completedAt = new Date();
      setData((current) => ({
        health:
          healthResult.status === "fulfilled" && healthResult.value
            ? "online"
            : "offline",
        targets:
          targetResult.status === "fulfilled"
            ? targetResult.value
            : current.targets,
        indexers:
          indexerResult.status === "fulfilled"
            ? indexerResult.value
            : current.indexers,
        runtimeIndexers:
          runtimeResult.status === "fulfilled"
            ? runtimeResult.value
            : current.runtimeIndexers,
        services: {
          readiness: serviceDiagnostic(
            healthResult,
            current.services.readiness,
            completedAt,
            "The node is not ready.",
          ),
          targets: serviceDiagnostic(
            targetResult,
            current.services.targets,
            completedAt,
          ),
          indexers: serviceDiagnostic(
            indexerResult,
            current.services.indexers,
            completedAt,
          ),
          runtime: serviceDiagnostic(
            runtimeResult,
            current.services.runtime,
            completedAt,
          ),
        },
      }));
      if (
        healthResult.status === "fulfilled" &&
        targetResult.status === "fulfilled" &&
        indexerResult.status === "fulfilled" &&
        runtimeResult.status === "fulfilled"
      ) {
        setLastUpdated(completedAt);
      }
    } catch (error) {
      if (
        signal.aborted ||
        (error instanceof DOMException && error.name === "AbortError")
      ) {
        return;
      }
      setData((current) => ({
        ...current,
        health: "offline",
        services: Object.fromEntries(
          Object.entries(current.services).map(([name, service]) => [
            name,
            {
              ...service,
              state: "degraded",
              error: failureMessage(error),
            },
          ]),
        ) as DashboardData["services"],
      }));
    } finally {
      setRefreshing(false);
    }
  }, []);

  const load = useCallback(
    async (signal: AbortSignal, refreshAfterActive = false) => {
      const activeLoad = activeLoadRef.current;
      if (activeLoad) {
        await activeLoad;
        if (!refreshAfterActive || signal.aborted) {
          return;
        }
      }
      if (signal.aborted) {
        return;
      }

      const nextLoad = performLoad(signal);
      activeLoadRef.current = nextLoad;
      try {
        await nextLoad;
      } finally {
        if (activeLoadRef.current === nextLoad) {
          activeLoadRef.current = null;
        }
      }
    },
    [performLoad],
  );

  useEffect(() => {
    const updateVisibility = () =>
      setDocumentVisible(document.visibilityState === "visible");
    document.addEventListener("visibilitychange", updateVisibility);
    return () =>
      document.removeEventListener("visibilitychange", updateVisibility);
  }, []);

  useEffect(() => {
    const updateSection = () => setActiveSection(currentSection());
    window.addEventListener("hashchange", updateSection);
    return () => window.removeEventListener("hashchange", updateSection);
  }, []);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => {
      document.getElementById(activeSection)?.scrollIntoView({
        block: "start",
      });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [activeSection]);

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 5_000);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const url = new URL(window.location.href);
    const setOptional = (name: string, value: string, fallback = "") => {
      if (value === fallback) {
        url.searchParams.delete(name);
      } else {
        url.searchParams.set(name, value);
      }
    };
    setOptional("iq", indexerSearch);
    setOptional("ir", indexerRuntimeFilter, "ALL");
    setOptional("ip", indexerPublicationFilter, "ALL");
    setOptional("isort", indexerSort, "NAME_ASC");
    setOptional("ilimit", String(indexerPageSize), "10");
    setOptional("ipage", String(indexerPage), "1");
    setOptional("tq", targetSearch);
    setOptional("tp", targetProvisioningFilter, "ALL");
    setOptional("tsort", targetSort, "NAME_ASC");
    setOptional("tlimit", String(targetPageSize), "10");
    setOptional("tpage", String(targetPage), "1");
    setOptional(
      "poll",
      String(pollInterval / 1_000),
      String(DEFAULT_POLL_INTERVAL / 1_000),
    );
    setOptional(
      "target",
      selectedTargetId === null ? "" : String(selectedTargetId),
    );
    setOptional(
      "indexer",
      selectedIndexerId === null ? "" : String(selectedIndexerId),
    );
    window.history.replaceState(null, "", url);
  }, [
    indexerPublicationFilter,
    indexerPage,
    indexerPageSize,
    indexerRuntimeFilter,
    indexerSearch,
    indexerSort,
    pollInterval,
    selectedIndexerId,
    selectedTargetId,
    targetProvisioningFilter,
    targetPage,
    targetPageSize,
    targetSearch,
    targetSort,
  ]);

  useEffect(() => {
    const controller = new AbortController();
    if (!documentVisible) {
      return () => controller.abort();
    }

    void load(controller.signal, true);
    if (pollInterval === 0) {
      return () => controller.abort();
    }
    const interval = window.setInterval(() => {
      void load(controller.signal);
    }, pollInterval);

    return () => {
      controller.abort();
      window.clearInterval(interval);
    };
  }, [documentVisible, load, pollInterval]);

  const activeIndexers = useMemo(
    () =>
      data.indexers.filter((indexer) => indexer.runtime_state === "ACTIVE")
        .length,
    [data.indexers],
  );

  const publishedIndexers = useMemo(
    () =>
      data.indexers.filter(
        (indexer) => indexer.publication_state === "PUBLISHED",
      ).length,
    [data.indexers],
  );

  const provisioningIssues = useMemo(
    () =>
      data.targets.filter((target) => target.provisioning_state === "FAILED")
        .length +
      data.indexers.filter(
        (indexer) => indexer.provisioning_state === "FAILED",
      ).length,
    [data.indexers, data.targets],
  );

  const runtimeComparisonAvailable =
    data.services.indexers.state === "online" &&
    data.services.runtime.state === "online";
  const runtimeDrifts = useMemo<RuntimeDrift[]>(() => {
    if (!runtimeComparisonAvailable) {
      return [];
    }
    const attachedIds = new Set(
      data.runtimeIndexers.map((indexer) => indexer.indexer_id),
    );
    return data.indexers.flatMap((indexer) => {
      const shouldBeAttached =
        indexer.runtime_state === "ACTIVE" &&
        indexer.provisioning_state === "READY" &&
        indexer.mutation_state !== "DELETING";
      const attached = attachedIds.has(indexer.id);
      if (shouldBeAttached === attached) {
        return [];
      }
      return [
        {
          indexerId: indexer.id,
          indexName: indexer.index_name,
          targetName: indexer.target_name,
          kind: shouldBeAttached
            ? "MISSING_ATTACHMENT"
            : "UNEXPECTED_ATTACHMENT",
        },
      ];
    });
  }, [data.indexers, data.runtimeIndexers, runtimeComparisonAvailable]);

  const runtimeDriftByIndexer = useMemo(
    () =>
      new Map(runtimeDrifts.map((drift) => [drift.indexerId, drift] as const)),
    [runtimeDrifts],
  );

  const staleAfterMs =
    pollInterval === 0 ? 60_000 : Math.max(30_000, pollInterval * 2);
  const dataAgeMs =
    lastUpdated === null ? null : Math.max(0, clock - lastUpdated.getTime());
  const hasDegradedService = Object.values(data.services).some(
    (service) => service.state === "degraded",
  );
  const dataStale =
    hasDegradedService ||
    (dataAgeMs !== null && dataAgeMs > staleAfterMs);
  const updateStatus = !documentVisible
    ? "Paused while tab is hidden"
    : pollInterval === 0
      ? "Auto-refresh paused"
      : dataStale
        ? dataAgeMs === null
          ? "Service data unavailable"
          : `Data stale · last success ${ageLabel(dataAgeMs)}`
        : refreshing
          ? "Refreshing live data"
          : `Live · every ${pollInterval / 1_000}s`;
  const degradedServices = (
    Object.entries(data.services) as [
      ServiceName,
      ServiceDiagnostic,
    ][]
  ).filter(([, service]) => service.state === "degraded");
  const operationalIssues =
    provisioningIssues + runtimeDrifts.length + degradedServices.length;

  const filteredIndexers = useMemo(() => {
    const query = indexerSearch.trim().toLowerCase();
    return data.indexers
      .filter(
        (indexer) =>
          (!query ||
            [indexer.index_name, indexer.target_name, indexer.uid].some(
              (value) => value.toLowerCase().includes(query),
            )) &&
          (indexerRuntimeFilter === "ALL" ||
            indexer.runtime_state === indexerRuntimeFilter) &&
          (indexerPublicationFilter === "ALL" ||
            indexer.publication_state === indexerPublicationFilter),
      )
      .sort((left, right) => {
        const idOrder = left.id - right.id;
        switch (indexerSort) {
          case "TARGET_ASC":
            return (
              left.target_name.localeCompare(right.target_name) ||
              left.index_name.localeCompare(right.index_name) ||
              idOrder
            );
          case "UPDATED_DESC":
            return (
              right.updated_at.localeCompare(left.updated_at) ||
              right.id - left.id
            );
          case "VERSION_DESC":
            return right.version - left.version || right.id - left.id;
          default:
            return left.index_name.localeCompare(right.index_name) || idOrder;
        }
      });
  }, [
    data.indexers,
    indexerPublicationFilter,
    indexerRuntimeFilter,
    indexerSearch,
    indexerSort,
  ]);

  const filteredTargets = useMemo(() => {
    const query = targetSearch.trim().toLowerCase();
    return data.targets
      .filter(
        (target) =>
          (!query ||
            [target.target_name, target.uid, target.period_key ?? ""].some(
              (value) => value.toLowerCase().includes(query),
            )) &&
          (targetProvisioningFilter === "ALL" ||
            target.provisioning_state === targetProvisioningFilter),
      )
      .sort((left, right) => {
        const idOrder = left.id - right.id;
        switch (targetSort) {
          case "PERIOD_DESC":
            return (
              (right.period_key ?? "").localeCompare(left.period_key ?? "") ||
              right.id - left.id
            );
          case "UPDATED_DESC":
            return (
              right.updated_at.localeCompare(left.updated_at) ||
              right.id - left.id
            );
          case "STATUS_ASC":
            return (
              left.provisioning_state.localeCompare(
                right.provisioning_state,
              ) ||
              left.target_name.localeCompare(right.target_name) ||
              idOrder
            );
          default:
            return left.target_name.localeCompare(right.target_name) || idOrder;
        }
      });
  }, [data.targets, targetProvisioningFilter, targetSearch, targetSort]);

  const indexerPageCount = Math.max(
    1,
    Math.ceil(filteredIndexers.length / indexerPageSize),
  );
  const currentIndexerPage = Math.min(indexerPage, indexerPageCount);
  const pagedIndexers = filteredIndexers.slice(
    (currentIndexerPage - 1) * indexerPageSize,
    currentIndexerPage * indexerPageSize,
  );
  const targetPageCount = Math.max(
    1,
    Math.ceil(filteredTargets.length / targetPageSize),
  );
  const currentTargetPage = Math.min(targetPage, targetPageCount);
  const pagedTargets = filteredTargets.slice(
    (currentTargetPage - 1) * targetPageSize,
    currentTargetPage * targetPageSize,
  );
  const indexerFiltersActive =
    indexerSearch !== "" ||
    indexerRuntimeFilter !== "ALL" ||
    indexerPublicationFilter !== "ALL";
  const targetFiltersActive =
    targetSearch !== "" || targetProvisioningFilter !== "ALL";

  useEffect(() => {
    if (
      data.services.indexers.state !== "checking" &&
      indexerPage !== currentIndexerPage
    ) {
      setIndexerPage(currentIndexerPage);
    }
  }, [currentIndexerPage, data.services.indexers.state, indexerPage]);

  useEffect(() => {
    if (
      data.services.targets.state !== "checking" &&
      targetPage !== currentTargetPage
    ) {
      setTargetPage(currentTargetPage);
    }
  }, [currentTargetPage, data.services.targets.state, targetPage]);

  const selectedTarget =
    selectedTargetId === null
      ? null
      : (data.targets.find((target) => target.id === selectedTargetId) ?? null);
  const selectedIndexer =
    selectedIndexerId === null
      ? null
      : (data.indexers.find((indexer) => indexer.id === selectedIndexerId) ??
        null);
  const selectedRuntimeIndexer =
    selectedIndexerId === null
      ? null
      : (data.runtimeIndexers.find(
          (indexer) => indexer.indexer_id === selectedIndexerId,
        ) ?? null);

  const changeIndexerRuntimeState = useCallback(
    async (indexer: Indexer, desiredState: Indexer["runtime_state"]) => {
      let mutationFailure: unknown;
      try {
        if (desiredState === "ACTIVE") {
          await activateIndexer(indexer.id, indexer.version);
        } else {
          await deactivateIndexer(indexer.id, indexer.version);
        }
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      await load(controller.signal, true);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const recoverTarget = useCallback(
    async (target: Target) => {
      let mutationFailure: unknown;
      try {
        await recoverTargetProvisioning(target.id, target.version);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      await load(controller.signal, true);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const reconcileRuntime = useCallback(
    async (indexer: Indexer) => {
      let mutationFailure: unknown;
      try {
        await reconcileIndexer(indexer.id);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      await load(controller.signal, true);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const resetQueue = useCallback(
    async (indexer: Indexer) => {
      let mutationFailure: unknown;
      try {
        await resetIndexerQueue(indexer.id, indexer.version);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      await load(controller.signal, true);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  const deleteSelectedIndexer = useCallback(
    async (indexer: Indexer) => {
      let mutationFailure: unknown;
      try {
        await deleteIndexer(indexer.id, indexer.version);
      } catch (error) {
        mutationFailure = error;
      }

      const controller = new AbortController();
      await load(controller.signal, true);
      if (mutationFailure) {
        throw mutationFailure;
      }
    },
    [load],
  );

  return (
    <main className="app-shell">
      <aside className="sidebar">
        <a className="brand" href="#overview" aria-label="Inqwise Indexer home">
          <span className="brand__glyph">IQ</span>
          <span>
            <strong>Inqwise</strong>
            <small>Indexer console</small>
          </span>
        </a>

        <nav className="nav" aria-label="Primary navigation">
          <a
            className={`nav__item${activeSection === "overview" ? " nav__item--active" : ""}`}
            href="#overview"
          >
            <span aria-hidden="true">⌁</span>
            Overview
          </a>
          <a
            className={`nav__item${activeSection === "targets" ? " nav__item--active" : ""}`}
            href="#targets"
          >
            <span aria-hidden="true">◎</span>
            Targets
          </a>
          <a
            className={`nav__item${activeSection === "indexers" ? " nav__item--active" : ""}`}
            href="#indexers"
          >
            <span aria-hidden="true">◇</span>
            Indexers
          </a>
          <a
            className={`nav__item${activeSection === "runtime" ? " nav__item--active" : ""}`}
            href="#runtime"
          >
            <span aria-hidden="true">↯</span>
            Runtime
          </a>
        </nav>

        <div className="sidebar__foot">
          <span className="eyebrow">Environment</span>
          <div className="environment">
            <span className="environment__pulse" />
            Local development
          </div>
          <p>Internal APIs only · Gateway disabled</p>
        </div>
      </aside>

      <section className="workspace">
        <header className="topbar">
          <div>
            <span className="eyebrow">Operations / Local node</span>
            <h1>Indexer overview</h1>
          </div>
          <div className="topbar__actions">
            <div
              aria-live="polite"
              className={`monitor-state${dataStale ? " monitor-state--stale" : ""}`}
            >
              <span aria-hidden="true" />
              {updateStatus}
            </div>
            <label className="poll-control">
              <span>Auto-refresh</span>
              <select
                aria-label="Auto-refresh interval"
                onChange={(event) =>
                  setPollInterval(Number(event.target.value) as PollInterval)
                }
                value={pollInterval}
              >
                <option value={0}>Paused</option>
                <option value={15_000}>15 seconds</option>
                <option value={30_000}>30 seconds</option>
                <option value={60_000}>60 seconds</option>
              </select>
            </label>
            <div className={`node-state node-state--${data.health}`}>
              <span aria-hidden="true" />
              {data.health === "online"
                ? "Node ready"
                : data.health === "checking"
                  ? "Checking node"
                  : "Node offline"}
            </div>
            <button
              className="refresh-button"
              type="button"
              disabled={refreshing}
              onClick={() => {
                const controller = new AbortController();
                void load(controller.signal);
              }}
            >
              <span aria-hidden="true" className={refreshing ? "spin" : ""}>
                ↻
              </span>
              {refreshing ? "Refreshing" : "Refresh"}
            </button>
          </div>
        </header>

        <div className="content" id="overview">
          {degradedServices.length > 0 && (
            <div className="notice" role="status">
              <span className="notice__icon" aria-hidden="true">
                !
              </span>
              <div>
                <strong>Internal services need attention</strong>
                <p>
                  {degradedServices
                    .map(
                      ([name, service]) =>
                        `${SERVICE_LABELS[name]}: ${service.error ?? "Unavailable"}`,
                    )
                    .join(" · ")}
                </p>
              </div>
              {data.health === "offline" && <code>./run-local.sh</code>}
            </div>
          )}

          <section
            aria-label="Internal service diagnostics"
            className="service-diagnostics"
          >
            {(Object.keys(SERVICE_LABELS) as ServiceName[]).map((name) => {
              const service = data.services[name];
              return (
                <article
                  className={`service-diagnostic service-diagnostic--${service.state}`}
                  key={name}
                >
                  <span aria-hidden="true" className="service-diagnostic__dot" />
                  <div>
                    <strong>{SERVICE_LABELS[name]}</strong>
                    <small>
                      {service.state === "checking"
                        ? "Checking"
                        : service.state === "online"
                          ? `Healthy · ${ageLabel(
                              Math.max(
                                0,
                                clock -
                                  (service.lastSuccess?.getTime() ?? clock),
                              ),
                            )}`
                          : service.lastSuccess
                            ? `Degraded · last success ${ageLabel(
                                Math.max(
                                  0,
                                  clock - service.lastSuccess.getTime(),
                                ),
                              )}`
                            : "Unavailable"}
                    </small>
                  </div>
                </article>
              );
            })}
          </section>

          <section className="hero">
            <div>
              <span className="hero__kicker">Control plane</span>
              <h2>Everything indexed, visible at a glance.</h2>
              <p>
                Monitor target readiness, publication state, and local runtime
                convergence from one focused operational view.
              </p>
            </div>
            <div className="hero__signal" aria-hidden="true">
              <span />
              <span />
              <span />
              <i />
            </div>
          </section>

          <section className="metrics" aria-label="Indexer metrics">
            <Metric
              label="Targets"
              value={data.targets.length}
              detail={
                data.targets.length === 0
                  ? "No catalog records"
                  : `${data.targets.filter((target) => target.provisioning_state === "READY").length} ready`
              }
              accent="cyan"
            />
            <Metric
              label="Active indexers"
              value={activeIndexers}
              detail={`${data.runtimeIndexers.length} attached to this node`}
              accent="violet"
            />
            <Metric
              label="Published"
              value={publishedIndexers}
              detail={
                operationalIssues === 0
                  ? "No operational issues"
                  : `${operationalIssues} need attention`
              }
              accent="amber"
            />
          </section>

          <div className="dashboard-grid">
            <section className="panel panel--wide" id="indexers">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Catalog</span>
                  <h3>Indexers</h3>
                </div>
                <span className="panel__count">
                  {filteredIndexers.length} of {data.indexers.length}
                </span>
              </div>

              <div className="filters" aria-label="Indexer filters">
                <label className="filter-field filter-field--search">
                  <span>Search</span>
                  <input
                    onChange={(event) => {
                      setIndexerSearch(event.target.value);
                      setIndexerPage(1);
                    }}
                    placeholder="Name, target, or UID"
                    type="search"
                    value={indexerSearch}
                  />
                </label>
                <label className="filter-field">
                  <span>Runtime</span>
                  <select
                    onChange={(event) => {
                      setIndexerRuntimeFilter(
                        event.target.value as IndexerRuntimeFilter,
                      );
                      setIndexerPage(1);
                    }}
                    value={indexerRuntimeFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="ACTIVE">Active</option>
                    <option value="NON_ACTIVE">Non active</option>
                  </select>
                </label>
                <label className="filter-field">
                  <span>Publication</span>
                  <select
                    onChange={(event) => {
                      setIndexerPublicationFilter(
                        event.target.value as IndexerPublicationFilter,
                      );
                      setIndexerPage(1);
                    }}
                    value={indexerPublicationFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="PUBLISHED">Published</option>
                    <option value="UNPUBLISHED">Unpublished</option>
                    <option value="RETIRED">Retired</option>
                  </select>
                </label>
              </div>

              <div
                className="catalog-controls"
                aria-label="Indexer catalog controls"
              >
                <label>
                  <span>Sort</span>
                  <select
                    onChange={(event) => {
                      setIndexerSort(event.target.value as IndexerSort);
                      setIndexerPage(1);
                    }}
                    value={indexerSort}
                  >
                    <option value="NAME_ASC">Name A–Z</option>
                    <option value="TARGET_ASC">Target A–Z</option>
                    <option value="UPDATED_DESC">Recently updated</option>
                    <option value="VERSION_DESC">Highest version</option>
                  </select>
                </label>
                <label>
                  <span>Rows</span>
                  <select
                    onChange={(event) => {
                      setIndexerPageSize(
                        Number(event.target.value) as CatalogPageSize,
                      );
                      setIndexerPage(1);
                    }}
                    value={indexerPageSize}
                  >
                    {CATALOG_PAGE_SIZES.map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  disabled={!indexerFiltersActive}
                  onClick={() => {
                    setIndexerSearch("");
                    setIndexerRuntimeFilter("ALL");
                    setIndexerPublicationFilter("ALL");
                    setIndexerPage(1);
                  }}
                  type="button"
                >
                  Clear filters
                </button>
              </div>

              {filteredIndexers.length > 0 ? (
                <>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Indexer</th>
                          <th>Target</th>
                          <th>Runtime</th>
                          <th>Publication</th>
                          <th>Version</th>
                        </tr>
                      </thead>
                      <tbody>
                        {pagedIndexers.map((indexer) => (
                          <tr key={indexer.id}>
                            <td>
                              <button
                                className="entity-link"
                                onClick={() => {
                                  setSelectedTargetId(null);
                                  setSelectedIndexerId(indexer.id);
                                }}
                                type="button"
                              >
                                <strong>{indexer.index_name}</strong>
                                <small>
                                  #{indexer.id} · {indexer.role}
                                </small>
                              </button>
                            </td>
                            <td>{indexer.target_name}</td>
                            <td>
                              <div className="runtime-state-cell">
                                <StatusPill value={indexer.runtime_state} />
                                {runtimeDriftByIndexer.has(indexer.id) && (
                                  <span className="drift-label">
                                    {runtimeDriftByIndexer.get(indexer.id)
                                      ?.kind === "MISSING_ATTACHMENT"
                                      ? "not attached"
                                      : "unexpected attachment"}
                                  </span>
                                )}
                              </div>
                            </td>
                            <td>
                              <StatusPill value={indexer.publication_state} />
                            </td>
                            <td className="mono">v{indexer.version}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <Pagination
                    onPageChange={setIndexerPage}
                    page={currentIndexerPage}
                    pageCount={indexerPageCount}
                  />
                </>
              ) : (
                <div className="empty-state">
                  <span aria-hidden="true">◇</span>
                  <strong>
                    {data.indexers.length === 0
                      ? "No indexers yet"
                      : "No indexers match these filters"}
                  </strong>
                  <p>
                    {data.indexers.length === 0
                      ? "Indexers will appear here after a target is provisioned."
                      : "Adjust the search, runtime, or publication filters."}
                  </p>
                </div>
              )}
            </section>

            <section className="panel" id="runtime">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Local node</span>
                  <h3>Runtime</h3>
                </div>
                <span className={`live-dot live-dot--${data.health}`} />
              </div>

              <div className="runtime-summary">
                <div className="runtime-summary__ring">
                  <strong>{data.runtimeIndexers.length}</strong>
                  <span>attached</span>
                </div>
                <div>
                  <p>Reconciler state</p>
                  <strong>
                    {data.health !== "online"
                      ? "Unavailable"
                      : !runtimeComparisonAvailable
                        ? "Diagnostics degraded"
                        : runtimeDrifts.length === 0
                          ? "Converged"
                          : `${runtimeDrifts.length} drift${runtimeDrifts.length === 1 ? "" : "s"}`}
                  </strong>
                  <small>{updateStatus}</small>
                </div>
              </div>

              <div className="runtime-list">
                {data.runtimeIndexers.slice(0, 4).map((indexer) => (
                  <div key={indexer.indexer_id}>
                    <span className="runtime-list__icon">↯</span>
                    <span>
                      <strong>{indexer.index_name}</strong>
                      <small>{indexer.target_name}</small>
                    </span>
                    <StatusPill value={indexer.runtime_state} />
                  </div>
                ))}
                {data.runtimeIndexers.length === 0 && (
                  <p className="runtime-list__empty">
                    No indexers are attached to this node.
                  </p>
                )}
              </div>
              {runtimeDrifts.length > 0 && (
                <div className="runtime-drift" role="status">
                  <span className="eyebrow">Needs convergence</span>
                  {runtimeDrifts.slice(0, 4).map((drift) => (
                    <button
                      key={drift.indexerId}
                      onClick={() => {
                        setSelectedTargetId(null);
                        setSelectedIndexerId(drift.indexerId);
                      }}
                      type="button"
                    >
                      <span>
                        <strong>{drift.indexName}</strong>
                        <small>{drift.targetName}</small>
                      </span>
                      <em>
                        {drift.kind === "MISSING_ATTACHMENT"
                          ? "Not attached"
                          : "Unexpected attachment"}
                      </em>
                    </button>
                  ))}
                </div>
              )}
            </section>

            <section className="panel panel--targets" id="targets">
              <div className="panel__header">
                <div>
                  <span className="eyebrow">Routing destinations</span>
                  <h3>Targets</h3>
                </div>
                <span className="panel__count">
                  {filteredTargets.length} of {data.targets.length}
                </span>
              </div>

              <div className="filters" aria-label="Target filters">
                <label className="filter-field filter-field--search">
                  <span>Search</span>
                  <input
                    onChange={(event) => {
                      setTargetSearch(event.target.value);
                      setTargetPage(1);
                    }}
                    placeholder="Name, period, or UID"
                    type="search"
                    value={targetSearch}
                  />
                </label>
                <label className="filter-field">
                  <span>Provisioning</span>
                  <select
                    onChange={(event) => {
                      setTargetProvisioningFilter(
                        event.target.value as TargetProvisioningFilter,
                      );
                      setTargetPage(1);
                    }}
                    value={targetProvisioningFilter}
                  >
                    <option value="ALL">All</option>
                    <option value="READY">Ready</option>
                    <option value="PROVISIONING">Provisioning</option>
                    <option value="FAILED">Failed</option>
                  </select>
                </label>
              </div>

              <div
                className="catalog-controls"
                aria-label="Target catalog controls"
              >
                <label>
                  <span>Sort</span>
                  <select
                    onChange={(event) => {
                      setTargetSort(event.target.value as TargetSort);
                      setTargetPage(1);
                    }}
                    value={targetSort}
                  >
                    <option value="NAME_ASC">Name A–Z</option>
                    <option value="PERIOD_DESC">Newest period</option>
                    <option value="UPDATED_DESC">Recently updated</option>
                    <option value="STATUS_ASC">Provisioning state</option>
                  </select>
                </label>
                <label>
                  <span>Rows</span>
                  <select
                    onChange={(event) => {
                      setTargetPageSize(
                        Number(event.target.value) as CatalogPageSize,
                      );
                      setTargetPage(1);
                    }}
                    value={targetPageSize}
                  >
                    {CATALOG_PAGE_SIZES.map((size) => (
                      <option key={size} value={size}>
                        {size}
                      </option>
                    ))}
                  </select>
                </label>
                <button
                  disabled={!targetFiltersActive}
                  onClick={() => {
                    setTargetSearch("");
                    setTargetProvisioningFilter("ALL");
                    setTargetPage(1);
                  }}
                  type="button"
                >
                  Clear filters
                </button>
              </div>

              <div className="target-list">
                {pagedTargets.map((target) => (
                  <button
                    className="target-card"
                    key={target.id}
                    onClick={() => {
                      setSelectedIndexerId(null);
                      setSelectedTargetId(target.id);
                    }}
                    type="button"
                  >
                    <span className="target-list__glyph">
                      {target.target_name.slice(0, 1).toUpperCase()}
                    </span>
                    <div>
                      <strong>{target.target_name}</strong>
                      <small>
                        {target.period_key} · #{target.id}
                      </small>
                    </div>
                    <StatusPill value={target.provisioning_state} />
                  </button>
                ))}
                {filteredTargets.length === 0 && (
                  <div className="empty-state empty-state--compact">
                    <span aria-hidden="true">◎</span>
                    <strong>
                      {data.targets.length === 0
                        ? "No targets configured"
                        : "No targets match these filters"}
                    </strong>
                    <p>
                      {data.targets.length === 0
                        ? "Create a target through the internal Admin API."
                        : "Adjust the search or provisioning filter."}
                    </p>
                  </div>
                )}
              </div>
              <Pagination
                onPageChange={setTargetPage}
                page={currentTargetPage}
                pageCount={targetPageCount}
              />
            </section>
          </div>

          <footer>
            <span>
              Last successful update{" "}
              {lastUpdated
                ? `${lastUpdated.toLocaleTimeString([], {
                    hour: "2-digit",
                    minute: "2-digit",
                    second: "2-digit",
                  })} · ${ageLabel(dataAgeMs ?? 0)}${dataStale ? " · stale" : ""}`
                : "—"}
            </span>
            <span>Internal operator console · v0.1</span>
          </footer>
        </div>
      </section>
      <CatalogDetailPanel
        indexer={selectedIndexer}
        onClose={() => {
          setSelectedIndexerId(null);
          setSelectedTargetId(null);
        }}
        onIndexerDelete={deleteSelectedIndexer}
        onQueueReset={resetQueue}
        onRuntimeReconcile={reconcileRuntime}
        onRuntimeStateChange={changeIndexerRuntimeState}
        onTargetRecovery={recoverTarget}
        runtimeIndexer={selectedRuntimeIndexer}
        target={selectedTarget}
      />
    </main>
  );
}
