package com.inqwise.indexer.rest.indexer;

import java.util.List;

import com.inqwise.indexer.catalog.indexers.IndexerProvisioningState;
import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerStatus;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.catalog.indexers.MutationState;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.service.indexer.IndexerCatalogService;
import com.inqwise.indexer.service.indexer.IndexerCatalogServices;
import com.inqwise.indexer.service.indexer.IndexerGetRequest;
import com.inqwise.indexer.service.indexer.IndexerListResult;
import com.inqwise.indexer.service.indexer.IndexerQuery;
import com.inqwise.indexer.service.indexer.IndexerResult;
import com.inqwise.indexer.service.indexer.IndexerVersionRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class IndexerCatalogRestVerticle extends AbstractVerticle {
	private final IndexerCatalogRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public IndexerCatalogRestVerticle() {
		configuredOptions = null;
	}

	public IndexerCatalogRestVerticle(IndexerCatalogRestOptions options) {
		configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		IndexerCatalogRestOptions options = configuredOptions == null
			? new IndexerCatalogRestOptions(config())
			: configuredOptions;
		IndexerCatalogService service = IndexerCatalogServices.proxy(
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
					"listIndexers",
					context -> service.list(query(context)),
					IndexerCatalogRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"getIndexer",
					context -> service.get(new IndexerGetRequest()
						.setId(pathInteger(context, "id"))),
					IndexerCatalogRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"activateIndexer",
					context -> service.activate(versionRequest(context)),
					IndexerCatalogRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"deactivateIndexer",
					context -> service.deactivate(versionRequest(context)),
					IndexerCatalogRestVerticle::toJson
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
		if (result instanceof IndexerListResult list) {
			return list.toJson();
		}
		if (result instanceof IndexerResult indexer) {
			return indexer.toJson();
		}
		throw IndexerErrors.invalidRequest("Unsupported Indexer Catalog REST result");
	}

	private static IndexerQuery query(RoutingContext context) {
		return new IndexerQuery()
			.setIds(queryIntegers(context, "id"))
			.setTargetIds(queryIntegers(context, "target_id"))
			.setTypes(queryEnums(context, "type", IndexerType.class))
			.setRoles(queryEnums(context, "role", IndexerRole.class))
			.setStatuses(queryEnums(context, "status", IndexerStatus.class))
			.setProvisioningStates(queryEnums(
				context,
				"provisioning_state",
				IndexerProvisioningState.class
			))
			.setRuntimeStates(queryEnums(context, "runtime_state", IndexerRuntimeState.class))
			.setMutationStates(queryEnums(context, "mutation_state", MutationState.class));
	}

	private static IndexerVersionRequest versionRequest(RoutingContext context) {
		return new IndexerVersionRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
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
		return context.queryParam(name).stream().map(value -> {
			try {
				return Enum.valueOf(type, value);
			} catch (IllegalArgumentException error) {
				throw IndexerErrors.invalidRequest("Invalid value for " + name + ": " + value);
			}
		}).toList();
	}
}
