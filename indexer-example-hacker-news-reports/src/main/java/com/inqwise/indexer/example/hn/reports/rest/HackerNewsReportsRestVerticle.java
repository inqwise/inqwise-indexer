package com.inqwise.indexer.example.hn.reports.rest;

import java.util.Objects;

import com.inqwise.indexer.example.hn.reports.DefaultHackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryRequest;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryRequestCodec;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryResultCodec;
import com.inqwise.indexer.example.hn.reports.HackerNewsReports;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesRequest;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesRequestCodec;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesResultCodec;
import com.inqwise.indexer.query.TypedReportExecutor;
import com.inqwise.indexer.query.service.ReportsService;
import com.inqwise.indexer.query.service.ReportsServices;
import com.inqwise.indexer.rest.RestOperations;
import com.inqwise.indexer.service.IndexerErrors;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RequestExtractor;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;

public final class HackerNewsReportsRestVerticle extends AbstractVerticle {
	private static final HackerNewsStoriesRequestCodec REQUEST_CODEC =
		new HackerNewsStoriesRequestCodec();
	private static final HackerNewsStoriesResultCodec RESULT_CODEC =
		new HackerNewsStoriesResultCodec();
	private static final HackerNewsAuthorSummaryRequestCodec AUTHOR_REQUEST_CODEC =
		new HackerNewsAuthorSummaryRequestCodec();
	private static final HackerNewsAuthorSummaryResultCodec AUTHOR_RESULT_CODEC =
		new HackerNewsAuthorSummaryResultCodec();

	private final HackerNewsReportsRestOptions configuredOptions;
	private HttpServer server;
	private int actualPort = -1;

	public HackerNewsReportsRestVerticle() {
		configuredOptions = null;
	}

	public HackerNewsReportsRestVerticle(HackerNewsReportsRestOptions options) {
		configuredOptions = Objects.requireNonNull(options, "options");
	}

	@Override
	public void start(Promise<Void> startPromise) {
		HackerNewsReportsRestOptions options = configuredOptions == null
			? HackerNewsReportsRestOptions.from(config())
			: configuredOptions;
		ReportsService reports = ReportsServices.proxy(vertx, options.reportsAddress());

		OpenAPIContract.from(vertx, options.openApiPath())
			.map(contract -> {
				RouterBuilder builder = RouterBuilder.create(
					vertx,
					contract,
					RequestExtractor.withBodyHandler()
				);
				builder.rootHandler(BodyHandler.create());
				RestOperations.bind(
					builder,
					"queryHackerNewsStories",
					context -> reports(reports)
						.stories(request(context)),
					HackerNewsReportsRestVerticle::toJson
				);
				RestOperations.bind(
					builder,
					"queryHackerNewsStoryAuthors",
					context -> reports(reports)
						.storyAuthors(authorRequest(context)),
					HackerNewsReportsRestVerticle::toJson
				);
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

	private static HackerNewsReports reports(ReportsService reports) {
		return new DefaultHackerNewsReports(new TypedReportExecutor(reports));
	}

	private static HackerNewsStoriesRequest request(RoutingContext context) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}
		try {
			return REQUEST_CODEC.decode(body);
		} catch (IllegalArgumentException | NullPointerException error) {
			throw IndexerErrors.invalidRequest(error.getMessage());
		}
	}

	private static HackerNewsAuthorSummaryRequest authorRequest(
		RoutingContext context
	) {
		JsonObject body = context.body().asJsonObject();
		if (body == null) {
			throw IndexerErrors.invalidRequest("Request body is required");
		}
		try {
			return AUTHOR_REQUEST_CODEC.decode(body);
		} catch (IllegalArgumentException | NullPointerException error) {
			throw IndexerErrors.invalidRequest(error.getMessage());
		}
	}

	private static JsonObject toJson(Object result) {
		if (result instanceof HackerNewsStoriesResult stories) {
			return RESULT_CODEC.encode(stories);
		}
		if (result instanceof HackerNewsAuthorSummaryResult authors) {
			return AUTHOR_RESULT_CODEC.encode(authors);
		}
		throw new IllegalStateException(
			"Unsupported Hacker News report REST result: " + result.getClass().getName()
		);
	}
}
