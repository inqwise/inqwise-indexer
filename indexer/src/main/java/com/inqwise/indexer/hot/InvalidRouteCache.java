package com.inqwise.indexer.hot;

import java.util.Optional;

public interface InvalidRouteCache {
	Optional<InvalidRouteRecord> find(InvalidRouteSignature signature);

	void record(InvalidRouteSignature signature, String reason);

	void invalidateMatching(InvalidRouteInvalidation invalidation);
}
