package com.inqwise.indexer.publication;

import java.util.Objects;

import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor.LifecycleOperation;

import io.vertx.core.Future;

public final class MonitoredIndexPublicationService implements IndexPublicationService {
	private final IndexPublicationService delegate;
	private final IndexerOperationalMonitor monitor;

	public MonitoredIndexPublicationService(
		IndexPublicationService delegate,
		IndexerOperationalMonitor monitor
	) {
		this.delegate = Objects.requireNonNull(delegate, "delegate");
		this.monitor = Objects.requireNonNull(monitor, "monitor");
	}

	@Override
	public Future<PublicationReadinessResult> markReady(
		MarkIndexReadyRequest request
	) {
		return delegate.markReady(request);
	}

	@Override
	public Future<IndexPublicationResult> publish(PublishIndexRequest request) {
		monitor.lifecycleStarted(LifecycleOperation.PUBLISH);
		Future<IndexPublicationResult> result;
		try {
			result = delegate.publish(request);
		} catch (Throwable error) {
			monitor.lifecycleCompleted(LifecycleOperation.PUBLISH, false);
			return Future.failedFuture(error);
		}
		result.onComplete(completed -> monitor.lifecycleCompleted(
			LifecycleOperation.PUBLISH,
			completed.succeeded()
		));
		return result;
	}

	@Override
	public Future<IndexPublicationResult> retire(RetireIndexRequest request) {
		return delegate.retire(request);
	}
}
