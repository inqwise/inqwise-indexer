package com.inqwise.indexer.node.application;

import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.runtime.IndexerEventPublisher;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public interface IndexerApplicationProvider {
	IndexerApplicationRuntime create(
		Vertx vertx,
		JsonObject config,
		IndexerEventPublisher events,
		IndexerOperationalMonitor monitor
	);
}
