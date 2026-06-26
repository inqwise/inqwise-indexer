package com.inqwise.indexer.hot;

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
		return new InvalidRouteInvalidation(
			targetName,
			periodKey,
			targetId,
			indexerId,
			indexName,
			false
		);
	}

	boolean matches(InvalidRouteSignature signature) {
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
}
