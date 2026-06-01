package com.inqwise.indexer.hot;

public record InvalidRouteInvalidation(
	String targetUid,
	String targetName,
	String periodKey,
	Integer targetId,
	Integer indexerId,
	String indexName
) {
	boolean matches(InvalidRouteSignature signature) {
		return matches(targetUid, signature.targetUid())
			&& matches(targetName, signature.targetName())
			&& matches(periodKey, signature.periodKey())
			&& matches(targetId, signature.targetId())
			&& matches(indexerId, signature.indexerId())
			&& matches(indexName, signature.indexName());
	}

	private boolean matches(Object expected, Object actual) {
		return expected == null || expected.equals(actual);
	}
}
