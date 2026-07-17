package com.inqwise.indexer.rest.target;

import java.time.Instant;
import java.util.List;

import com.inqwise.indexer.catalog.targets.InitialPublicationMode;
import com.inqwise.indexer.catalog.targets.TargetProvisioningState;
import com.inqwise.indexer.catalog.targets.TargetStatus;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.service.target.TargetCatalogService;
import com.inqwise.indexer.service.target.TargetCatalogServices;
import com.inqwise.indexer.service.target.TargetCreateRequest;
import com.inqwise.indexer.service.target.TargetGetRequest;
import com.inqwise.indexer.service.target.TargetListResult;
import com.inqwise.indexer.service.target.TargetQuery;
import com.inqwise.indexer.service.target.TargetResult;
import com.inqwise.indexer.service.target.TargetVersionRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class TargetCatalogRestVerticle extends AbstractVerticle {
	private final TargetCatalogRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public TargetCatalogRestVerticle() {
		configuredOptions = null;
	}

	public TargetCatalogRestVerticle(TargetCatalogRestOptions options) {
		configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		TargetCatalogRestOptions options = configuredOptions == null
			? new TargetCatalogRestOptions(config())
			: configuredOptions;
		TargetCatalogService service = TargetCatalogServices.proxy(
			vertx,
			options.getServiceAddress()
		);

		OpenAPIContract.from(vertx, options.getOpenApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				RestOperations.bind(
					builder,
					"listTargets",
					context -> service.list(query(context)),
					TargetCatalogRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"createTarget",
					context -> service.create(createRequest(context)),
					TargetCatalogRestVerticle::toJson,
					201
				);
				RestOperations.bind(
					builder,
					"getTarget",
					context -> service.get(new TargetGetRequest()
						.setId(pathInteger(context, "id"))),
					TargetCatalogRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"recoverTargetProvisioning",
					context -> service.recoverProvisioning(new TargetVersionRequest()
						.setTargetId(pathInteger(context, "id"))
						.setExpectedVersion(requiredQueryLong(context, "expected_version"))),
					TargetCatalogRestVerticle::toJson
				);
				return builder.createRouter();
			})
			.compose(router -> vertx.createHttpServer()
				.requestHandler(router)
				.listen(options.getPort(), options.getHost()))
			.onComplete(result -> {
				if (result.succeeded()) {
					server = result.result();
					actualPort = server.actualPort();
					startPromise.complete();
				} else {
					startPromise.fail(result.cause());
				}
			});
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (server == null) {
			stopPromise.complete();
			return;
		}
		server.close().onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}

	private static JsonObject toJson(Object result) {
		if (result instanceof TargetListResult list) {
			return list.toJson();
		}
		if (result instanceof TargetResult target) {
			return target.toJson();
		}
		throw IndexerErrors.invalidRequest("Unsupported Target Catalog REST result");
	}

	private static TargetQuery query(RoutingContext context) {
		return new TargetQuery()
			.setIds(queryIntegers(context, "id"))
			.setTargetNames(context.queryParam("target_name"))
			.setStatuses(queryEnums(context, "status", TargetStatus.class))
			.setProvisioningStates(queryEnums(
				context,
				"provisioning_state",
				TargetProvisioningState.class
			));
	}

	private static TargetCreateRequest createRequest(RoutingContext context) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}
		String timestampValue = body.getString("timestamp");
		String modeValue = body.getString("initial_publication_mode");
		return new TargetCreateRequest()
			.setTargetName(body.getString("target_name"))
			.setTimestamp(timestampValue == null ? null : Instant.parse(timestampValue))
			.setInitialPublicationMode(modeValue == null
				? null
				: enumValue(modeValue, "initial_publication_mode", InitialPublicationMode.class));
	}

	private static Integer pathInteger(RoutingContext context, String name) {
		try {
			return Integer.valueOf(context.pathParam(name));
		} catch (NumberFormatException error) {
			throw IndexerErrors.invalidRequest("Invalid integer value for " + name);
		}
	}

	private static long requiredQueryLong(RoutingContext context, String name) {
		List<String> values = context.queryParam(name);
		if (values.isEmpty()) {
			throw IndexerErrors.invalidRequest("Missing required query parameter: " + name);
		}
		try {
			return Long.parseLong(values.get(0));
		} catch (NumberFormatException error) {
			throw IndexerErrors.invalidRequest("Invalid long value for " + name);
		}
	}

	private static List<Integer> queryIntegers(RoutingContext context, String name) {
		return context.queryParam(name).stream().map(value -> {
			try {
				return Integer.valueOf(value);
			} catch (NumberFormatException error) {
				throw IndexerErrors.invalidRequest("Invalid integer value for " + name);
			}
		}).toList();
	}

	private static <E extends Enum<E>> List<E> queryEnums(
		RoutingContext context,
		String name,
		Class<E> type
	) {
		return context.queryParam(name).stream()
			.map(value -> enumValue(value, name, type))
			.toList();
	}

	private static <E extends Enum<E>> E enumValue(String value, String name, Class<E> type) {
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException error) {
			throw IndexerErrors.invalidRequest("Invalid value for " + name + ": " + value);
		}
	}
}
