package com.inqwise.indexer.commands;

import java.util.Objects;

public sealed interface CommandExecutionOutcome {
	record Succeeded() implements CommandExecutionOutcome {
	}

	record Failed(
		CommandFailureKind failureKind,
		Throwable error
	) implements CommandExecutionOutcome {
		public Failed {
			failureKind = Objects.requireNonNull(failureKind, "failureKind");
			error = Objects.requireNonNull(error, "error");
		}
	}

	static CommandExecutionOutcome succeeded() {
		return new Succeeded();
	}

	static CommandExecutionOutcome failed(
		CommandFailureKind failureKind,
		Throwable error
	) {
		return new Failed(failureKind, error);
	}
}
