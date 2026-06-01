package com.inqwise.indexer.hot;

public interface TargetInvalidationRegistry {
	void markInvalidated(Integer concreteTargetId);

	TargetInvalidationEntries listInvalidations(int maxTargets);
}
