package com.inqwise.indexer.web;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.httpproxy.HttpProxy;
import io.vertx.httpproxy.ProxyInterceptor;

public final class IndexerWebVerticle extends AbstractVerticle {
	private static final String ADMIN_PREFIX = "/api/admin";
	private static final String TARGET_ACTION_PREFIX = "/api/actions";
	private static final String RUNTIME_PREFIX = "/api/runtime";
	private static final String HEALTH_PREFIX = "/api/health";
	private static final String METRICS_PREFIX = "/api/metrics";
	private static final String REPORTS_PREFIX = "/api/reports";
	private static final String LOADS_PREFIX = "/api/loads";

	private final IndexerWebOptions configuredOptions;
	private HttpClient proxyClient;
	private HttpServer server;
	private int actualPort = -1;

	public IndexerWebVerticle() {
		configuredOptions = null;
	}

	public IndexerWebVerticle(IndexerWebOptions options) {
		configuredOptions = options;
	}

	@Override
	public void start(Promise<Void> startPromise) {
		IndexerWebOptions options = configuredOptions == null
			? IndexerWebOptions.from(config())
			: configuredOptions;
		proxyClient = vertx.createHttpClient();

		Router router = Router.router(vertx);
		mountProxy(
			router,
			ADMIN_PREFIX,
			options.adminHost(),
			options.adminPort()
		);
		mountProxy(
			router,
			TARGET_ACTION_PREFIX,
			options.targetActionHost(),
			options.targetActionPort()
		);
		mountProxy(
			router,
			RUNTIME_PREFIX,
			options.runtimeHost(),
			options.runtimePort()
		);
		mountProxy(
			router,
			HEALTH_PREFIX,
			options.healthHost(),
			options.healthPort()
		);
		mountProxy(
			router,
			METRICS_PREFIX,
			options.metricsHost(),
			options.metricsPort()
		);
		mountProxy(
			router,
			REPORTS_PREFIX,
			options.reportsHost(),
			options.reportsPort()
		);
		mountProxy(
			router,
			LOADS_PREFIX,
			options.loadsHost(),
			options.loadsPort()
		);

		router.route("/api").handler(IndexerWebVerticle::rejectUnknownApiRoute);
		router.route("/api/*").handler(IndexerWebVerticle::rejectUnknownApiRoute);
		router.route().handler(context -> {
			context.response().putHeader("cache-control", "no-cache");
			context.next();
		});
		router.route().handler(StaticHandler.create()
			.setIndexPage("index.html")
			.setCachingEnabled(false));
		router.get().handler(IndexerWebVerticle::serveSpaEntry);
		router.route().handler(context -> context.response().setStatusCode(404).end());

		vertx.createHttpServer()
			.requestHandler(router)
			.listen(options.port(), options.host())
			.onComplete(result -> {
				if (result.succeeded()) {
					server = result.result();
					actualPort = server.actualPort();
					startPromise.complete();
				} else {
					closeProxyClient()
						.onComplete(ignored -> startPromise.fail(result.cause()));
				}
			});
	}

	private static void rejectUnknownApiRoute(RoutingContext context) {
		context.response()
			.setStatusCode(404)
			.putHeader("content-type", "application/json")
			.end("{\"status\":404,\"message\":\"Unknown web API route\"}");
	}

	@Override
	public void stop(Promise<Void> stopPromise) {
		Future<Void> closeServer = server == null
			? Future.succeededFuture()
			: server.close();
		closeServer
			.compose(ignored -> closeProxyClient())
			.onComplete(stopPromise);
	}

	public int actualPort() {
		return actualPort;
	}

	private void mountProxy(
		Router router,
		String prefix,
		String upstreamHost,
		int upstreamPort
	) {
		HttpProxy proxy = HttpProxy.reverseProxy(proxyClient)
			.origin(upstreamPort, upstreamHost)
			.addInterceptor(ProxyInterceptor.builder()
				.removingPathPrefix(prefix)
				.build());
		router.route(prefix).handler(context -> proxy.handle(context.request()));
		router.route(prefix + "/*").handler(context -> proxy.handle(context.request()));
	}

	private static void serveSpaEntry(RoutingContext context) {
		String path = context.normalizedPath();
		String segment = path.substring(path.lastIndexOf('/') + 1);
		if (segment.contains(".")) {
			context.response().setStatusCode(404).end();
			return;
		}
		context.reroute("/index.html");
	}

	private Future<Void> closeProxyClient() {
		if (proxyClient == null) {
			return Future.succeededFuture();
		}
		HttpClient client = proxyClient;
		proxyClient = null;
		return client.close();
	}
}
