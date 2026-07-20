package com.inqwise.coordination;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

public final class LocalExclusiveFlowCoordinator implements ExclusiveFlowCoordinator {
	private final ConcurrentMap<String, ActiveFlow<?>> activeFlows = new ConcurrentHashMap<>();

	@Override
	public <T> Future<T> execute(
		String key,
		Class<T> resultType,
		Handler<Promise<T>> flow
	) {
		Objects.requireNonNull(key, "key");
		Objects.requireNonNull(resultType, "resultType");
		Objects.requireNonNull(flow, "flow");

		AtomicReference<ActiveFlow<T>> acquired = new AtomicReference<>();
		ActiveFlow<?> active = activeFlows.compute(key, (ignored, current) -> {
			if (current != null) {
				return current;
			}

			ActiveFlow<T> created = ActiveFlow.<T>builder()
				.withResultType(resultType)
				.withPromise(Promise.promise())
				.build();
			acquired.set(created);
			return created;
		});

		if (!active.resultType().equals(resultType)) {
			return Future.failedFuture(new IllegalArgumentException(
				"Exclusive flow result type mismatch for key " + key
					+ ": expected " + active.resultType().getName()
					+ ", received " + resultType.getName()
			));
		}

		ActiveFlow<T> typed = cast(active);
		if (acquired.get() == typed) {
			typed.promise().future().onComplete(ignored -> activeFlows.remove(key, typed));
			try {
				flow.handle(typed.promise());
			} catch (Throwable error) {
				typed.promise().tryFail(error);
			}
		}

		return typed.promise().future();
	}

	@SuppressWarnings("unchecked")
	private <T> ActiveFlow<T> cast(ActiveFlow<?> active) {
		return (ActiveFlow<T>) active;
	}

	private record ActiveFlow<T>(
		Class<T> resultType,
		Promise<T> promise
	) {
		private static <T> Builder<T> builder() {
			return new Builder<>();
		}

		private static final class Builder<T> {
			private Class<T> resultType;
			private Promise<T> promise;

			private Builder<T> withResultType(Class<T> value) {
				resultType = value;
				return this;
			}

			private Builder<T> withPromise(Promise<T> value) {
				promise = value;
				return this;
			}

			private ActiveFlow<T> build() {
				return new ActiveFlow<>(
					Objects.requireNonNull(resultType, "resultType"),
					Objects.requireNonNull(promise, "promise")
				);
			}
		}
	}
}
