package com.inqwise.indexer.runtime;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.catalog.indexers.IndexerModel;

import io.vertx.core.Future;

final class ActionItemCommitContinuation {
	private static final Logger logger = LogManager.getLogger(
		ActionItemCommitContinuation.class
	);

	private ActionItemCommitContinuation() {
	}

	static Future<Void> run(
		IndexerModel model,
		IndexerActionItem item,
		IndexerQueueConsumer consumer,
		IndexerEventPublisher events,
		ActionItemAfterCommitObserver observer
	) {
		return observeAfterCommit(model, item, events, observer)
			.compose(ignored -> consumer.resume())
			.compose(ignored -> publish(
				events,
				event(model, item, IndexerEventType.CONSUMER_RESUMED, null)
			))
			.recover(error -> loggedFailure(
				"Post-commit action continuation failed",
				error
			));
	}

	static Future<Void> observeAfterCommit(
		IndexerModel model,
		IndexerActionItem item,
		IndexerEventPublisher events,
		ActionItemAfterCommitObserver observer
	) {
		return publish(events, event(model, item, IndexerEventType.ACTION_ITEM_COMMITTED, null))
			.recover(error -> loggedFailure(
				"Failed to publish committed action event",
				error
			))
			.compose(ignored -> observe(model, item, events, observer));
	}

	private static Future<Void> observe(
		IndexerModel model,
		IndexerActionItem item,
		IndexerEventPublisher events,
		ActionItemAfterCommitObserver observer
	) {
		return Future.<Void>succeededFuture()
			.compose(ignored -> observer.afterCommit(item))
			.recover(error -> {
				logger.error("After-commit action observer failed", error);
				return publish(events, event(
					model,
					item,
					IndexerEventType.ACTION_ITEM_AFTER_COMMIT_OBSERVER_FAILED,
					error
				)).recover(reportError -> loggedFailure(
					"Failed to publish after-commit observer failure",
					reportError
				));
			});
	}

	private static Future<Void> publish(
		IndexerEventPublisher events,
		IndexerEvent event
	) {
		return Future.<Void>succeededFuture()
			.compose(ignored -> events.publish(event));
	}

	private static IndexerEvent event(
		IndexerModel model,
		IndexerActionItem item,
		IndexerEventType type,
		Throwable error
	) {
		return IndexerEvent.builder()
			.withType(type)
			.withModel(model)
			.withItem(item)
			.withError(error)
			.build();
	}

	private static Future<Void> loggedFailure(String message, Throwable error) {
		logger.error(message, error);
		return Future.succeededFuture();
	}
}
