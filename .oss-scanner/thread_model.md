# Inqwise Indexer threat model

## Scanner configuration

This file intentionally uses the repository's requested name `thread_model.md`.
Set `threat_model: .oss-scanner/thread_model.md` and
`dockerfile: .oss-scanner/Dockerfile` in the enrollment `project.yaml` submitted
to [Anthropic's OSS Scanner repository](https://github.com/anthropics/oss-scanner).
The default threat-model filename will not discover this file automatically.

## System and assets

Inqwise Indexer is a Java 21 / Vert.x multi-module library and node application
for ingesting document actions, managing logical targets and index lifecycles,
loading or rebuilding datasets, and executing typed reports. The React operator
console exposes operational views through server-side API proxies. The Hacker
News modules are a reference consumer and distribution.

Protect indexed document confidentiality and integrity, trusted report scope,
target/publication metadata, action and command integrity, service availability,
and any deployment credentials. Consult `README.md`, `docs/ARCHITECTURE.md`,
`ROADMAP.md`, the module sources, and OpenAPI resources for current behavior.
Intended invariants below are audit targets, not claims that they are verified.

## Trust boundaries and adversarial inputs

- Treat HTTP bodies, paths, query parameters, headers, document JSON, document
  UIDs, report names/parameters, timestamps, pagination, and source API responses
  as adversarial. Include malformed, oversized, nested, and replayed inputs.
- Producer permission to submit document actions does not imply permission to
  choose a physical index, queue, routing destination, trusted caller identity,
  or administrative operation. Consumer preparation owns schema validation and
  logical document normalization before durable acceptance where configured.
- Report requests carry user parameters only. Caller identity and consumer scope
  come from server-side composition. Report scope, trusted consumer scope, and
  user filters must intersect; user input must never broaden access. Physical
  index resolution belongs to Publication; report definitions and public report
  results must not expose storage identities or accept raw provider queries.
- EventBus services and cluster peers are internal trusted deployment surfaces,
  not an authenticated public API. Nevertheless, transport payload validation
  and preservation of server-owned identity/scope remain in scope. State the
  access needed to reach an internal address in every finding.
- Provider JARs, ServiceLoader registrations, deployment configuration, and
  adapter implementations are trusted operator-installed code. Remote requests
  must not gain the ability to install code, change provider registration, or
  override trusted configuration. Arbitrary execution by an already malicious
  installed Java provider is outside the intended isolation boundary.
- Persistence and queue delivery can be delayed, duplicated, reordered, or fail.
  Audit retries, stale ownership, concurrent lifecycle transitions, cleanup,
  publication, load barriers, and commit ordering for exploitable corruption,
  data loss, cross-target effects, or unbounded resource retention.
- Source record text, URLs, errors, and operational metadata can reach the web
  console. Audit rendering and proxy handling for injection, credential leakage,
  path confusion, and unintended access to internal services.

## Deployment assumptions and scope

The included local and Kubernetes topologies use in-memory adapters and are
evaluation environments. Clustered Vert.x does not make their state durable or
shared. Production deployments supply durable adapters, authentication,
authorization, trusted report context, network isolation, and secret management.
Internal admin/runtime/action/catalog endpoints, diagnostics, health, and
metrics must be exposed only according to deployment policy. Existing gateway
authentication hooks are not automatically protection for every HTTP listener.

Absence of authentication on a deliberately internal evaluation listener is a
deployment limitation; identify an actual violated boundary rather than assume
the example is a hardened public service. Authentication bypass when hooks are
configured, scope widening, unsafe proxying, and untrusted input vulnerabilities
remain in scope. Configuration that permits unexpected exposure should be
reported with its concrete prerequisites.

Prioritize first-party code in `indexer-core`, `indexer`, `indexer-load`,
`indexer-query`, `indexer-query-rest`, `indexer-coordination`, `indexer-events`,
`indexer-node-application`, and `indexer-web`. Consumer example modules are also
in scope, especially source parsing, action preparation, report codecs, and
composition. Check the deployment scripts/manifests for concrete exposure or
secret-handling problems. External production adapters that are not present in
this repository cannot be audited here. Do not submit a dependency version
warning without demonstrating reachability and impact in this project's use.

## Building and reproducing offline

Build with networking available from the repository root:

```sh
docker build -f .oss-scanner/Dockerfile -t inqwise-indexer-oss-scanner .
```

The image keeps the checkout at `/src`, all module build/test outputs, Maven's
cache at `/opt/maven-repository` (selected through `MAVEN_OPTS`), npm's cache at
`/root/.npm`, frontend dependencies under
`indexer-web/src/main/frontend/node_modules`, and Node/npm under
`indexer-web/target/node` (on `PATH`). It runs the complete Java reactor and
frontend static-build tests, then verifies both again without networking.
Java test failures are nonfatal during image setup, following the scanner's
template; compilation and dependency failures still abort it. Inspect each
module's `target/surefire-reports` for failures. The commands below enforce test
success when checking or reproducing a finding.

```sh
docker run --rm --network none inqwise-indexer-oss-scanner \
  mvn --batch-mode --no-transfer-progress --offline verify -Dskip.installnodenpm=true
docker run --rm --network none inqwise-indexer-oss-scanner \
  npm --prefix indexer-web/src/main/frontend test
```

Use `mvn --offline -pl <module> -am test -Dtest=<TestClass>
-Dsurefire.failIfNoSpecifiedTests=false -Dskip.installnodenpm=true` for focused
Java reproductions. Write asynchronous Vert.x tests with `vertx-junit5`.
Loopback HTTP/EventBus tests can run in the offline container; no external queue,
database, cloud service, Docker daemon, or live Hacker News API is required for
the existing suite. The metrics integration test binds port 9090, so do not run
a node concurrently with the full suite in the same container.
During local setup verification, the existing
`HackerNewsIndexerNodeApplicationVerticleTest.startsNodeBeforeIngestionAndStopsInReverseOrder`
failed because shutdown callbacks arrived in the opposite order. Do not assume
the baseline suite is entirely green; report reproduction results separately.

Avoid `mvn clean` for ordinary audit iterations: it removes the bundled Node
installation from `indexer-web/target`. Use the cached `verify` lifecycle above.
Network-dependent Jib, Docker Compose, Kubernetes deployment, and live source
crawling are not offline reproduction steps. Do not use real credentials or
external services in proof-of-concept tests.

## Severity and report preferences

- Critical: reachable code execution or broad destructive compromise from an
  untrusted caller without privileged deployment access.
- High: demonstrated trusted-scope bypass, cross-consumer data disclosure,
  unauthorized destructive operations, or durable integrity loss with realistic
  access prerequisites.
- Medium: bounded authorization/information leaks or reproducible sustained
  denial of service by an ordinary allowed caller, requiring more constrained
  access or configuration.
- Low: limited-impact hardening issues. Explain the actual affected asset.

Rate findings by demonstrated impact and prerequisites, including whether the
attacker needs public HTTP access, producer access, trusted cluster membership,
or operator privileges. Distinguish ordinary correctness failures from security
consequences. Treat already-missing resources during cleanup as expected;
swallowing real storage failures or deleting another target's resources is not.

Each report should include affected paths/lines, an attacker model, necessary
configuration, the violated invariant, expected versus actual behavior, impact,
and a deterministic local test or minimal proof of concept. Prefer small
regression tests and minimal corrective patches that preserve module ownership,
durable/idempotent workflows, and query/runtime separation. Use tabs for Java
and XML indentation and validated model builders in production code. Deduplicate
by root cause and list all affected entry points in that report. Separate bugs
that require independent fixes. Mark unverified hypotheses explicitly.
