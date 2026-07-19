package com.inqwise.indexer.commands;

import java.time.Duration;
import java.util.Objects;

public record CommandRetryPolicy(
	int maxAttempts,
	Duration initialDelay,
	Duration maximumDelay,
	int multiplier,
	double jitterRatio
) {
	public CommandRetryPolicy {
		initialDelay = Objects.requireNonNull(initialDelay, "initialDelay");
		maximumDelay = Objects.requireNonNull(maximumDelay, "maximumDelay");
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1");
		}
		if (initialDelay.isNegative()) {
			throw new IllegalArgumentException("initialDelay cannot be negative");
		}
		if (maximumDelay.compareTo(initialDelay) < 0) {
			throw new IllegalArgumentException("maximumDelay cannot be less than initialDelay");
		}
		if (multiplier < 1) {
			throw new IllegalArgumentException("multiplier must be at least 1");
		}
		if (!Double.isFinite(jitterRatio) || jitterRatio < 0 || jitterRatio > 1) {
			throw new IllegalArgumentException("jitterRatio must be between 0 and 1");
		}
		initialDelay.toNanos();
		maximumDelay.toNanos();
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Decides the outcome after {@code failedAttempts} handler executions. The first
	 * failed attempt uses {@link #initialDelay()} when it remains retryable.
	 */
	public CommandRetryDecision decide(
		CommandFailureKind failureKind,
		int failedAttempts,
		double jitterSample
	) {
		Objects.requireNonNull(failureKind, "failureKind");
		if (failedAttempts < 1) {
			throw new IllegalArgumentException("failedAttempts must be at least 1");
		}
		if (!Double.isFinite(jitterSample) || jitterSample < 0 || jitterSample > 1) {
			throw new IllegalArgumentException("jitterSample must be between 0 and 1");
		}
		if (failureKind != CommandFailureKind.RETRYABLE) {
			return CommandRetryDecision.finalFailure();
		}
		if (failedAttempts >= maxAttempts) {
			return CommandRetryDecision.exhausted();
		}

		Duration baseDelay = delayFor(failedAttempts);
		double jitterFactor = 1 - jitterRatio + (jitterRatio * jitterSample);
		long jitteredNanos = Math.round(baseDelay.toNanos() * jitterFactor);
		return CommandRetryDecision.retry(Duration.ofNanos(jitteredNanos));
	}

	private Duration delayFor(int failedAttempts) {
		Duration delay = initialDelay;
		for (int attempt = 1; attempt < failedAttempts; attempt++) {
			if (delay.compareTo(maximumDelay) >= 0) {
				return maximumDelay;
			}
			try {
				delay = delay.multipliedBy(multiplier);
			} catch (ArithmeticException error) {
				return maximumDelay;
			}
			if (delay.compareTo(maximumDelay) > 0) {
				return maximumDelay;
			}
		}
		return delay;
	}

	public static final class Builder {
		private Integer maxAttempts;
		private Duration initialDelay;
		private Duration maximumDelay;
		private Integer multiplier;
		private Double jitterRatio;

		private Builder() {
		}

		public Builder withMaxAttempts(int value) {
			maxAttempts = value;
			return this;
		}

		public Builder withInitialDelay(Duration value) {
			initialDelay = value;
			return this;
		}

		public Builder withMaximumDelay(Duration value) {
			maximumDelay = value;
			return this;
		}

		public Builder withMultiplier(int value) {
			multiplier = value;
			return this;
		}

		public Builder withJitterRatio(double value) {
			jitterRatio = value;
			return this;
		}

		public CommandRetryPolicy build() {
			return new CommandRetryPolicy(
				Objects.requireNonNull(maxAttempts, "maxAttempts"),
				Objects.requireNonNull(initialDelay, "initialDelay"),
				Objects.requireNonNull(maximumDelay, "maximumDelay"),
				Objects.requireNonNull(multiplier, "multiplier"),
				Objects.requireNonNull(jitterRatio, "jitterRatio")
			);
		}
	}
}
