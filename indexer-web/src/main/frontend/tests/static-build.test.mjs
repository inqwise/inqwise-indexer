import assert from "node:assert/strict";
import { access, readFile, readdir } from "node:fs/promises";
import test from "node:test";

test("builds a static React application for the Vert.x webroot", async () => {
  const html = await readFile(
    new URL("../dist/index.html", import.meta.url),
    "utf8",
  );
  const assets = await readdir(new URL("../dist/assets/", import.meta.url));

  assert.match(html, /<title>Inqwise Indexer Console<\/title>/);
  assert.match(html, /<div id="root"><\/div>/);
  assert.match(html, /src="\/assets\/[^"]+\.js"/);
  assert.ok(assets.some((asset) => asset.endsWith(".js")));
  assert.ok(assets.some((asset) => asset.endsWith(".css")));
  await access(new URL("../dist/og.png", import.meta.url));
});

test("keeps the browser API surface same-origin and Gateway-independent", async () => {
  const source = await readFile(
    new URL("../src/api/indexer-api.ts", import.meta.url),
    "utf8",
  );

  assert.match(source, /baseUrl: "\/api\/admin"/);
  assert.match(source, /GET\("\/admin\/targets"/);
  assert.match(source, /GET\("\/admin\/indexers"/);
  assert.match(source, /baseUrl: "\/api\/runtime"/);
  assert.match(source, /GET\("\/runtime\/status"/);
  assert.match(source, /GET\("\/admin\/node\/status"/);
  assert.match(source, /POST\(\s*"\/admin\/node\/recover"/);
  assert.match(source, /"\/admin\/infrastructure\/status"/);
  assert.match(source, /"\/admin\/routing\/invalid-routes"/);
  assert.match(source, /"\/admin\/routing\/target-invalidations"/);
  assert.match(source, /"\/admin\/definitions\/targets"/);
  assert.match(source, /"\/admin\/definitions\/indexers"/);
  assert.match(source, /"\/api\/health\/health\/ready"/);
  assert.match(source, /return response\.ok/);
  assert.match(source, /"\/api\/metrics\/metrics"/);
  assert.doesNotMatch(source, /\/gateway\//);
});

test("guards node recovery behind recovery-only diagnostics state", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const view = await readFile(
    new URL("../src/components/NodeDiagnosticsView.tsx", import.meta.url),
    "utf8",
  );

  assert.match(view, /node\.recovery_only &&/);
  assert.match(view, /onRecoverNode: \(\) => Promise<void>/);
  assert.match(view, /recoveryPending \? "Recovering…" : "Recover node"/);
  assert.match(view, /setRecoveryError\(errorMessage\(error\)\)/);
  assert.match(view, /role="alert"/);
  assert.match(app, /await recoverNode\(\)/);
  assert.match(app, /await load\(controller\.signal, true\)/);
  assert.match(app, /onRecoverNode=\{recoverCurrentNode\}/);
});

test("renders bounded read-only load workflow visibility with explicit identities", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const view = await readFile(
    new URL("../src/components/LoadOperationsView.tsx", import.meta.url),
    "utf8",
  );
  const api = await readFile(
    new URL("../src/api/load-query-api.ts", import.meta.url),
    "utf8",
  );
  const vite = await readFile(
    new URL("../vite.config.ts", import.meta.url),
    "utf8",
  );

  assert.match(app, /<strong>Load operations<\/strong>/);
  assert.match(app, /<LoadOperationsView/);
  assert.match(api, /baseUrl: "\/api\/loads"/);
  assert.match(api, /GET\("\/admin\/loads"/);
  assert.match(api, /max: 50/);
  assert.match(vite, /"\/api\/loads"/);
  assert.match(view, /MAX_VISIBLE_LOADS = 25/);
  assert.match(view, /MAX_TEXT = 180/);
  assert.match(view, /destination=\{\{ kind, id \}\}/);
  assert.match(view, /Missing #/);
  assert.doesNotMatch(view, /source_query|dangerouslySetInnerHTML/);
  assert.doesNotMatch(view, /POST\(|DELETE\(|PUT\(/);
});

test("generates dashboard contracts from the Java REST OpenAPI schemas", async () => {
  const adminTypes = await readFile(
    new URL("../src/generated/admin-api.ts", import.meta.url),
    "utf8",
  );
  const runtimeTypes = await readFile(
    new URL("../src/generated/runtime-api.ts", import.meta.url),
    "utf8",
  );

  await assertGeneratedFields(
    "../../../../../indexer/src/main/java/com/inqwise/indexer/service/admin/AdminTargetView.java",
    adminTypes,
  );
  await assertGeneratedFields(
    "../../../../../indexer/src/main/java/com/inqwise/indexer/service/admin/AdminIndexerView.java",
    adminTypes,
  );
  await assertGeneratedFields(
    "../../../../../indexer/src/main/java/com/inqwise/indexer/service/runtime/RuntimeIndexerStatus.java",
    runtimeTypes,
  );
  assert.match(adminTypes, /ErrorBody:/);
  assert.match(runtimeTypes, /ErrorBody:/);
});

test("uses generated DTOs instead of handwritten dashboard response models", async () => {
  const source = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );

  assert.match(source, /from "\.\/api\/indexer-api"/);
  assert.doesNotMatch(source, /type (Target|Indexer|RuntimeIndexer) = \{/);
  assert.doesNotMatch(source, /getJson</);
});

test("uses node-local hot routing diagnostics for target state", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const details = await readFile(
    new URL("../src/components/CatalogDetailPanel.tsx", import.meta.url),
    "utf8",
  );

  assert.match(app, /hotTargets\(signal\)/);
  assert.match(app, /hotTargetsById\.get\(target\.id\)/);
  assert.match(details, /actual hot writer/);
  assert.match(details, /not loaded in the node-local hot routing view/);
  assert.doesNotMatch(app, /LIVE_WRITER.*WRITABLE|WRITABLE.*LIVE_WRITER/s);
});

test("provides catalog filters and accessible entity details", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const details = await readFile(
    new URL("../src/components/CatalogDetailPanel.tsx", import.meta.url),
    "utf8",
  );
  assert.match(app, /aria-label="Indexer filters"/);
  assert.match(app, /aria-label="Target filters"/);
  assert.match(app, /type="search"/);
  assert.match(details, /data-testid="catalog-detail-panel"/);
  assert.match(details, /aria-label="Close details"/);
  assert.match(details, /event\.key === "Escape"/);
});

test("links catalog entities through explicit stable identifiers", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const details = await readFile(
    new URL("../src/components/CatalogDetailPanel.tsx", import.meta.url),
    "utf8",
  );
  const link = await readFile(
    new URL("../src/components/EntityLink.tsx", import.meta.url),
    "utf8",
  );
  const navigation = await readFile(
    new URL("../src/entity-navigation.ts", import.meta.url),
    "utf8",
  );
  const issues = await readFile(
    new URL("../src/components/OperationalIssuesView.tsx", import.meta.url),
    "utf8",
  );

  assert.match(navigation, /kind: "target"; id: number/);
  assert.match(navigation, /kind: "indexer"; id: number/);
  assert.match(navigation, /searchParams\.delete\("indexer"\)/);
  assert.match(navigation, /searchParams\.delete\("target"\)/);
  assert.match(link, /history\.pushState/);
  assert.match(details, /Related indexers/);
  assert.match(details, /Missing target #/);
  assert.match(details, /destination=\{\{ kind: "target", id: indexer\.target_id \}\}/);
  assert.match(app, /targetsById/);
  assert.match(app, /indexersById/);
  assert.match(app, /destination=\{\{ kind: "indexer", id: indexer\.indexer_id \}\}/);
  assert.match(issues, /destination: \{ section: "indexers", id: drift\.indexerId \}/);
  assert.doesNotMatch(navigation, /index_name|queue_name|physical/i);
});

test("organizes the console into local and system workspaces with an entity hierarchy", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const hierarchy = await readFile(
    new URL("../src/components/SystemHierarchyView.tsx", import.meta.url),
    "utf8",
  );
  const styles = await readFile(
    new URL("../src/globals.css", import.meta.url),
    "utf8",
  );

  assert.match(app, /type DashboardSection = "local" \| "system"/);
  assert.match(app, /className="brand" href="#system"/);
  assert.match(app, /href="#local"/);
  assert.match(app, /href="#system"/);
  assert.match(app, /const LOCAL_SECTIONS = new Set/);
  assert.match(app, /function currentSection[\s\S]*return "system";/);
  assert.match(app, /hidden=\{activeSection !== "local"\}/);
  assert.match(app, /hidden=\{activeSection !== "system"\}/);
  assert.match(app, /<details className="subpanel"/);
  assert.match(app, /<SystemHierarchyView/);
  assert.match(app, /className="local-overview-grid"/);
  assert.match(app, /<h2>Operational overview<\/h2>/);
  assert.doesNotMatch(app, /className="hero"/);
  assert.doesNotMatch(app, /aria-label="Indexer metrics"/);
  assert.match(hierarchy, /indexersByTarget/);
  assert.match(hierarchy, /attachedIds/);
  assert.match(hierarchy, /hotIndexerIds\.has\(indexer\.id\)/);
  assert.match(hierarchy, /destination=\{\{ kind: "target", id: target\.id \}\}/);
  assert.match(hierarchy, /destination=\{\{ kind: "indexer", id: indexer\.id \}\}/);
  assert.match(hierarchy, /MAX_VISIBLE_TARGETS = 30/);
  assert.match(hierarchy, /MAX_VISIBLE_INDEXERS = 8/);
  assert.match(styles, /\.diagnostics-block--infrastructure\s*\{[^}]*grid-column: 1 \/ -1/s);
  assert.match(styles, /\.diagnostics-block--node\s*\{[^}]*grid-column: 1 \/ -1/s);
  assert.match(styles, /@media \(max-width: 1100px\)[\s\S]*?\.diagnostics-grid\s*\{[^}]*grid-template-columns: 1fr/s);
  assert.match(styles, /\.diagnostics-services\s*\{[^}]*repeat\(auto-fit, minmax\(160px, 1fr\)\)/s);
  assert.match(styles, /\.local-runtime__attachments\s*\{[^}]*repeat\(auto-fit, minmax\(min\(100%, 260px\), 1fr\)\)/s);
  assert.match(styles, /\.local-overview-grid\s*\{[^}]*grid-template-columns: minmax\(0, 1\.2fr\) minmax\(320px, 0\.8fr\)/s);
  assert.match(styles, /\.compact-metrics-grid\s*\{[^}]*grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/s);
  assert.match(styles, /\.diagnostics-view__header p\s*\{[^}]*font-size: 13px/s);
  assert.match(styles, /\.node-facts strong\s*\{[^}]*font-size: 12px/s);
  assert.match(styles, /\.infrastructure-list article > small\s*\{[^}]*font-size: 11px/s);
});

test("provides sortable paged catalogs with URL-persisted navigation", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const styles = await readFile(
    new URL("../src/globals.css", import.meta.url),
    "utf8",
  );

  assert.match(app, /aria-label="Indexer catalog controls"/);
  assert.match(app, /aria-label="Target catalog controls"/);
  assert.match(app, /Recently updated/);
  assert.match(app, /Clear filters/);
  assert.match(app, /CATALOG_PAGE_SIZES/);
  assert.match(app, /<Pagination/);
  assert.match(app, /setOptional\("isort"/);
  assert.match(app, /setOptional\("ipage"/);
  assert.match(app, /setOptional\("tsort"/);
  assert.match(app, /setOptional\("tpage"/);
  assert.match(app, /services\.indexers\.state !== "checking"/);
  assert.match(styles, /width: min\(1320px, 100%\)/);
  assert.match(styles, /@media \(min-width: 1380px\)/);
});

test("provides visibility-aware live monitoring and URL-persisted view state", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const styles = await readFile(
    new URL("../src/globals.css", import.meta.url),
    "utf8",
  );

  assert.match(app, /aria-label="Auto-refresh interval"/);
  assert.match(app, /queryInterval === null/);
  assert.match(app, /document\.visibilityState === "visible"/);
  assert.match(app, /visibilitychange/);
  assert.match(app, /activeLoadRef/);
  assert.match(app, /refreshAfterActive/);
  assert.match(app, /window\.history\.replaceState/);
  assert.match(app, /queryValue\("iq"\)/);
  assert.match(app, /queryId\("indexer"\)/);
  assert.match(app, /currentSection/);
  assert.match(app, /scrollIntoView/);
  assert.match(app, /MISSING_ATTACHMENT/);
  assert.match(app, /UNEXPECTED_ATTACHMENT/);
  assert.match(app, /Data stale/);
  assert.match(app, /Last successful update/);
  assert.match(styles, /\.topbar\s*\{[^}]*position: sticky/s);
  assert.match(styles, /#runtime,[^}]*scroll-margin-top: 110px/s);
});

test("keeps last-good data while reporting internal services independently", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );

  assert.match(app, /Promise\.allSettled/);
  assert.match(app, /current\.targets/);
  assert.match(app, /current\.indexers/);
  assert.match(app, /current\.runtimeIndexers/);
  assert.doesNotMatch(app, /aria-label="Internal service diagnostics"/);
  assert.match(app, /Internal services need attention/);
  assert.match(app, /Target catalog/);
  assert.match(app, /Indexer catalog/);
  assert.match(app, /Local runtime/);
  assert.match(app, /!runtimeComparisonAvailable/);
  assert.match(app, /Runtime status unavailable/);
});

test("shows a bounded compact view of project-logic metrics", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const api = await readFile(
    new URL("../src/api/indexer-api.ts", import.meta.url),
    "utf8",
  );
  const vite = await readFile(
    new URL("../vite.config.ts", import.meta.url),
    "utf8",
  );

  assert.match(app, /aria-label="Operational metrics"/);
  assert.match(app, /Action intake/);
  assert.match(app, /Indexing outcomes/);
  assert.match(app, /Runtime convergence/);
  assert.match(app, /Lifecycle operations/);
  assert.match(api, /parseOperationalMetrics/);
  assert.match(api, /inqwise_indexer_action_intake_total/);
  assert.match(api, /inqwise_indexer_action_processing_seconds_count/);
  assert.match(api, /inqwise_indexer_runtime_convergence/);
  assert.match(api, /inqwise_indexer_lifecycle_operations_total/);
  assert.match(vite, /"\/api\/metrics"/);
  assert.doesNotMatch(app, /document id|request id|queue name/i);
});

test("renders bounded read-only report activity through neutral contracts", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const view = await readFile(
    new URL("../src/components/ReportsView.tsx", import.meta.url),
    "utf8",
  );
  const metrics = await readFile(
    new URL("../src/api/indexer-api.ts", import.meta.url),
    "utf8",
  );
  const api = await readFile(
    new URL("../src/api/reports-api.ts", import.meta.url),
    "utf8",
  );
  const generated = await readFile(
    new URL("../src/generated/reports-api.ts", import.meta.url),
    "utf8",
  );
  const vite = await readFile(
    new URL("../vite.config.ts", import.meta.url),
    "utf8",
  );

  assert.match(app, /<strong>Report activity<\/strong>/);
  assert.match(app, /<ReportsView/);
  assert.match(api, /baseUrl: "\/api\/reports"/);
  assert.match(api, /GET\("\/reports"/);
  assert.doesNotMatch(api, /POST\(|executions/);
  assert.match(generated, /ReportPresentation:/);
  assert.match(view, /MAX_DISCOVERED_REPORTS = 256/);
  assert.match(view, /Available reports/);
  assert.match(view, /Executions observed/);
  assert.match(view, /succeeded \/ invalid \/ failed/);
  assert.match(view, /Average duration/);
  assert.match(metrics, /inqwise_indexer_report_executions_total/);
  assert.match(metrics, /inqwise_indexer_report_executions_active/);
  assert.match(metrics, /inqwise_indexer_report_execution_duration_seconds_total/);
  assert.doesNotMatch(view, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(view, /hacker.news/i);
  assert.doesNotMatch(view, /<form|Run report|result_schema|parameters_schema/);
  assert.match(vite, /"\/api\/reports"/);
});

test("derives a bounded operational attention queue from project state", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const issues = await readFile(
    new URL("../src/components/OperationalIssuesView.tsx", import.meta.url),
    "utf8",
  );

  assert.match(app, /href="#issues"/);
  assert.match(app, /<OperationalIssuesView/);
  assert.match(issues, /MAX_VISIBLE_ACTIVE_ISSUES = 12/);
  assert.match(issues, /provisioning_state === "FAILED"/);
  assert.match(issues, /MISSING_ATTACHMENT/);
  assert.match(issues, /lifecyclePending/);
  assert.match(issues, /Cumulative counters are signals, not necessarily active incidents/);
  assert.match(issues, /onSelectTarget/);
  assert.match(issues, /onSelectIndexer/);
  assert.doesNotMatch(issues, /hacker.news/i);
});

test("renders bounded read-only node diagnostics with explicit entity resolution", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const diagnostics = await readFile(
    new URL("../src/components/NodeDiagnosticsView.tsx", import.meta.url),
    "utf8",
  );

  assert.match(app, /id="local-diagnostics"/);
  assert.match(app, /<NodeDiagnosticsView/);
  assert.match(diagnostics, /MAX_NODE_SERVICES = 32/);
  assert.match(diagnostics, /MAX_ROUTING_ITEMS = 12/);
  assert.match(diagnostics, /MAX_INFRASTRUCTURE_ITEMS = 40/);
  assert.match(diagnostics, /MAX_DETAILS_PER_ITEM = 8/);
  assert.match(diagnostics, /SENSITIVE_DETAIL/);
  assert.match(diagnostics, /matches\.length === 1/);
  assert.match(diagnostics, /Ambiguous target/);
  assert.match(diagnostics, /Missing indexer #/);
  assert.match(diagnostics, /Missing target #/);
  assert.match(diagnostics, /<EntityLink/);
  assert.ok(
    diagnostics.indexOf('title="Infrastructure adapters"') <
      diagnostics.indexOf('title="Invalid routes"'),
  );
  assert.ok(
    diagnostics.indexOf('title="Invalid routes"') <
      diagnostics.indexOf('title="Target invalidations"'),
  );
  assert.doesNotMatch(diagnostics, /dangerouslySetInnerHTML/);
  assert.doesNotMatch(diagnostics, /POST\(|DELETE\(|PUT\(/);
  assert.doesNotMatch(diagnostics, /hacker.news/i);
});

test("renders bounded read-only definitions without inferred catalog identity", async () => {
  const app = await readFile(
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );
  const definitions = await readFile(
    new URL("../src/components/DefinitionsView.tsx", import.meta.url),
    "utf8",
  );

  assert.match(app, /id="configuration"/);
  assert.match(app, /<DefinitionsView/);
  assert.match(definitions, /MAX_DEFINITIONS = 24/);
  assert.match(definitions, /MAX_CONFIGURATION_KEYS = 12/);
  assert.match(definitions, /SENSITIVE_KEY/);
  assert.match(definitions, /matches\.length === 1/);
  assert.match(definitions, /no single link/);
  assert.match(definitions, /no\s+relationship is inferred/);
  assert.match(definitions, /<EntityLink/);
  assert.doesNotMatch(definitions, /JSON\.stringify|dangerouslySetInnerHTML/);
  assert.doesNotMatch(definitions, /POST\(|DELETE\(|PUT\(/);
  assert.doesNotMatch(definitions, /hacker.news/i);
});

test("limits mutations to bounded and explicitly confirmed operator changes", async () => {
  const details = await readFile(
    new URL("../src/components/CatalogDetailPanel.tsx", import.meta.url),
    "utf8",
  );
  const api = await readFile(
    new URL("../src/api/indexer-api.ts", import.meta.url),
    "utf8",
  );

  assert.match(api, /POST\(\s*"\/admin\/indexers\/\{id\}\/activate"/);
  assert.match(api, /POST\(\s*"\/admin\/indexers\/\{id\}\/deactivate"/);
  assert.match(
    api,
    /POST\(\s*"\/admin\/targets\/\{id\}\/recover-provisioning"/,
  );
  assert.match(
    api,
    /POST\(\s*"\/runtime\/indexers\/\{id\}\/reconcile"/,
  );
  assert.match(
    api,
    /POST\(\s*"\/admin\/indexers\/\{id\}\/reset-queue"/,
  );
  assert.match(api, /DELETE\(\s*"\/admin\/indexers\/\{id\}"/);
  assert.equal(api.match(/expected_version: expectedVersion/g)?.length, 5);
  assert.equal(api.match(/\.DELETE\(/g)?.length, 1);
  assert.doesNotMatch(api, /\.(PUT|PATCH)\(/);
  assert.match(details, /Activate indexer/);
  assert.match(details, /Deactivate indexer/);
  assert.match(details, /Recover provisioning/);
  assert.match(details, /Reconcile local runtime/);
  assert.match(details, /Review queue reset/);
  assert.match(details, /Confirm queue reset/);
  assert.match(details, /Old in-flight\s+items are not synchronously guaranteed to stop/);
  assert.match(details, /Review indexer deletion/);
  assert.match(details, /Confirm indexer deletion/);
  assert.match(details, /deleteConfirmationText !== indexer\.index_name/);
  assert.match(details, /This cannot be undone/);
  assert.match(details, /Acceptance starts durable\s+cleanup/);
  assert.match(details, /This does not change the desired catalog state/);
  assert.match(details, /target\.provisioning_state === "FAILED"/);
  assert.match(details, /role="alert"/);
});

async function assertGeneratedFields(javaPath, generatedTypes) {
  const source = await readFile(new URL(javaPath, import.meta.url), "utf8");
  const fields = Array.from(
    source.matchAll(/public static final String \w+ = "([^"]+)";/g),
    (match) => match[1],
  );

  assert.ok(fields.length > 0, `No serialized fields found in ${javaPath}`);
  for (const field of fields) {
    assert.match(generatedTypes, new RegExp(`\\b${field}:`));
  }
}
