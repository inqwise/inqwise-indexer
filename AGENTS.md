# Repository Instructions

- Use tabs for hierarchy indentation in Java and XML files. Do not use leading spaces for indentation in those files.
- Use `vertx-junit5` library tools for tests that exercise Vert.x asynchronous code.
- Treat the project as distributed-oriented. Keep runtime components, persistence, queue/buffer transport, and command orchestration separate.
- Do not put distributed workflow state into `Indexer` unless explicitly requested. Prefer durable commands and idempotent handlers for cross-resource operations.
- Design destructive cleanup as idempotent: missing queue/topic, document index, or repository record should be handled as an expected cleanup miss unless the storage client reports a real failure.
- Keep `Indexer` focused on runtime transport for one model: activate listeners/consumers, pause/resume/commit portions, process action items, emit events, and clean up its own resources.
- Keep command orchestration generic where possible. Use the generic `CommandService` layer for lifecycle/workflow commands instead of indexer-specific command infrastructure unless the domain requires it.
