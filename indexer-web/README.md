# Inqwise Indexer Console

Internal operations console for the local Inqwise Indexer node.

The console provides client-side target/indexer search and state filters plus
detail drawers. These projections use the already-loaded generated DTOs and do
not add another API boundary. The first mutation slice is limited to
version-aware indexer activate/deactivate; it refreshes durable state after
every attempt. Failed targets also expose version-aware provisioning recovery.
Ready indexers can request node-local runtime reconciliation without changing
their desired catalog state. Single-indexer queue reset is exposed behind a
separate explicit confirmation step; it advances future writes to a versioned
queue and schedules cleanup of the retired queue. Single-indexer deletion
requires typing the exact indexer name and explains that acceptance fences the
indexer before durable physical cleanup completes. Bulk destructive operations
are not exposed.

Live monitoring supports paused, 15-second, 30-second, and 60-second refresh
intervals. Polling is serialized, pauses while the browser tab is hidden, and
surfaces stale-data age plus desired-runtime/local-attachment drift. Search,
state filters, refresh interval, selected entity, and active section remain in
the URL so an operator view can be refreshed or shared without losing context.
Readiness, target catalog, indexer catalog, and local runtime requests are
tracked independently. A failed envelope keeps its last successful data visible
and reports its own degraded state and last-success age without hiding fresh
results from the other services.

Catalog browsing uses the available desktop width and adds deterministic sort
orders, 10/25/50-row limits, pagination, and clear-filter controls for targets
and indexers. Sort, limit, and page state are persisted alongside the existing
filters in the URL. These remain client-side projections over the loaded Admin
catalog responses and do not add another API boundary.

Entity navigation uses only explicit catalog relationships and stable numeric
identifiers. Indexers link to their `target_id`, targets list their related
indexers, attached runtime entries link to both catalog entities, and detailed
runtime-drift issues open the affected indexer. Links preserve the current URL
filters while replacing the mutually exclusive `target`/`indexer` selection,
so `?target=42#targets` and `?indexer=73#indexers` remain refreshable and
shareable. Lookup maps are built from already-loaded catalogs; navigation does
not add per-entity requests. Missing references are shown explicitly rather
than hidden. Physical index/queue names and arbitrary report-result fields are
never inferred as navigation identities.

The compact Operational Metrics section reads only a bounded projection of the
node's project-logic metrics: accepted/rejected action intake, indexing
outcomes, desired-versus-attached runtime convergence, and lifecycle operation
pending/succeeded/failed/retrying totals.
The Vert.x wrapper forwards `/api/metrics/metrics` to the deployment-configured
metrics endpoint. It does not expose arbitrary metric selection, raw series, or
high-cardinality labels in the rendered UI; the internal proxy still forwards
the deployment-owned Prometheus scrape unchanged. Exported labels are limited
to bounded action type, outcome, runtime state, operation, and role values.

The Operational Issues section is a bounded read-only attention queue derived
from the same loaded service, catalog, runtime, and metrics state. Active
conditions include degraded internal services, failed target/indexer
provisioning, desired-versus-attached runtime drift, and pending lifecycle
work. Inspect actions navigate to the relevant entity or runtime view without
mutating state. Rejected actions and failed/retrying outcomes are displayed
separately as cumulative observations since process start, not mislabeled as
active incidents. At most twelve active issue cards are rendered.

The Node Diagnostics section composes the existing read-only Admin node,
infrastructure, invalid-route, and target-invalidation contracts. It bounds
node services, routing rows, infrastructure items, detail fields, and text;
retains last-good results independently for each diagnostic envelope; and
omits detail keys that look credential-bearing. Invalid-route references use
Indexer/Target ids first and resolve a target name plus optional period only
when exactly one loaded catalog record matches. Missing or ambiguous
references remain visible instead of becoming guessed links. The view does not
probe health, mutate runtime state, accept arbitrary diagnostic selectors, or
render nested infrastructure configuration.

The Definitions & Capabilities section reads the two node-loaded definition
snapshots independently. Target definitions expose period and automatic-write
behavior; indexer definitions expose their logical name, schema identity, and
bounded top-level index/mapping/queue configuration keys without rendering
arbitrary nested values or credential-like keys. A target definition links to
a catalog target only when exactly one currently loaded target has that name.
Multiple periodic targets remain an explicit count, and the console never
infers an indexer-definition relationship from schema or physical names. The
view is read-only and keeps provider configuration separate from catalog state.

The Reports section discovers consumer-neutral parameter/result schemas through
the generic Reports REST adapter. It renders only closed object schemas with
flat scalar inputs and bounded scalar/table results. Unknown keywords, remote
references, deep nesting, incompatible payloads, unsafe links, and oversized
display values are rejected or capped. The frontend imports no consumer code.

The React/Vite workspace is located at `src/main/frontend`; the Java delivery
wrapper uses the standard Maven `src/main/java` layout.

The module follows the Vert.x React SPA pattern:

- Vite serves the React application with hot reload during frontend development.
- Maven builds the static SPA and copies it to the classpath `webroot`.
- The frontend build generates Admin, Runtime, and neutral Reports TypeScript
  contracts from their owning OpenAPI source files.
- `openapi-fetch` provides typed same-origin Admin, Runtime, and Reports clients.
- `IndexerWebVerticle` serves that webroot in packaged/runtime use.
- Static console assets are served without browser caching so a redeployed node cannot retain an older SPA shell.
- Both development and runtime expose the same `/api/{service}` browser paths.
- The Vert.x wrapper forwards those paths to the existing internal REST ports.
- The wrapper also forwards the read-only Prometheus scrape used by the compact
  metrics view.
- The wrapper forwards the neutral Reports API without interpreting schemas,
  parameters, or results.

The wrapper is a delivery adapter. It does not depend on Indexer domain/runtime
classes, EventBus services, or Gateway.

## Hot-reload development

Start the local Indexer node, then:

```sh
cd src/main/frontend
npm install
npm run dev
```

Open `http://localhost:3001`. Vite forwards API requests directly to the local
Indexer services while the packaged console remains available from the node on
port `3000`.

## Vert.x-wrapped runtime

Build the frontend and Java wrapper as dependencies of the node application:

```sh
mvn -pl indexer-node-application -am package
```

Build and start the single node container:

```sh
./run-local.sh
```

`IndexerNodeApplicationVerticle` starts the Indexer node and deploys
`IndexerWebVerticle` in the same Vert.x JVM. The `web` section in
`deployment/local/indexer-node.json` exposes the wrapper on `0.0.0.0:3000` and
uses loopback addresses for the node-owned REST services.

## Verification

```sh
cd src/main/frontend
npm run generate:api
npm run build
npm test
npm run lint
cd ../../../..
mvn -pl indexer-web -am test
```

This interface is not approved for public exposure. Production identity and
authorization remain deployment-owned decisions.
