package com.inqwise.indexer.commands;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.inqwise.indexer.errors.RetryableStaleStateException;

public final class CommandFailureClassifier {
	private final List<Rule> providerRules;

	public CommandFailureClassifier() {
		this(List.of());
	}

	public CommandFailureClassifier(List<Rule> providerRules) {
		this.providerRules = List.copyOf(Objects.requireNonNull(
			providerRules,
			"providerRules"
		));
	}

	public CommandFailureKind classify(Throwable error) {
		Objects.requireNonNull(error, "error");
		List<Throwable> causes = causes(error);

		Optional<CommandFailureKind> explicit = causes.stream()
			.filter(CommandFailure.class::isInstance)
			.map(CommandFailure.class::cast)
			.map(CommandFailure::kind)
			.findFirst();
		if (explicit.isPresent()) {
			return explicit.get();
		}

		for (Rule rule : providerRules) {
			Optional<CommandFailureKind> classified = Objects.requireNonNull(
				rule.classify(error),
				"Provider command failure classification"
			);
			if (classified.isPresent()) {
				return classified.get();
			}
		}

		if (causes.stream().anyMatch(RetryableStaleStateException.class::isInstance)) {
			return CommandFailureKind.RETRYABLE;
		}
		if (causes.stream().anyMatch(errorCause ->
			errorCause instanceof IllegalArgumentException
				|| errorCause instanceof IllegalStateException)) {
			return CommandFailureKind.FINAL;
		}

		return CommandFailureKind.RETRYABLE;
	}

	private List<Throwable> causes(Throwable error) {
		java.util.ArrayList<Throwable> causes = new java.util.ArrayList<>();
		Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		Throwable current = error;
		while (current != null && seen.add(current)) {
			causes.add(current);
			current = current.getCause();
		}
		return List.copyOf(causes);
	}

	@FunctionalInterface
	public interface Rule {
		Optional<CommandFailureKind> classify(Throwable error);
	}
}
