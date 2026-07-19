package com.inqwise.indexer.load.runtime;

import com.inqwise.indexer.load.api.IndexerLoadRecord;
import com.inqwise.indexer.load.api.LoadProviderRegistry;
import com.inqwise.indexer.load.api.LoadStopRequest;
import com.inqwise.indexer.load.repository.IndexerLoadRepository;
import com.inqwise.indexer.load.repository.UpdateIndexerLoadFailure;


import java.util.Objects;

import com.inqwise.indexer.runtime.IndexerEvent;
import com.inqwise.indexer.runtime.IndexerEventPublisher;
import com.inqwise.indexer.runtime.IndexerEventType;

import io.vertx.core.Future;

public class LoadAwareIndexerEventPublisher implements IndexerEventPublisher {
	private final IndexerLoadRepository loadRepository;
	private final LoadProviderRegistry loadProviderRegistry;
	private final IndexerEventPublisher delegate;

	public LoadAwareIndexerEventPublisher(
		IndexerLoadRepository loadRepository,
		LoadProviderRegistry loadProviderRegistry,
		IndexerEventPublisher delegate
	) {
		this.loadRepository = Objects.requireNonNull(loadRepository, "loadRepository");
		this.loadProviderRegistry = Objects.requireNonNull(loadProviderRegistry, "loadProviderRegistry");
		this.delegate = delegate == null ? IndexerEventPublisher.NOOP : delegate;
	}

	@Override
	public Future<Void> publish(IndexerEvent event) {
		Future<Void> handled = event.getType() == IndexerEventType.ACTION_ITEM_FAILED
			? failActiveLoad(event)
			: Future.succeededFuture();

		return handled
			.recover(ignored -> Future.succeededFuture())
			.compose(ignored -> delegate.publish(event));
	}

	private Future<Void> failActiveLoad(IndexerEvent event) {
		Integer indexerId = event.getModel().getId();
		if (indexerId == null) {
			return Future.succeededFuture();
		}

		return loadRepository.getActiveByTargetIndexerId(indexerId)
			.compose(found -> found
				.map(load -> markFailed(load, failureReason(event))
					.compose(ignored -> stopProvider(load, failureReason(event))))
				.orElseGet(Future::succeededFuture));
	}

	private Future<Void> markFailed(IndexerLoadRecord load, String reason) {
		return loadRepository.markFailed(UpdateIndexerLoadFailure.builder()
			.withIndexerId(load.indexerId())
			.withFailureReason(reason)
			.withExpectedVersion(load.version())
			.build());
	}

	private Future<Void> stopProvider(IndexerLoadRecord load, String reason) {
		return loadProviderRegistry.get(load.providerId())
			.compose(provider -> provider.stop(LoadStopRequest.builder()
				.withIndexerId(load.indexerId())
				.withReason(reason)
				.build()))
			.recover(ignored -> Future.succeededFuture());
	}

	private String failureReason(IndexerEvent event) {
		if (event.getError() == null || event.getError().getMessage() == null) {
			return "Load action item failed";
		}
		return event.getError().getMessage();
	}
}
