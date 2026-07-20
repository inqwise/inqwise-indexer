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

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private CommandFailureKind failureKind;
			private Throwable error;

			private Builder() {
			}

			public Builder withFailureKind(CommandFailureKind value) {
				failureKind = value;
				return this;
			}

			public Builder withError(Throwable value) {
				error = value;
				return this;
			}

			public Failed build() {
				return new Failed(
					Objects.requireNonNull(failureKind, "failureKind"),
					Objects.requireNonNull(error, "error")
				);
			}
		}
	}

	static CommandExecutionOutcome succeeded() {
		return new Succeeded();
	}

	static CommandExecutionOutcome failed(
		CommandFailureKind failureKind,
		Throwable error
	) {
		return Failed.builder()
			.withFailureKind(failureKind)
			.withError(error)
			.build();
	}
}
