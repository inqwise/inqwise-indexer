package com.inqwise.indexer.commands;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class CommandFailureClassifier {
	private final List<Rule> rules;

	public CommandFailureClassifier() {
		this(List.of());
	}

	public CommandFailureClassifier(List<Rule> rules) {
		this.rules = List.copyOf(Objects.requireNonNull(
			rules,
			"rules"
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

		for (Rule rule : rules) {
			Optional<CommandFailureKind> classified = Objects.requireNonNull(
				rule.classify(error),
				"Command failure rule classification"
			);
			if (classified.isPresent()) {
				return classified.get();
			}
		}

		if (causes.stream().anyMatch(errorCause ->
			errorCause instanceof IllegalArgumentException
				|| errorCause instanceof IllegalStateException)) {
			return CommandFailureKind.FINAL;
		}

		return CommandFailureKind.RETRYABLE;
	}

	public static Rule causeType(
		Class<? extends Throwable> errorType,
		CommandFailureKind kind
	) {
		Objects.requireNonNull(errorType, "errorType");
		Objects.requireNonNull(kind, "kind");
		return error -> causes(error).stream().anyMatch(errorType::isInstance)
			? Optional.of(kind)
			: Optional.empty();
	}

	private static List<Throwable> causes(Throwable error) {
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
