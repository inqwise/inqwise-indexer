package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class CommandRetryPolicyTest {
	@Test
	void finalAndStableInvalidFailuresDoNotRetry() {
		CommandRetryPolicy policy = policy(5, 0);

		assertEquals(
			CommandRetryDecision.finalFailure(),
			policy.decide(CommandFailureKind.FINAL, 1, 0.5)
		);
		assertEquals(
			CommandRetryDecision.finalFailure(),
			policy.decide(CommandFailureKind.STABLE_INVALID, 1, 0.5)
		);
	}

	@Test
	void retryDelayGrowsExponentiallyAndIsCapped() {
		CommandRetryPolicy policy = policy(8, 0);

		assertRetryDelay(policy, 1, Duration.ofSeconds(1));
		assertRetryDelay(policy, 2, Duration.ofSeconds(2));
		assertRetryDelay(policy, 3, Duration.ofSeconds(4));
		assertRetryDelay(policy, 4, Duration.ofSeconds(5));
		assertRetryDelay(policy, 7, Duration.ofSeconds(5));
	}

	@Test
	void jitterIsDeterministicFromProvidedSample() {
		CommandRetryPolicy policy = policy(5, 1);

		assertEquals(
			Duration.ZERO,
			policy.decide(CommandFailureKind.RETRYABLE, 1, 0).delay().orElseThrow()
		);
		assertEquals(
			Duration.ofMillis(500),
			policy.decide(CommandFailureKind.RETRYABLE, 1, 0.5).delay().orElseThrow()
		);
		assertEquals(
			Duration.ofSeconds(1),
			policy.decide(CommandFailureKind.RETRYABLE, 1, 1).delay().orElseThrow()
		);
	}

	@Test
	void retryableFailureBecomesExhaustedAtMaximumAttempts() {
		CommandRetryPolicy policy = policy(3, 0);

		assertEquals(
			CommandRetryDecisionType.RETRY,
			policy.decide(CommandFailureKind.RETRYABLE, 2, 0.5).type()
		);
		assertEquals(
			CommandRetryDecision.exhausted(),
			policy.decide(CommandFailureKind.RETRYABLE, 3, 0.5)
		);
	}

	@Test
	void validatesPolicyAndDecisionInputs() {
		assertThrows(IllegalArgumentException.class, () -> new CommandRetryPolicy(
			0,
			Duration.ofSeconds(1),
			Duration.ofSeconds(5),
			2,
			0.5
		));
		assertThrows(IllegalArgumentException.class, () -> new CommandRetryPolicy(
			3,
			Duration.ofSeconds(5),
			Duration.ofSeconds(1),
			2,
			0.5
		));
		assertThrows(IllegalArgumentException.class, () -> policy(3, 0).decide(
			CommandFailureKind.RETRYABLE,
			0,
			0.5
		));
	}

	private CommandRetryPolicy policy(int maxAttempts, double jitterRatio) {
		return new CommandRetryPolicy(
			maxAttempts,
			Duration.ofSeconds(1),
			Duration.ofSeconds(5),
			2,
			jitterRatio
		);
	}

	private void assertRetryDelay(
		CommandRetryPolicy policy,
		int failedAttempts,
		Duration expected
	) {
		CommandRetryDecision decision = policy.decide(
			CommandFailureKind.RETRYABLE,
			failedAttempts,
			1
		);
		assertEquals(CommandRetryDecisionType.RETRY, decision.type());
		assertEquals(expected, decision.delay().orElseThrow());
	}
}
