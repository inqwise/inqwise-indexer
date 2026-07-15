package com.inqwise.indexer.hot;

import java.util.Optional;

import com.inqwise.indexer.providers.HotIndexerCapability;

import io.vertx.core.Future;

public interface HotMetadataView {
	Optional<HotTarget> findTargetByName(String targetName);

	Optional<HotIndexerCapability> findIndexerById(Integer indexerId);

	Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId);

	void invalidateHotTargetByConcreteTargetId(Integer targetId);

	void invalidateHotTargetByIndexerId(Integer indexerId);

	void invalidateAllHotTargets();
}
