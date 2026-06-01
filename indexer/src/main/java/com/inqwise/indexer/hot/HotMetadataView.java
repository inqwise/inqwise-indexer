package com.inqwise.indexer.hot;

import java.util.Optional;

import io.vertx.core.Future;

public interface HotMetadataView {
	Optional<HotTarget> findTargetByName(String targetName);

	Optional<HotTarget> findTargetByUid(String targetUid);

	Optional<HotIndexer> findIndexerById(Integer indexerId);

	Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId);

	void invalidateHotTargetByConcreteTargetId(Integer targetId);

	void invalidateHotTargetByIndexerId(Integer indexerId);
}
