package com.inqwise.indexer.rest.document;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.inqwise.indexer.documents.DocumentQuery;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.IndexerErrors;
import com.inqwise.indexer.service.document.DocumentQueryService;
import com.inqwise.indexer.service.document.DocumentQueryServices;
import com.inqwise.indexer.service.document.DocumentSearchRequest;
import com.inqwise.indexer.service.document.DocumentSearchResult;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class DocumentQueryRestVerticle extends AbstractVerticle {
	private final DocumentQueryRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public DocumentQueryRestVerticle() {
		configuredOptions = null;
	}

	public DocumentQueryRestVerticle(DocumentQueryRestOptions options) {
		configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		DocumentQueryRestOptions options = configuredOptions == null
			? new DocumentQueryRestOptions(config())
			: configuredOptions;
		DocumentQueryService service = DocumentQueryServices.proxy(
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
					"searchDocuments",
					context -> service.search(request(context)),
					DocumentQueryRestVerticle::toJson
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
		if (result instanceof DocumentSearchResult search) {
			return search.toJson();
		}
		throw IndexerErrors.invalidRequest("Unsupported Document Query REST result");
	}

	private static DocumentSearchRequest request(RoutingContext context) {
		return DocumentSearchRequest.builder()
			.withTargetName(requiredQuery(context, "target_name"))
			.withQueryText(optionalQuery(context, "q"))
			.withFromEpochMs(optionalInstant(context, "from"))
			.withToEpochMs(optionalInstant(context, "to"))
			.withOffset(optionalInteger(context, "offset", 0))
			.withLimit(optionalInteger(context, "limit", DocumentQuery.DEFAULT_LIMIT))
			.build();
	}

	private static String requiredQuery(RoutingContext context, String name) {
		String value = optionalQuery(context, name);
		if (value == null || value.isBlank()) {
			throw IndexerErrors.invalidRequest("Missing required query parameter: " + name);
		}
		return value;
	}

	private static String optionalQuery(RoutingContext context, String name) {
		List<String> values = context.queryParam(name);
		return values.isEmpty() ? null : values.get(0);
	}

	private static Long optionalInstant(RoutingContext context, String name) {
		String value = optionalQuery(context, name);
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Instant.parse(value).toEpochMilli();
		} catch (DateTimeParseException | ArithmeticException error) {
			throw IndexerErrors.invalidRequest("Invalid timestamp value for " + name);
		}
	}

	private static int optionalInteger(
		RoutingContext context,
		String name,
		int defaultValue
	) {
		String value = optionalQuery(context, name);
		if (value == null || value.isBlank()) {
			return defaultValue;
		}
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException error) {
			throw IndexerErrors.invalidRequest("Invalid integer value for " + name);
		}
	}
}
