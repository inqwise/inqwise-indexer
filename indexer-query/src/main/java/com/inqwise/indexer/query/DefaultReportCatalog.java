package com.inqwise.indexer.query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultReportCatalog implements ReportCatalog {
	private final Map<String, ReportDefinition<?, ?>> definitions;

	private DefaultReportCatalog(Builder builder) {
		Map<String, ReportDefinition<?, ?>> registered = new LinkedHashMap<>();
		for (ReportDefinition<?, ?> definition : builder.definitions) {
			Objects.requireNonNull(definition, "definition");
			ReportDescriptor descriptor = Objects.requireNonNull(
				definition.descriptor(),
				"definition.descriptor"
			);
			ReportDefinition<?, ?> previous = registered.putIfAbsent(
				descriptor.name(),
				definition
			);
			if (previous != null) {
				throw new IllegalArgumentException(
					"Duplicate report definition: " + descriptor.name()
				);
			}
		}
		definitions = Map.copyOf(registered);
	}

	@Override
	public Optional<ReportDefinition<?, ?>> find(String reportName) {
		if (reportName == null || reportName.isBlank()) {
			return Optional.empty();
		}
		return Optional.ofNullable(definitions.get(reportName));
	}

	@Override
	public Collection<ReportDescriptor> descriptors() {
		return definitions.values().stream()
			.map(ReportDefinition::descriptor)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<ReportDefinition<?, ?>> definitions = List.of();

		private Builder() {
		}

		public Builder withDefinitions(List<ReportDefinition<?, ?>> value) {
			definitions = value == null ? null : List.copyOf(value);
			return this;
		}

		public DefaultReportCatalog build() {
			Objects.requireNonNull(definitions, "definitions");
			return new DefaultReportCatalog(this);
		}
	}
}
