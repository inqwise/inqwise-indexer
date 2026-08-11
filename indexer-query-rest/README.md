# Indexer Query REST

`indexer-query-rest` is the consumer-neutral HTTP delivery adapter for the
provider-neutral query services. It depends on `indexer-query`, not on a
consumer, `indexer`, runtime, node composition, load workflow, or Gateway.

The OpenAPI contract exposes two internal operations:

- `GET /reports` returns only validated `ReportPresentation` metadata.
- `POST /reports/{report_name}/executions` accepts the report's user parameter
  object and returns its encoded result object.

The adapter delegates discovery and execution to their independently addressed
Vert.x services. It does not inspect consumer parameters or results and cannot
select caller identity, trusted scope, logical targets, provider queries,
physical indexes, or schema compatibility. Authentication and authorization
remain deployment responsibilities, so the local profile is internal-only.

`indexer-web` consumes this OpenAPI contract but supports only a bounded JSON
Schema subset. Adding schema features requires a concrete consumer need and a
review of rendering, validation, resource, and link-safety behavior.
