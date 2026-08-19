package com.inqwise.indexer.service.admin;

import java.util.List;

import com.inqwise.indexer.hot.HotRoutingDiagnostics;
import com.inqwise.indexer.hot.HotRoutingSnapshot;

enum EmptyHotRoutingDiagnostics implements HotRoutingDiagnostics {
	INSTANCE;

	@Override
	public HotRoutingSnapshot snapshot(int maxTargets, int maxIndexersPerTarget) {
		return HotRoutingSnapshot.builder()
			.withTargets(List.of())
			.withTruncated(false)
			.build();
	}
}
