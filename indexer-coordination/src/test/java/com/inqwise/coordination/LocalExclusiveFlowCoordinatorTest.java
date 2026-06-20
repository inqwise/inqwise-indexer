package com.inqwise.coordination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LocalExclusiveFlowCoordinatorTest {
	@Test
	void concurrentCallersShareOwnerResult(VertxTestContext testContext) {
		LocalExclusiveFlowCoordinator coordinator = new LocalExclusiveFlowCoordinator();
		AtomicInteger executions = new AtomicInteger();
		AtomicReference<Promise<String>> ownerPromise = new AtomicReference<>();

		Future<String> owner = coordinator.execute("key", String.class, promise -> {
			executions.incrementAndGet();
			ownerPromise.set(promise);
		});
		Future<String> waiter = coordinator.execute("key", String.class, promise ->
			executions.incrementAndGet());

		assertEquals(1, executions.get());
		ownerPromise.get().complete("value");

		Future.all(owner, waiter).onComplete(testContext.succeeding(results ->
			testContext.verify(() -> {
				assertEquals("value", results.resultAt(0));
				assertEquals("value", results.resultAt(1));
				testContext.completeNow();
			})
		));
	}

	@Test
	void concurrentCallersShareOriginalFailure(VertxTestContext testContext) {
		LocalExclusiveFlowCoordinator coordinator = new LocalExclusiveFlowCoordinator();
		AtomicReference<Promise<String>> ownerPromise = new AtomicReference<>();
		RuntimeException failure = new RuntimeException("failed");
		AtomicInteger failures = new AtomicInteger();

		Future<String> owner = coordinator.execute("key", String.class, ownerPromise::set);
		Future<String> waiter = coordinator.execute("key", String.class, promise -> {
			throw new AssertionError("waiter flow must not execute");
		});

		owner.onFailure(error -> verifySharedFailure(testContext, failures, failure, error));
		waiter.onFailure(error -> verifySharedFailure(testContext, failures, failure, error));
		ownerPromise.get().fail(failure);
	}

	@Test
	void completedKeyCanExecuteAgain(VertxTestContext testContext) {
		LocalExclusiveFlowCoordinator coordinator = new LocalExclusiveFlowCoordinator();
		AtomicInteger executions = new AtomicInteger();

		coordinator.execute("key", String.class, promise -> {
			executions.incrementAndGet();
			promise.complete("first");
		}).compose(first -> coordinator.execute("key", String.class, promise -> {
			executions.incrementAndGet();
			promise.complete("second");
		})).onComplete(testContext.succeeding(second -> testContext.verify(() -> {
			assertEquals("second", second);
			assertEquals(2, executions.get());
			testContext.completeNow();
		})));
	}

	@Test
	void activeKeyRejectsDifferentResultType() {
		LocalExclusiveFlowCoordinator coordinator = new LocalExclusiveFlowCoordinator();
		AtomicReference<Promise<String>> ownerPromise = new AtomicReference<>();

		coordinator.execute("key", String.class, ownerPromise::set);
		Future<Integer> mismatch = coordinator.execute("key", Integer.class, Promise::complete);

		assertTrue(mismatch.failed());
		assertInstanceOf(IllegalArgumentException.class, mismatch.cause());
		ownerPromise.get().complete("done");
	}

	@Test
	void synchronousOwnerFailureFailsSharedFuture() {
		LocalExclusiveFlowCoordinator coordinator = new LocalExclusiveFlowCoordinator();
		RuntimeException failure = new RuntimeException("failed");

		Future<String> result = coordinator.execute("key", String.class, promise -> {
			throw failure;
		});

		assertTrue(result.failed());
		assertSame(failure, result.cause());
	}

	private void verifySharedFailure(
		VertxTestContext testContext,
		AtomicInteger failures,
		Throwable expected,
		Throwable actual
	) {
		testContext.verify(() -> assertSame(expected, actual));
		if (failures.incrementAndGet() == 2) {
			testContext.completeNow();
		}
	}
}
