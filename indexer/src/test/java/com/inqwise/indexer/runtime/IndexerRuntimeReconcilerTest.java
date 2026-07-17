package com.inqwise.indexer.runtime;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBus;
import com.inqwise.indexer.adapters.local.InMemoryIndexerLifecycleEventBusProvider;
import com.inqwise.indexer.adapters.local.InMemoryIndexerQueue;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.lifecycle.IndexerLifecycleProviderSignal;
import com.inqwise.indexer.lifecycle.IndexerLifecycleSubscription;
import com.inqwise.indexer.lifecycle.IndexerMetadataChanged;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.adapters.local.InMemoryDocumentStoreMetadataRepository;
import com.inqwise.indexer.metadata.IndexerRecord;
import com.inqwise.indexer.metadata.InsertIndexer;
import com.inqwise.indexer.metadata.InsertTarget;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.publication.PublicationState;
import com.inqwise.indexer.metadata.UpdateIndexerRuntimeState;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerRuntimeReconcilerTest {
	@Test
	void startupFullSynchronizationActivatesDesiredIndexer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		IndexerRuntime runtime = runtime(vertx, activated, new AtomicInteger());
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(ignored -> reconciler.start())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, activated.get());
				assertEquals(1, runtime.indexerIds().size());
				testContext.completeNow();
			})));
	}

	@Test
	void startupFullSynchronizationClosesLocalIndexerAbsentFromDesiredSet(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = runtime(vertx, new AtomicInteger(), closed);
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> repository.getIndexerById(indexerId)
				.compose(found -> runtime.reconcile(found.orElseThrow()))
				.compose(ignored -> repository.updateIndexerRuntimeState(
					new UpdateIndexerRuntimeState(
						indexerId,
						IndexerRuntimeState.NON_ACTIVE,
						0L
					)
				)))
			.compose(ignored -> reconciler.start())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, closed.get());
				assertTrue(runtime.indexerIds().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void failedActivationDoesNotRemainRegistered(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			new AtomicInteger(),
			closed,
			true
		));

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(indexerId -> repository.getIndexerById(indexerId))
			.compose(found -> runtime.reconcile(found.orElseThrow()))
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertTrue(runtime.indexerIds().isEmpty());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void failedStartupCanSubscribeAndSynchronizeAgain(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicBoolean failFirstActivation = new AtomicBoolean(true);
		AtomicInteger activated = new AtomicInteger();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			activated,
			closed,
			failFirstActivation.compareAndSet(true, false)
		));
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(ignored -> reconciler.start())
			.transform(first -> {
				assertTrue(first.failed());
				return reconciler.start();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, activated.get());
				assertEquals(1, closed.get());
				assertEquals(1, runtime.indexerIds().size());
				testContext.completeNow();
			})));
	}

	@Test
	void unexpectedEventReconciliationFailureStopsRuntimeAndNotifiesSupervisor(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger failures = new AtomicInteger();
		Promise<Void> enteredRecovery = Promise.promise();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			new AtomicInteger(),
			new AtomicInteger(),
			true
		));
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);
		reconciler.onFailure(error -> {
			failures.incrementAndGet();
			enteredRecovery.tryComplete();
			return Future.succeededFuture();
		});

		reconciler.start()
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.ACTIVE))
			.compose(indexerId -> eventBus.publish(new IndexerMetadataChanged(
				indexerId,
				1,
				"test",
				0L
			)))
			.compose(ignored -> enteredRecovery.future())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, failures.get());
				assertTrue(runtime.indexerIds().isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void dirtyCapacityOverflowCollapsesPendingIdsIntoFullSynchronization(
		Vertx vertx,
		VertxTestContext testContext
	) {
		BlockingRuntimeRepository repository = new BlockingRuntimeRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		IndexerRuntime runtime = runtime(vertx, activated, new AtomicInteger());
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime,
			new IndexerRuntimeReconcilerOptions().setMaxDirtyIndexers(2)
		);
		Future<Void> started = reconciler.start();

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, "customers-one")
			.compose(first -> eventBus.publish(event(first)))
			.compose(ignored -> insertIndexer(
				repository,
				IndexerRuntimeState.ACTIVE,
				"customers-two"
			))
			.compose(second -> eventBus.publish(event(second)))
			.compose(ignored -> insertIndexer(
				repository,
				IndexerRuntimeState.ACTIVE,
				"customers-three"
			))
			.compose(third -> eventBus.publish(event(third)))
			.compose(ignored -> repository.releaseFirstList())
			.compose(ignored -> started)
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, repository.listCalls.get());
				assertEquals(3, activated.get());
				assertEquals(3, runtime.indexerIds().size());
				testContext.completeNow();
			})));
	}

	@Test
	void providerSignalsRequestSerializedFullSynchronization(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TrackingRuntimeRepository repository = new TrackingRuntimeRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		Promise<Void> secondActivated = Promise.promise();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			activated,
			new AtomicInteger(),
			false,
			() -> {
				if (activated.get() == 2) {
					secondActivated.tryComplete();
				}
			}
		));
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, "customers-one")
			.compose(ignored -> reconciler.start())
			.compose(ignored -> insertIndexer(
				repository,
				IndexerRuntimeState.ACTIVE,
				"customers-two"
			))
			.onSuccess(ignored -> eventBus.emitProviderSignal(
				IndexerLifecycleProviderSignal.RECONNECTED
			))
			.compose(ignored -> secondActivated.future())
			.onSuccess(ignored -> eventBus.emitProviderSignal(
				IndexerLifecycleProviderSignal.DELIVERY_LOST
			))
			.compose(ignored -> repository.thirdList.future())
			.onSuccess(ignored -> eventBus.emitProviderSignal(
				IndexerLifecycleProviderSignal.EXCESSIVE_LAG
			))
			.compose(ignored -> repository.fourthList.future())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(4, repository.listCalls.get());
				assertEquals(2, activated.get());
				assertEquals(2, runtime.indexerIds().size());
				testContext.completeNow();
			})));
	}

	@Test
	void stoppedReconcilerIgnoresProviderSignals(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TrackingRuntimeRepository repository = new TrackingRuntimeRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime(vertx, new AtomicInteger(), new AtomicInteger())
		);

		reconciler.start()
			.compose(ignored -> reconciler.stop())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				eventBus.emitProviderSignal(IndexerLifecycleProviderSignal.RECONNECTED);
				assertEquals(1, repository.listCalls.get());
				testContext.completeNow();
			})));
	}

	@Test
	void stopClosesAllOwnedResourcesWhenSignalUnsubscribeFails(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		FailingSignalCloseEventBus eventBus = new FailingSignalCloseEventBus();
		AtomicInteger closed = new AtomicInteger();
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime(vertx, new AtomicInteger(), closed)
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE)
			.compose(ignored -> reconciler.start())
			.compose(ignored -> reconciler.stop())
			.onComplete(testContext.failing(error -> testContext.verify(() -> {
				assertEquals("signal close failed", error.getMessage());
				assertEquals(1, eventBus.signalCloses.get());
				assertEquals(1, eventBus.indexerCloses.get());
				assertEquals(1, closed.get());
				testContext.completeNow();
			})));
	}

	@Test
	void cleanupFailureDoesNotSuppressRecoveryOnlyNotification(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryDocumentStoreMetadataRepository repository =
			new InMemoryDocumentStoreMetadataRepository();
		FailingSignalCloseEventBus eventBus = new FailingSignalCloseEventBus();
		Promise<Void> enteredRecovery = Promise.promise();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			new AtomicInteger(),
			new AtomicInteger(),
			true
		));
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime
		);
		reconciler.onFailure(error -> {
			enteredRecovery.tryComplete();
			return Future.succeededFuture();
		});

		reconciler.start()
			.compose(ignored -> insertIndexer(repository, IndexerRuntimeState.ACTIVE))
			.compose(indexerId -> eventBus.publish(event(indexerId)))
			.compose(ignored -> enteredRecovery.future())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, eventBus.signalCloses.get());
				assertEquals(1, eventBus.indexerCloses.get());
				testContext.completeNow();
			})));
	}

	@Test
	void periodicSafetySynchronizationFindsRepositoryOnlyChanges(
		Vertx vertx,
		VertxTestContext testContext
	) {
		TrackingRuntimeRepository repository = new TrackingRuntimeRepository();
		InMemoryIndexerLifecycleEventBus eventBus = new InMemoryIndexerLifecycleEventBus();
		AtomicInteger activated = new AtomicInteger();
		Promise<Void> secondActivated = Promise.promise();
		IndexerRuntime runtime = new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			activated,
			new AtomicInteger(),
			false,
			() -> {
				if (activated.get() == 2) {
					secondActivated.tryComplete();
				}
			}
		));
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			eventBus,
			runtime,
			new IndexerRuntimeReconcilerOptions().setSafetySyncIntervalMs(10L)
		);

		insertIndexer(repository, IndexerRuntimeState.ACTIVE, "customers-one")
			.compose(ignored -> reconciler.start())
			.compose(ignored -> insertIndexer(
				repository,
				IndexerRuntimeState.ACTIVE,
				"customers-two"
			))
			.compose(ignored -> secondActivated.future())
			.compose(ignored -> reconciler.stop())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertTrue(repository.listCalls.get() >= 2);
				assertEquals(2, activated.get());
				testContext.completeNow();
			})));
	}

	@Test
	void periodicTicksCoalesceWhileSynchronizationIsRunningAndStopDiscardsPendingTick(
		Vertx vertx,
		VertxTestContext testContext
	) {
		BlockingPeriodicRuntimeRepository repository =
			new BlockingPeriodicRuntimeRepository();
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			new InMemoryIndexerLifecycleEventBus(),
			runtime(vertx, new AtomicInteger(), new AtomicInteger()),
			new IndexerRuntimeReconcilerOptions().setSafetySyncIntervalMs(5L)
		);

		reconciler.start()
			.compose(ignored -> repository.secondListStarted.future())
			.compose(ignored -> delay(vertx, 30L))
			.compose(ignored -> {
				testContext.verify(() -> assertEquals(2, repository.listCalls.get()));
				Future<Void> stopping = reconciler.stop();
				repository.releaseSecondList();
				return stopping;
			})
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, repository.listCalls.get());
				assertEquals(1, repository.maxConcurrentLists.get());
				testContext.completeNow();
			})));
	}

	@Test
	void periodicSafetySynchronizationFailureNotifiesSupervisor(
		Vertx vertx,
		VertxTestContext testContext
	) {
		FailingPeriodicRuntimeRepository repository =
			new FailingPeriodicRuntimeRepository();
		AtomicInteger failures = new AtomicInteger();
		Promise<Void> enteredRecovery = Promise.promise();
		IndexerRuntimeReconciler reconciler = new IndexerRuntimeReconciler(
			vertx,
			repository,
			new InMemoryIndexerLifecycleEventBus(),
			runtime(vertx, new AtomicInteger(), new AtomicInteger()),
			new IndexerRuntimeReconcilerOptions().setSafetySyncIntervalMs(5L)
		);
		reconciler.onFailure(error -> {
			failures.incrementAndGet();
			enteredRecovery.tryComplete();
			return Future.succeededFuture();
		});

		reconciler.start()
			.compose(ignored -> enteredRecovery.future())
			.compose(ignored -> delay(vertx, 20L))
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(2, repository.listCalls.get());
				assertEquals(1, failures.get());
				testContext.completeNow();
			})));
	}

	@Test
	void rejectsNonPositiveDirtyCapacity(Vertx vertx) {
		assertThrows(IllegalArgumentException.class, () -> new IndexerRuntimeReconciler(
			vertx,
			new InMemoryDocumentStoreMetadataRepository(),
			new InMemoryIndexerLifecycleEventBus(),
			new IndexerRuntime(record -> {
				throw new AssertionError("Indexer must not be created");
			}),
			new IndexerRuntimeReconcilerOptions().setMaxDirtyIndexers(0)
		));
	}

	@Test
	void rejectsNonPositiveSafetySynchronizationInterval(Vertx vertx) {
		assertThrows(IllegalArgumentException.class, () -> new IndexerRuntimeReconciler(
			vertx,
			new InMemoryDocumentStoreMetadataRepository(),
			new InMemoryIndexerLifecycleEventBus(),
			new IndexerRuntime(record -> {
				throw new AssertionError("Indexer must not be created");
			}),
			new IndexerRuntimeReconcilerOptions().setSafetySyncIntervalMs(0L)
		));
	}

	private Future<Void> delay(Vertx vertx, long delayMs) {
		Promise<Void> delayed = Promise.promise();
		vertx.setTimer(delayMs, ignored -> delayed.tryComplete());
		return delayed.future();
	}

	private IndexerMetadataChanged event(Integer indexerId) {
		return new IndexerMetadataChanged(indexerId, 1, "test", 0L);
	}

	private IndexerRuntime runtime(
		Vertx vertx,
		AtomicInteger activated,
		AtomicInteger closed
	) {
		return new IndexerRuntime(record -> new CountingIndexer(
			vertx,
			record,
			activated,
			closed,
			false
		));
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeState runtimeState
	) {
		return insertIndexer(repository, runtimeState, "customers");
	}

	private Future<Integer> insertIndexer(
		InMemoryDocumentStoreMetadataRepository repository,
		IndexerRuntimeState runtimeState,
		String targetName
	) {
		return repository.insertTarget(new InsertTarget("test", targetName, null))
			.compose(targetId -> repository.insertIndexer(new InsertIndexer(
				"test",
				targetId,
				targetName,
				targetName + "-index",
				targetName + "-queue",
				IndexerType.INDEX,
				runtimeState,
				PublicationState.UNPUBLISHED,
				MutationState.WRITABLE
			)));
	}

	private static final class CountingIndexer extends Indexer {
		private final AtomicInteger activated;
		private final AtomicInteger closed;
		private final boolean failActivation;
		private final Runnable activationCallback;

		private CountingIndexer(
			Vertx vertx,
			IndexerRecord record,
			AtomicInteger activated,
			AtomicInteger closed,
			boolean failActivation
		) {
			this(vertx, record, activated, closed, failActivation, () -> {
			});
		}

		private CountingIndexer(
			Vertx vertx,
			IndexerRecord record,
			AtomicInteger activated,
			AtomicInteger closed,
			boolean failActivation,
			Runnable activationCallback
		) {
			super(
				vertx,
				IndexerRuntime.toModel(record),
				new InMemoryIndexerDocumentStore()
			);
			this.activated = activated;
			this.closed = closed;
			this.failActivation = failActivation;
			this.activationCallback = activationCallback;
		}

		@Override
		public Future<Void> activate() {
			activated.incrementAndGet();
			activationCallback.run();
			return failActivation
				? Future.failedFuture("activation failed")
				: Future.succeededFuture();
		}

		@Override
		public Future<Void> close() {
			closed.incrementAndGet();
			return Future.succeededFuture();
		}
	}

	private static final class BlockingRuntimeRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private final Promise<List<IndexerRecord>> firstList = Promise.promise();
		private final AtomicInteger listCalls = new AtomicInteger();

		@Override
		public Future<List<IndexerRecord>> listRuntimeActiveIndexers() {
			return listCalls.incrementAndGet() == 1
				? firstList.future()
				: super.listRuntimeActiveIndexers();
		}

		private Future<Void> releaseFirstList() {
			return super.listRuntimeActiveIndexers()
				.onSuccess(firstList::tryComplete)
				.mapEmpty();
		}
	}

	private static final class TrackingRuntimeRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private final AtomicInteger listCalls = new AtomicInteger();
		private final Promise<Void> thirdList = Promise.promise();
		private final Promise<Void> fourthList = Promise.promise();

		@Override
		public Future<List<IndexerRecord>> listRuntimeActiveIndexers() {
			int call = listCalls.incrementAndGet();
			if (call == 3) {
				thirdList.tryComplete();
			} else if (call == 4) {
				fourthList.tryComplete();
			}
			return super.listRuntimeActiveIndexers();
		}
	}

	private static final class FailingSignalCloseEventBus
		extends InMemoryIndexerLifecycleEventBus {
		private final AtomicInteger indexerCloses = new AtomicInteger();
		private final AtomicInteger signalCloses = new AtomicInteger();

		@Override
		public Future<IndexerLifecycleSubscription> subscribe(
			Handler<IndexerMetadataChanged> handler
		) {
			return super.subscribe(handler).map(subscription -> () -> {
				indexerCloses.incrementAndGet();
				return subscription.close();
			});
		}

		@Override
		public Future<IndexerLifecycleSubscription> subscribeProviderSignals(
			Handler<IndexerLifecycleProviderSignal> handler
		) {
			return Future.succeededFuture(() -> {
				signalCloses.incrementAndGet();
				return Future.failedFuture("signal close failed");
			});
		}
	}

	private static final class BlockingPeriodicRuntimeRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private final AtomicInteger listCalls = new AtomicInteger();
		private final AtomicInteger concurrentLists = new AtomicInteger();
		private final AtomicInteger maxConcurrentLists = new AtomicInteger();
		private final Promise<Void> secondListStarted = Promise.promise();
		private final Promise<List<IndexerRecord>> secondList = Promise.promise();

		@Override
		public Future<List<IndexerRecord>> listRuntimeActiveIndexers() {
			int call = listCalls.incrementAndGet();
			int concurrent = concurrentLists.incrementAndGet();
			maxConcurrentLists.accumulateAndGet(concurrent, Math::max);
			Future<List<IndexerRecord>> result;
			if (call == 2) {
				secondListStarted.tryComplete();
				result = secondList.future();
			} else {
				result = super.listRuntimeActiveIndexers();
			}
			return result.onComplete(ignored -> concurrentLists.decrementAndGet());
		}

		private void releaseSecondList() {
			super.listRuntimeActiveIndexers().onComplete(secondList);
		}
	}

	private static final class FailingPeriodicRuntimeRepository
		extends InMemoryDocumentStoreMetadataRepository {
		private final AtomicInteger listCalls = new AtomicInteger();

		@Override
		public Future<List<IndexerRecord>> listRuntimeActiveIndexers() {
			return listCalls.incrementAndGet() == 2
				? Future.failedFuture("periodic synchronization failed")
				: super.listRuntimeActiveIndexers();
		}
	}
}
