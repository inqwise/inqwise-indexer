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
    new URL("../src/App.tsx", import.meta.url),
    "utf8",
  );

  assert.match(source, /"\/api\/admin\/admin\/targets"/);
  assert.match(source, /"\/api\/admin\/admin\/indexers"/);
  assert.match(source, /"\/api\/runtime\/runtime\/status"/);
  assert.match(source, /"\/api\/health\/health\/ready"/);
  assert.doesNotMatch(source, /\/gateway\//);
});
