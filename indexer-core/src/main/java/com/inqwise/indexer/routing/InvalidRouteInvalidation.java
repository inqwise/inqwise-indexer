package com.inqwise.indexer.routing;

import java.util.Objects;

public record InvalidRouteInvalidation(
	String targetName,
	String periodKey,
	Integer targetId,
	Integer indexerId,
	String indexName,
	boolean periodKeyWildcard
) {
	public InvalidRouteInvalidation(
		String targetName,
		String periodKey,
		Integer targetId,
		Integer indexerId,
		String indexName
	) {
		this(targetName, periodKey, targetId, indexerId, indexName, true);
	}

	public static InvalidRouteInvalidation exactPeriodKey(
		String targetName,
		String periodKey,
		Integer targetId,
		Integer indexerId,
		String indexName
	) {
		return builder()
			.withTargetName(targetName)
			.withPeriodKey(periodKey)
			.withTargetId(targetId)
			.withIndexerId(indexerId)
			.withIndexName(indexName)
			.withPeriodKeyWildcard(false)
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public boolean matches(InvalidRouteSignature signature) {
		return matches(targetName, signature.targetName())
			&& matchesPeriodKey(signature.periodKey())
			&& matches(targetId, signature.targetId())
			&& matches(indexerId, signature.indexerId())
			&& matches(indexName, signature.indexName());
	}

	private boolean matchesPeriodKey(String actual) {
		if (periodKeyWildcard && periodKey == null) {
			return true;
		}

		return Objects.equals(periodKey, actual);
	}

	private boolean matches(Object expected, Object actual) {
		return expected == null || expected.equals(actual);
	}

	public static final class Builder {
		private String targetName;
		private String periodKey;
		private Integer targetId;
		private Integer indexerId;
		private String indexName;
		private boolean periodKeyWildcard = true;

		private Builder() {
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withPeriodKey(String value) {
			periodKey = value;
			return this;
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		public Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		public Builder withPeriodKeyWildcard(boolean value) {
			periodKeyWildcard = value;
			return this;
		}

		public InvalidRouteInvalidation build() {
			return new InvalidRouteInvalidation(
				targetName,
				periodKey,
				targetId,
				indexerId,
				indexName,
				periodKeyWildcard
			);
		}
	}
}
