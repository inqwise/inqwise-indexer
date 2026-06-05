package com.inqwise.indexer.load;

import java.util.Objects;

import com.inqwise.indexer.commands.Command;
import com.inqwise.indexer.commands.CommandHandler;
import com.inqwise.indexer.commands.CommandService;
import com.inqwise.indexer.commands.DeleteIndexerCommand;
import com.inqwise.indexer.metadata.DocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;

import io.vertx.core.Future;

public class CancelLoadCommandHandler implements CommandHandler {
	private final DocumentStoreMetadataRepository metadataRepository;
	private final IndexerLoadRepository loadRepository;
	private final LoadProviderRegistry loadProviderRegistry;
	private final CommandService commandService;

	public CancelLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		LoadProvider loadProvider,
		CommandService commandService
	) {
		this(
			metadataRepository,
			loadRepository,
			new InMemoryLoadProviderRegistry().register("default", loadProvider),
			commandService
		);
	}

	public CancelLoadCommandHandler(
		DocumentStoreMetadataRepository metadataRepository,
		IndexerLoadRepository loadRepository,
		LoadProviderRegistry loadProviderRegistry,
		CommandService commandService
	) {
		this.metadataRepository = Objects.requireNonNull(metadataRepository, "metadataRepository");
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.loadProviderRegistry = Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
		this.commandService = commandService;
	}

	@Override
	public String getType() {
		return CancelLoadCommand.TYPE;
	}

	@Override
	public Future<Void> handle(Command command) {
		CancelLoadCommand cancel = new CancelLoadCommand(command.toJson());

		return loadRepository.getByIndexerId(cancel.getIndexerId())
			.compose(found -> found
				.map(Future::succeededFuture)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + cancel.getIndexerId())))
			.compose(load -> cancel(load, cancel));
	}

	private Future<Void> cancel(IndexerLoadRecord load, CancelLoadCommand cancel) {
		if (load.version() != cancel.getExpectedLoadVersion()) {
			return Future.failedFuture(
				"Indexer load version conflict for id " + load.indexerId() + ": expected "
					+ cancel.getExpectedLoadVersion() + " but was " + load.version()
			);
		}

		if (load.state() == IndexerLoadState.PUBLISHED) {
			return Future.failedFuture("Indexer load is not cancellable: " + load.state());
		}
		if (load.state() == IndexerLoadState.CANCELLED) {
			return cleanup(load);
		}

		return loadProviderRegistry.get(load.providerId())
			.compose(provider -> provider.stop(new LoadStopRequest(load.indexerId(), cancel.getReason())))
			.compose(ignored -> loadRepository.updateState(new UpdateIndexerLoadState(
				load.indexerId(),
				IndexerLoadState.CANCELLED,
				load.version()
			)))
			.compose(ignored -> loadRepository.getByIndexerId(load.indexerId()))
			.compose(updated -> updated
				.map(this::cleanup)
				.orElseGet(() -> Future.failedFuture("Indexer load not found: " + load.indexerId())));
	}

	private Future<Void> cleanup(IndexerLoadRecord load) {
		Future<Void> deleted = deleteIfPresent(load.liveIndexerId());
		return deleted.compose(ignored -> deleteIfPresent(load.indexerId()));
	}

	private Future<Void> deleteIfPresent(Integer indexerId) {
		if (commandService == null || indexerId == null) {
			return Future.succeededFuture();
		}

		return metadataRepository.getIndexerById(indexerId)
			.compose(found -> found
				.map(this::delete)
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> delete(IndexerRecord indexer) {
		return commandService.submit(new DeleteIndexerCommand(indexer.id(), indexer.version()));
	}
}
