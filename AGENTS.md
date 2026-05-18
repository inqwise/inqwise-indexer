# Repository Instructions

- Use tabs for hierarchy indentation in Java and XML files. Do not use leading spaces for indentation in those files.
- Use `vertx-junit5` library tools for tests that exercise Vert.x asynchronous code.
- Treat the project as distributed-oriented. Keep runtime components, persistence, queue/buffer transport, and command orchestration separate.
- Do not put distributed workflow state into `Indexer` unless explicitly requested. Prefer durable commands and idempotent handlers for cross-resource operations.
- Design destructive cleanup as idempotent: missing queue/topic, document index, or repository record should be handled as an expected cleanup miss unless the storage client reports a real failure.
- Keep `Indexer` focused on runtime transport for one model: activate listeners/consumers, pause/resume/commit portions, process action items, emit events, and clean up its own resources.
- Keep command orchestration generic where possible. Use the generic `CommandService` layer for lifecycle/workflow commands instead of indexer-specific command infrastructure unless the domain requires it.
- Document important accepted modules, designs, and solutions in `README.md` as part of the change that introduces or approves them.
- Add important uncovered flows, deferred design questions, and known follow-up decisions to the roadmap instead of leaving them only in conversation or code comments.
- Periodically refactor `README.md` and the roadmap so they form coherent, complete documents rather than scattered accumulated notes.
- For scoped multi-step work, create or update a local progress checklist file such as `PROGRESS.md`. Record accepted decisions, implementation tasks, deferred items, and verification status, and keep checklist item statuses current as the work progresses. Treat progress checklist files as local working artifacts; keep them git-ignored rather than committed.
- After meaningful design or implementation changes, periodically review the implementation shape before committing or starting the next scope. Look for ownership mismatches, lifecycle gaps, distributed-state leakage, unclear boundaries, and missing tests.
