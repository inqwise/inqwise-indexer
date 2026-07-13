package com.inqwise.indexer.rest.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.catalog.indexers.IndexerRole;
import com.inqwise.indexer.catalog.indexers.IndexerRuntimeState;
import com.inqwise.indexer.catalog.indexers.IndexerType;
import com.inqwise.indexer.commands.InitialPublicationMode;
import com.inqwise.indexer.errors.IndexerErrors;
import com.inqwise.indexer.metadata.IndexerProvisioningState;
import com.inqwise.indexer.metadata.IndexerStatus;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;
import com.inqwise.indexer.metadata.TargetProvisioningState;
import com.inqwise.indexer.metadata.TargetStatus;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.admin.AdminCreateRequestResolver;
import com.inqwise.indexer.service.admin.AdminCreateIndexerRequest;
import com.inqwise.indexer.service.admin.AdminCreateTargetRequest;
import com.inqwise.indexer.service.admin.AdminDeleteIndexerRequest;
import com.inqwise.indexer.service.admin.AdminIndexerGetRequest;
import com.inqwise.indexer.service.admin.AdminIndexerLifecycleRequest;
import com.inqwise.indexer.service.admin.AdminIndexerListResult;
import com.inqwise.indexer.service.admin.AdminIndexerQuery;
import com.inqwise.indexer.service.admin.AdminIndexerResult;
import com.inqwise.indexer.service.admin.AdminRecoverTargetProvisioningRequest;
import com.inqwise.indexer.service.admin.AdminResetIndexerQueueRequest;
import com.inqwise.indexer.service.admin.AdminService;
import com.inqwise.indexer.service.admin.AdminServices;
import com.inqwise.indexer.service.admin.AdminTargetGetRequest;
import com.inqwise.indexer.service.admin.AdminTargetListResult;
import com.inqwise.indexer.service.admin.AdminTargetQuery;
import com.inqwise.indexer.service.admin.AdminTargetResult;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public class AdminRestVerticle extends AbstractVerticle {
	private final AdminRestOptions configuredOptions;
	private final AdminCreateRequestResolver createRequestResolver;
	private HttpServer server;
	private int actualPort = -1;

	public AdminRestVerticle() {
		this.configuredOptions = null;
		this.createRequestResolver = null;
	}

	public AdminRestVerticle(AdminRestOptions options) {
		this(options, null);
	}

	public AdminRestVerticle(AdminRestOptions options, AdminCreateRequestResolver createRequestResolver) {
		this.configuredOptions = options;
		this.createRequestResolver = createRequestResolver;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		AdminRestOptions options = configuredOptions == null
			? new AdminRestOptions(config())
			: configuredOptions;
		AdminService adminService = AdminServices.proxy(vertx);
		AdminCreateRequestResolver createResolver = Objects.requireNonNull(
			createRequestResolver,
			"createRequestResolver"
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
					context -> adminService.listTargets(targetQuery(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"createTarget",
					context -> adminService.createTarget(createTargetRequest(context, createResolver)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"getTarget",
					context -> adminService.getTarget(new AdminTargetGetRequest()
						.setId(pathInteger(context, "id"))),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"recoverTargetProvisioning",
					context -> adminService.recoverTargetProvisioning(recoverTargetProvisioningRequest(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"listIndexers",
					context -> adminService.listIndexers(indexerQuery(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"createIndexer",
					context -> createIndexerRequest(context, createResolver)
						.compose(adminService::createIndexer),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"getIndexer",
					context -> adminService.getIndexer(new AdminIndexerGetRequest()
						.setId(pathInteger(context, "id"))),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"activateIndexer",
					context -> adminService.activateIndexer(indexerLifecycleRequest(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"deactivateIndexer",
					context -> adminService.deactivateIndexer(indexerLifecycleRequest(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"resetIndexerQueue",
					context -> adminService.resetIndexerQueue(resetIndexerQueueRequest(context)),
					AdminRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"deleteIndexer",
					context -> adminService.deleteIndexer(deleteIndexerRequest(context)),
					AdminRestVerticle::toJson
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

		server.close()
			.onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}

	private static JsonObject toJson(Object result) {
		if (result instanceof AdminTargetListResult value) {
			return value.toJson();
		}
		if (result instanceof AdminTargetResult value) {
			return value.toJson();
		}
		if (result instanceof AdminIndexerListResult value) {
			return value.toJson();
		}
		if (result instanceof AdminIndexerResult value) {
			return value.toJson();
		}
		throw IndexerErrors.invalidRequest("Unsupported admin REST result type: " + result.getClass().getName());
	}

	private static AdminTargetQuery targetQuery(RoutingContext context) {
		return new AdminTargetQuery()
			.setIds(queryIntegers(context, "id"))
			.setTargetNames(context.queryParam("target_name"))
			.setStatuses(queryEnums(context, "status", TargetStatus.class))
			.setProvisioningStates(queryEnums(context, "provisioning_state", TargetProvisioningState.class));
	}

	private static AdminCreateTargetRequest createTargetRequest(
		RoutingContext context,
		AdminCreateRequestResolver resolver
	) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}

		String targetName = body.getString("target_name");
		String date = body.getString("date");
		if (targetName == null || targetName.isBlank()) {
			throw IndexerErrors.invalidRequest("Target name is required");
		}
		if (date == null || date.isBlank()) {
			throw IndexerErrors.invalidRequest("Date is required");
		}

		JsonObject createIndexer = body.getJsonObject("create_indexer");
		InitialPublicationMode mode = null;
		if (createIndexer != null) {
			mode = enumValue(
				createIndexer.getString("initial_publication_mode"),
				"initial_publication_mode",
				InitialPublicationMode.class
			);
		}

		return resolver.target(
			targetName,
			parseDate(date).atStartOfDay().toInstant(ZoneOffset.UTC),
			mode
		);
	}

	private static Future<AdminCreateIndexerRequest> createIndexerRequest(
		RoutingContext context,
		AdminCreateRequestResolver resolver
	) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}

		return resolver.indexer(body.getInteger("target_id"));
	}

	private static AdminRecoverTargetProvisioningRequest recoverTargetProvisioningRequest(RoutingContext context) {
		return new AdminRecoverTargetProvisioningRequest()
			.setTargetId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static AdminIndexerQuery indexerQuery(RoutingContext context) {
		return new AdminIndexerQuery()
			.setIds(queryIntegers(context, "id"))
			.setTargetIds(queryIntegers(context, "target_id"))
			.setTypes(queryEnums(context, "type", IndexerType.class))
			.setRoles(queryEnums(context, "role", IndexerRole.class))
			.setStatuses(queryEnums(context, "status", IndexerStatus.class))
			.setProvisioningStates(queryEnums(context, "provisioning_state", IndexerProvisioningState.class))
			.setRuntimeStates(queryEnums(context, "runtime_state", IndexerRuntimeState.class))
			.setPublicationStates(queryEnums(context, "publication_state", PublicationState.class))
			.setMutationStates(queryEnums(context, "mutation_state", MutationState.class));
	}

	private static AdminIndexerLifecycleRequest indexerLifecycleRequest(RoutingContext context) {
		return new AdminIndexerLifecycleRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static AdminResetIndexerQueueRequest resetIndexerQueueRequest(RoutingContext context) {
		return new AdminResetIndexerQueueRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static AdminDeleteIndexerRequest deleteIndexerRequest(RoutingContext context) {
		return new AdminDeleteIndexerRequest()
			.setIndexerId(pathInteger(context, "id"))
			.setExpectedVersion(requiredQueryLong(context, "expected_version"));
	}

	private static Integer pathInteger(RoutingContext context, String name) {
		return integer(context.pathParam(name), name);
	}

	private static List<Integer> queryIntegers(RoutingContext context, String name) {
		return context.queryParam(name).stream()
			.map(value -> integer(value, name))
			.toList();
	}

	private static long requiredQueryLong(RoutingContext context, String name) {
		List<String> values = context.queryParam(name);
		if (values.isEmpty()) {
			throw IndexerErrors.invalidRequest("Missing required query parameter: " + name);
		}

		return longValue(values.get(0), name);
	}

	private static Integer integer(String value, String name) {
		try {
			return Integer.valueOf(value);
		} catch (NumberFormatException e) {
			throw IndexerErrors.invalidRequest("Invalid integer value for " + name + ": " + value);
		}
	}

	private static long longValue(String value, String name) {
		try {
			return Long.parseLong(value);
		} catch (NumberFormatException e) {
			throw IndexerErrors.invalidRequest("Invalid long value for " + name + ": " + value);
		}
	}

	private static LocalDate parseDate(String value) {
		try {
			return LocalDate.parse(value);
		} catch (RuntimeException e) {
			throw IndexerErrors.invalidRequest("Invalid date value: " + value);
		}
	}

	private static <E extends Enum<E>> List<E> queryEnums(RoutingContext context, String name, Class<E> type) {
		return context.queryParam(name).stream()
			.map(value -> enumValue(value, name, type))
			.toList();
	}

	private static <E extends Enum<E>> E enumValue(String value, String name, Class<E> type) {
		try {
			return Enum.valueOf(type, value);
		} catch (IllegalArgumentException e) {
			throw IndexerErrors.invalidRequest("Invalid enum value for " + name + ": " + value);
		}
	}
}
