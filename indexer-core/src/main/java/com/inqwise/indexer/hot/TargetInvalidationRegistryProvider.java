package com.inqwise.indexer.hot;

public interface TargetInvalidationRegistryProvider {
	/**
	 * Returns a handle to the namespace's logical registry. Repeated calls for
	 * the same namespace must observe the same invalidation entries.
	 */
	TargetInvalidationRegistry create(TargetInvalidationRegistryConfig config);
}
