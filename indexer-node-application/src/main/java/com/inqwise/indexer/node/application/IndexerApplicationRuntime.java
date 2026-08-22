package com.inqwise.indexer.node.application;

import com.inqwise.indexer.load.api.LoadManagementService;
import com.inqwise.indexer.load.api.LoadQuery;
import com.inqwise.indexer.query.provider.ReportsProviders;

import io.vertx.core.Future;

public interface IndexerApplicationRuntime {
	Future<Void> start();

	Future<Void> stop();

	ReportsProviders reports();

	boolean loadEnabled();

	LoadManagementService loadManagement();

	LoadQuery loadQuery();
}
