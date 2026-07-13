package com.inqwise.indexer.service.admin;

import java.util.Objects;

import com.inqwise.indexer.provisioning.IndexerQueueResourceManager;
import com.inqwise.indexer.lifecycle.MetadataChangeNotifier;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.definitions.IndexerDefinitionProvider;
import com.inqwise.indexer.definitions.TargetDefinitionProvider;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.catalog.indexers.IndexerOperations;
import com.inqwise.indexer.provisioning.IndexerDocumentIndexResourceManager;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public class AdminServiceVerticle extends ServiceProxyVerticle<AdminService> {
	private final AdminService service;

	public AdminServiceVerticle(
		DocumentStoreMetadataRepository repository,
		MetadataChangeNotifier metadataChangeNotifier,
		IndexerQueueResourceManager queueResources,
		TargetDefinitionProvider targetDefinitionProvider,
		IndexerDefinitionProvider indexerDefinitionProvider,
		IndexerDocumentIndexResourceManager documentIndexResources,
		CommandService commandService,
		IndexerOperations indexerOperations
	) {
		this.service = new AdminServiceImpl(
			Objects.requireNonNull(repository, "repository"),
			Objects.requireNonNull(metadataChangeNotifier, "metadataChangeNotifier"),
			Objects.requireNonNull(queueResources, "queueResources"),
			Objects.requireNonNull(targetDefinitionProvider, "targetDefinitionProvider"),
			Objects.requireNonNull(indexerDefinitionProvider, "indexerDefinitionProvider"),
			Objects.requireNonNull(documentIndexResources, "documentIndexResources"),
			Objects.requireNonNull(commandService, "commandService"),
			Objects.requireNonNull(indexerOperations, "indexerOperations")
		);
	}

	@Override
	protected String address() {
		return AdminServices.DEFAULT_ADDRESS;
	}

	@Override
	protected AdminService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(AdminService service) {
		return new AdminServiceVertxProxyHandler(vertx, service);
	}
}
