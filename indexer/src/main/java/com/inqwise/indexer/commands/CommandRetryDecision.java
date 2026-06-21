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
		return new CommandRetryDecision(
			CommandRetryDecisionType.RETRY,
			Optional.of(Objects.requireNonNull(delay, "delay"))
		);
	}

	public static CommandRetryDecision finalFailure() {
		return new CommandRetryDecision(
			CommandRetryDecisionType.FINAL_FAILURE,
			Optional.empty()
		);
	}

	public static CommandRetryDecision exhausted() {
		return new CommandRetryDecision(
			CommandRetryDecisionType.EXHAUSTED,
			Optional.empty()
		);
	}
}
