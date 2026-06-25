package com.inqwise.indexer.commands;

import java.util.Objects;

public class CommandFailure extends RuntimeException {
	private final CommandFailureKind kind;

	private CommandFailure(CommandFailureKind kind, String message, Throwable cause) {
		super(message, cause);
		this.kind = Objects.requireNonNull(kind, "kind");
	}

	public static CommandFailure retryable(String message) {
		return new CommandFailure(CommandFailureKind.RETRYABLE, message, null);
	}

	public static CommandFailure retryable(String message, Throwable cause) {
		return new CommandFailure(CommandFailureKind.RETRYABLE, message, cause);
	}

	public static CommandFailure finalFailure(String message) {
		return new CommandFailure(CommandFailureKind.FINAL, message, null);
	}

	public static CommandFailure finalFailure(String message, Throwable cause) {
		return new CommandFailure(CommandFailureKind.FINAL, message, cause);
	}

	public static CommandFailure stableInvalid(String message) {
		return new CommandFailure(CommandFailureKind.STABLE_INVALID, message, null);
	}

	public static CommandFailure stableInvalid(String message, Throwable cause) {
		return new CommandFailure(CommandFailureKind.STABLE_INVALID, message, cause);
	}

	public CommandFailureKind kind() {
		return kind;
	}

	public boolean retryable() {
		return kind == CommandFailureKind.RETRYABLE;
	}

	public boolean stableInvalid() {
		return kind == CommandFailureKind.STABLE_INVALID;
	}
}
