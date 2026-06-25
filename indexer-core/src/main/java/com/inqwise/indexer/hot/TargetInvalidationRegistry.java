package com.inqwise.indexer.hot;

import io.vertx.core.Future;

public interface TargetInvalidationRegistry {
	Future<Void> markInvalidated(Integer concreteTargetId);

	Future<TargetInvalidationEntries> listInvalidations(int maxTargets);
}
