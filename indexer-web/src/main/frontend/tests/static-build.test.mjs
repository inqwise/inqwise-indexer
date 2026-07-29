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
