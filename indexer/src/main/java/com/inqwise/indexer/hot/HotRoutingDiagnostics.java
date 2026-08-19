package com.inqwise.indexer.hot;

public interface HotRoutingDiagnostics {
	HotRoutingSnapshot snapshot(int maxTargets, int maxIndexersPerTarget);
}
