package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class CommandFailureClassifierTest {
	private final CommandFailureClassifier classifier = new CommandFailureClassifier();

	@Test
	void preservesExplicitCommandFailureKinds() {
		assertEquals(
			CommandFailureKind.RETRYABLE,
			classifier.classify(CommandFailure.retryable("retry"))
		);
		assertEquals(
			CommandFailureKind.STABLE_INVALID,
			classifier.classify(CommandFailure.stableInvalid("invalid"))
		);
		assertEquals(
			CommandFailureKind.FINAL,
			classifier.classify(CommandFailure.finalFailure("final"))
		);
	}

	@Test
	void preservesWrappedExplicitFailure() {
		assertEquals(
			CommandFailureKind.FINAL,
			classifier.classify(new RuntimeException(
				"wrapper",
				CommandFailure.finalFailure("final")
			))
		);
	}

	@Test
	void causeTypeRuleClassifiesWrappedProviderFailureBeforeCoreDefaults() {
		CommandFailureClassifier configured = new CommandFailureClassifier(List.of(
			CommandFailureClassifier.causeType(
				RetryableProviderException.class,
				CommandFailureKind.RETRYABLE
			)
		));

		assertEquals(
			CommandFailureKind.RETRYABLE,
			configured.classify(new IllegalStateException(
				"wrapper",
				new RetryableProviderException("retry")
			))
		);
	}

	@Test
	void classifiesDomainArgumentAndStateFailuresAsFinal() {
		assertEquals(
			CommandFailureKind.FINAL,
			classifier.classify(new IllegalArgumentException("invalid"))
		);
		assertEquals(
			CommandFailureKind.FINAL,
			classifier.classify(new IllegalStateException("invalid state"))
		);
	}

	@Test
	void configuredRuleRunsBeforeCoreDefaults() {
		CommandFailureClassifier configured = new CommandFailureClassifier(List.of(
			error -> error instanceof SecurityException
				? Optional.of(CommandFailureKind.FINAL)
				: Optional.empty()
		));

		assertEquals(
			CommandFailureKind.FINAL,
			configured.classify(new SecurityException("denied"))
		);
	}

	@Test
	void unknownFailureDefaultsToRetryable() {
		assertEquals(
			CommandFailureKind.RETRYABLE,
			classifier.classify(new RuntimeException("unknown"))
		);
	}

	private static final class RetryableProviderException extends RuntimeException {
		private RetryableProviderException(String message) {
			super(message);
		}
	}
}
