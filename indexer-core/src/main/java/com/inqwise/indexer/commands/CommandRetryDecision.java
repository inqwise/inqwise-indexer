package com.inqwise.indexer.commands;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

public record CommandRetryDecision(
	CommandRetryDecisionType type,
	Optional<Duration> delay
) {
	public CommandRetryDecision {
		type = Objects.requireNonNull(type, "type");
		delay = Objects.requireNonNull(delay, "delay");
		if (type == CommandRetryDecisionType.RETRY && delay.isEmpty()) {
			throw new IllegalArgumentException("Retry decision requires delay");
		}
		if (type != CommandRetryDecisionType.RETRY && delay.isPresent()) {
			throw new IllegalArgumentException("Terminal decision cannot have delay");
		}
		if (delay.filter(Duration::isNegative).isPresent()) {
			throw new IllegalArgumentException("Retry delay cannot be negative");
		}
	}

	public static CommandRetryDecision retry(Duration delay) {
		return builder()
			.withType(CommandRetryDecisionType.RETRY)
			.withDelay(delay)
			.build();
	}

	public static CommandRetryDecision finalFailure() {
		return builder()
			.withType(CommandRetryDecisionType.FINAL_FAILURE)
			.build();
	}

	public static CommandRetryDecision exhausted() {
		return builder()
			.withType(CommandRetryDecisionType.EXHAUSTED)
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private CommandRetryDecisionType type;
		private Duration delay;

		private Builder() {
		}

		public Builder withType(CommandRetryDecisionType value) {
			type = value;
			return this;
		}

		public Builder withDelay(Duration value) {
			delay = value;
			return this;
		}

		public CommandRetryDecision build() {
			return new CommandRetryDecision(
				Objects.requireNonNull(type, "type"),
				Optional.ofNullable(delay)
			);
		}
	}
}
