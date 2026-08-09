package com.inqwise.indexer.query;

import java.util.Objects;
import java.util.Set;

import com.inqwise.indexer.catalog.targets.TargetNameValidator;

public record ReportDescriptor(
	String name,
	String targetName,
	ReportQueryScope scope,
	Set<IndexSchema> supportedSchemas
) {
	public ReportDescriptor {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		TargetNameValidator.requireTargetName(targetName);
		scope = Objects.requireNonNull(scope, "scope");
		supportedSchemas = supportedSchemas == null
			? Set.of()
			: Set.copyOf(supportedSchemas);
	}

	public boolean supports(IndexSchema schema) {
		Objects.requireNonNull(schema, "schema");
		return supportedSchemas.isEmpty() || supportedSchemas.contains(schema);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String name;
		private String targetName;
		private ReportQueryScope scope = ReportQueryScope.builder().build();
		private Set<IndexSchema> supportedSchemas = Set.of();

		private Builder() {
		}

		public Builder withName(String value) {
			name = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withScope(ReportQueryScope value) {
			scope = value;
			return this;
		}

		public Builder withSupportedSchemas(Set<IndexSchema> value) {
			supportedSchemas = value == null ? null : Set.copyOf(value);
			return this;
		}

		public ReportDescriptor build() {
			return new ReportDescriptor(
				Objects.requireNonNull(name, "name"),
				Objects.requireNonNull(targetName, "targetName"),
				Objects.requireNonNull(scope, "scope"),
				supportedSchemas
			);
		}
	}
}
