package com.inqwise.indexer.lifecycle;

import io.vertx.core.Future;

public interface TargetInvalidationRegistry {
	/**
	 * Atomically advances the target version and refreshes its expiry.
	 */
	Future<Void> markInvalidated(Integer concreteTargetId);

	/**
	 * Returns at most {@code maxTargets} entries and reports whether any entries
	 * were omitted. Implementations must not return an unbounded result.
	 */
	Future<TargetInvalidationEntries> listInvalidations(int maxTargets);
}
