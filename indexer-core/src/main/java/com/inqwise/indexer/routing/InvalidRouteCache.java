package com.inqwise.indexer.routing;

import java.util.List;
import java.util.Optional;

public interface InvalidRouteCache {
	Optional<InvalidRouteRecord> find(InvalidRouteSignature signature);

	void record(InvalidRouteSignature signature, String reason);

	void invalidateMatching(InvalidRouteInvalidation invalidation);

	List<InvalidRouteRecord> list(int maxRoutes);
}
