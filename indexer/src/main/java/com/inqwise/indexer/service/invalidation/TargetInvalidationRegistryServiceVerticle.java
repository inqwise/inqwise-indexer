package com.inqwise.indexer.service.invalidation;

import java.util.Objects;

import com.inqwise.indexer.lifecycle.TargetInvalidationRegistry;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;

public class TargetInvalidationRegistryServiceVerticle extends AbstractVerticle {
	private final TargetInvalidationRegistry registry;
	private final String address;
	private MessageConsumer<JsonObject> consumer;

	public TargetInvalidationRegistryServiceVerticle(TargetInvalidationRegistry registry) {
		this(registry, TargetInvalidationRegistryServices.DEFAULT_ADDRESS);
	}

	public TargetInvalidationRegistryServiceVerticle(
		TargetInvalidationRegistry registry,
		String address
	) {
		this.registry = Objects.requireNonNull(registry, "registry");
		this.address = TargetInvalidationRegistryServices.requireAddress(address);
	}

	@Override
	public void start(Promise<Void> startPromise) {
		consumer = vertx.eventBus().consumer(address, this::handle);
		consumer.completion().onComplete(startPromise);
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (consumer == null) {
			stopPromise.complete();
			return;
		}

		MessageConsumer<JsonObject> unregistering = consumer;
		consumer = null;
		unregistering.unregister().onComplete(stopPromise);
	}

	private void handle(Message<JsonObject> message) {
		try {
			invoke(message.body()).onComplete(result -> {
				if (result.succeeded()) {
					message.reply(result.result());
				} else {
					message.fail(500, failureMessage(result.cause()));
				}
			});
		} catch (Throwable error) {
			message.fail(500, failureMessage(error));
		}
	}

	private Future<JsonObject> invoke(JsonObject request) {
		if (request == null) {
			return Future.failedFuture("Request is required");
		}

		String operation = request.getString(TargetInvalidationRegistryServices.OPERATION);
		if (TargetInvalidationRegistryServices.MARK_INVALIDATED.equals(operation)) {
			Integer targetId = request.getInteger(TargetInvalidationRegistryServices.TARGET_ID);
			if (targetId == null) {
				return Future.failedFuture("target_id is required");
			}
			return registry.markInvalidated(targetId).map(new JsonObject());
		}

		if (TargetInvalidationRegistryServices.LIST_INVALIDATIONS.equals(operation)) {
			Integer maxTargets = request.getInteger(TargetInvalidationRegistryServices.MAX_TARGETS);
			if (maxTargets == null) {
				return Future.failedFuture("max_targets is required");
			}
			return registry.listInvalidations(maxTargets)
				.map(TargetInvalidationRegistryServices::toJson);
		}

		return Future.failedFuture("Unsupported operation: " + operation);
	}

	private String failureMessage(Throwable error) {
		return error == null || error.getMessage() == null
			? "Target invalidation registry request failed"
			: error.getMessage();
	}
}
