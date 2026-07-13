package com.inqwise.indexer.hot;

import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistry;
import com.inqwise.indexer.adapters.local.InMemoryTargetInvalidationRegistryProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class TargetInvalidationPollerTest {
	@Test
	void invalidatesOnlyChangedEntriesAndDetectsVersionReset(
		Vertx vertx,
		VertxTestContext testContext
	) {
		MutableClock clock = new MutableClock(Instant.parse("2026-06-22T08:00:00Z"));
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), clock);
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationPoller poller = new TargetInvalidationPoller(
			vertx,
			registry,
			view,
			new TargetInvalidationRegistryOptions(Duration.ofMinutes(1), 5, 10)
		);

		registry.markInvalidated(10)
			.compose(ignored -> poller.pollNow())
			.compose(ignored -> poller.pollNow())
			.compose(ignored -> registry.markInvalidated(10))
			.compose(ignored -> poller.pollNow())
			.compose(ignored -> {
				clock.advance(Duration.ofMinutes(5));
				return poller.pollNow();
			})
			.compose(ignored -> registry.markInvalidated(10))
			.compose(ignored -> poller.pollNow())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(List.of(10, 10, 10), view.invalidatedTargetIds);
				assertEquals(0, view.invalidateAllCount);
				testContext.completeNow();
			})));
	}

	@Test
	void invalidatesWholeViewWhenRegistryResultIsTruncated(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryTargetInvalidationRegistry registry =
			new InMemoryTargetInvalidationRegistry(Duration.ofMinutes(5), Clock.systemUTC());
		RecordingHotMetadataView view = new RecordingHotMetadataView();
		TargetInvalidationPoller poller = new TargetInvalidationPoller(
			vertx,
			registry,
			view,
			new TargetInvalidationRegistryOptions(Duration.ofMinutes(1), 5, 2)
		);

		registry.markInvalidated(10)
			.compose(ignored -> registry.markInvalidated(11))
			.compose(ignored -> registry.markInvalidated(12))
			.compose(ignored -> poller.pollNow())
			.onComplete(testContext.succeeding(ignored -> testContext.verify(() -> {
				assertEquals(1, view.invalidateAllCount);
				assertTrue(view.invalidatedTargetIds.isEmpty());
				testContext.completeNow();
			})));
	}

	@Test
	void startPollsPeriodicallyAndStopCancelsTimer(
		Vertx vertx,
		VertxTestContext testContext
	) {
		CountingRegistry registry = new CountingRegistry();
		TargetInvalidationPoller poller = new TargetInvalidationPoller(
			vertx,
			registry,
			new RecordingHotMetadataView(),
			new TargetInvalidationRegistryOptions(Duration.ofMillis(10), 2, 10)
		);

		poller.start()
			.compose(ignored -> delay(vertx, 35))
			.compose(ignored -> {
				assertTrue(registry.listCalls.get() >= 2);
				return poller.stop();
			})
			.compose(ignored -> {
				int callsAfterStop = registry.listCalls.get();
				return delay(vertx, 30).map(callsAfterStop);
			})
			.onComplete(testContext.succeeding(callsAfterStop -> testContext.verify(() -> {
				assertEquals(callsAfterStop, registry.listCalls.get());
				testContext.completeNow();
			})));
	}

	@Test
	void skipsTimerTickWhilePreviousPollIsRunning(
		Vertx vertx,
		VertxTestContext testContext
	) {
		BlockingRegistry registry = new BlockingRegistry();
		TargetInvalidationPoller poller = new TargetInvalidationPoller(
			vertx,
			registry,
			new RecordingHotMetadataView(),
			new TargetInvalidationRegistryOptions(Duration.ofHours(1), 2, 10)
		);

		poller.start()
			.compose(ignored -> {
				poller.pollOnTimer();
				poller.pollOnTimer();
				assertEquals(2, registry.listCalls.get());
				registry.pendingPoll.complete(new TargetInvalidationEntries(List.of(), false));
				return registry.pendingPoll.future().mapEmpty();
			})
			.compose(ignored -> poller.stop())
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}

	private Future<Void> delay(Vertx vertx, long delayMs) {
		Promise<Void> promise = Promise.promise();
		vertx.setTimer(delayMs, ignored -> promise.complete());
		return promise.future();
	}

	private static class CountingRegistry implements TargetInvalidationRegistry {
		private final AtomicInteger listCalls = new AtomicInteger();

		@Override
		public Future<Void> markInvalidated(Integer concreteTargetId) {
			return Future.succeededFuture();
		}

		@Override
		public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
			listCalls.incrementAndGet();
			return Future.succeededFuture(new TargetInvalidationEntries(List.of(), false));
		}
	}

	private static class BlockingRegistry implements TargetInvalidationRegistry {
		private final AtomicInteger listCalls = new AtomicInteger();
		private final Promise<TargetInvalidationEntries> pendingPoll = Promise.promise();

		@Override
		public Future<Void> markInvalidated(Integer concreteTargetId) {
			return Future.succeededFuture();
		}

		@Override
		public Future<TargetInvalidationEntries> listInvalidations(int maxTargets) {
			if (listCalls.incrementAndGet() == 1) {
				return Future.succeededFuture(new TargetInvalidationEntries(List.of(), false));
			}

			return pendingPoll.future();
		}
	}

	private static class RecordingHotMetadataView implements HotMetadataView {
		private final List<Integer> invalidatedTargetIds = new ArrayList<>();
		private int invalidateAllCount;

		@Override
		public Optional<HotTarget> findTargetByName(String targetName) {
			return Optional.empty();
		}

		@Override
		public Optional<HotIndexer> findIndexerById(Integer indexerId) {
			return Optional.empty();
		}

		@Override
		public Future<Void> refreshHotTargetByConcreteTargetId(Integer targetId) {
			return Future.succeededFuture();
		}

		@Override
		public void invalidateHotTargetByConcreteTargetId(Integer targetId) {
			invalidatedTargetIds.add(targetId);
		}

		@Override
		public void invalidateHotTargetByIndexerId(Integer indexerId) {
		}

		@Override
		public void invalidateAllHotTargets() {
			invalidateAllCount++;
		}
	}

	private static class MutableClock extends Clock {
		private Instant instant;

		private MutableClock(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}

		private void advance(Duration duration) {
			instant = instant.plus(duration);
		}
	}
}
