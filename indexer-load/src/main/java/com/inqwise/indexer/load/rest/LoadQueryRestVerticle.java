package com.inqwise.indexer.load.rest;

import java.util.List;

import com.inqwise.indexer.load.service.LoadListRequest;
import com.inqwise.indexer.load.service.LoadQueryService;
import com.inqwise.indexer.load.service.LoadQueryServices;
import com.inqwise.indexer.load.service.LoadServiceErrors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class LoadQueryRestVerticle extends AbstractVerticle {
	private final LoadQueryRestOptions options;
	private HttpServer server;
	private int actualPort = -1;

	public LoadQueryRestVerticle(LoadQueryRestOptions options) {
		this.options = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		LoadQueryService service = LoadQueryServices.proxy(vertx, options.serviceAddress());
		OpenAPIContract.from(vertx, options.openApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.getRoute("listLoads")
					.addHandler(context -> service.list(request(context))
						.onSuccess(result -> context.response()
							.putHeader("content-type", "application/json")
							.end(result.toJson().encode()))
						.onFailure(error -> LoadHttpErrorMapper.write(context, error)))
					.addFailureHandler(context -> LoadHttpErrorMapper.write(context, context.failure()));
				return builder.createRouter();
			})
			.compose(router -> vertx.createHttpServer()
				.requestHandler(router)
				.listen(options.port(), options.host()))
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

	public int actualPort() {
		return actualPort;
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		if (server == null) {
			stopPromise.complete();
			return;
		}
		server.close().onComplete(stopPromise);
	}

	private static LoadListRequest request(RoutingContext context) {
		List<String> values = context.queryParam("max");
		if (values.isEmpty()) {
			return LoadListRequest.builder().build();
		}
		try {
			return LoadListRequest.builder().withMax(Integer.valueOf(values.get(0))).build();
		} catch (NumberFormatException error) {
			throw LoadServiceErrors.invalidRequest("Invalid integer value for max");
		}
	}
}
