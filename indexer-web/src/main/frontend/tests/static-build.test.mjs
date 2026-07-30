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
  assert.match(source, /"\/api\/health\/health\/ready"/);
  assert.doesNotMatch(source, /\/gateway\//);
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
  assert.match(styles, /width: min\(1520px, 100%\)/);
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
  assert.match(app, /aria-label="Internal service diagnostics"/);
  assert.match(app, /Target catalog/);
  assert.match(app, /Indexer catalog/);
  assert.match(app, /Local runtime/);
  assert.match(app, /Degraded · last success/);
  assert.match(app, /!runtimeComparisonAvailable/);
  assert.match(app, /Diagnostics degraded/);
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
