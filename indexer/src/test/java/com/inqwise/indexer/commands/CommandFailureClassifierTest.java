package com.inqwise.indexer.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.errors.RetryableStaleStateException;

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
	void classifiesStaleStateAsRetryable() {
		assertEquals(
			CommandFailureKind.RETRYABLE,
			classifier.classify(new RetryableStaleStateException("stale"))
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
	void providerRuleRunsBeforeCoreDefaults() {
		CommandFailureClassifier providerClassifier = new CommandFailureClassifier(List.of(
			error -> error instanceof SecurityException
				? Optional.of(CommandFailureKind.FINAL)
				: Optional.empty()
		));

		assertEquals(
			CommandFailureKind.FINAL,
			providerClassifier.classify(new SecurityException("denied"))
		);
	}

	@Test
	void unknownFailureDefaultsToRetryable() {
		assertEquals(
			CommandFailureKind.RETRYABLE,
			classifier.classify(new RuntimeException("unknown"))
		);
	}
}
