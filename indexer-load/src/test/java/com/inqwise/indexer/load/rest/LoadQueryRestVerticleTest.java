package com.inqwise.indexer.load.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.load.adapters.local.InMemoryIndexerLoadRepository;
import com.inqwise.indexer.load.api.IndexerLoadState;
import com.inqwise.indexer.load.api.LiveWriterPolicy;
import com.inqwise.indexer.load.repository.InsertIndexerLoad;
import com.inqwise.indexer.load.service.LoadQueryServiceVerticle;
import com.inqwise.indexer.load.service.LoadQueryServices;
import com.inqwise.indexer.load.workflow.RepositoryLoadQuery;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class LoadQueryRestVerticleTest {
	@Test
	void listsBoundedLoadViewsThroughReadOnlyService(
		Vertx vertx,
		VertxTestContext testContext
	) {
		InMemoryIndexerLoadRepository repository = new InMemoryIndexerLoadRepository();
		String address = LoadQueryServices.DEFAULT_ADDRESS + ".test";
		LoadQueryRestVerticle rest = new LoadQueryRestVerticle(
			LoadQueryRestOptions.builder()
				.withPort(0)
				.withServiceAddress(address)
				.build()
		);

		repository.insert(InsertIndexerLoad.builder()
			.withIndexerId(91)
			.withTargetId(11)
			.withLiveIndexerId(92)
			.withLiveWriterPolicy(LiveWriterPolicy.CREATE_IMMEDIATELY)
			.withProviderId("archive")
			.withState(IndexerLoadState.HISTORICAL_LOADING)
			.withReviewRequired(true)
			.build())
			.compose(ignored -> vertx.deployVerticle(
				new LoadQueryServiceVerticle(new RepositoryLoadQuery(repository), address)
			))
			.compose(ignored -> vertx.deployVerticle(rest))
			.compose(ignored -> vertx.createHttpClient()
				.request(HttpMethod.GET, rest.actualPort(), "127.0.0.1", "/admin/loads?max=10"))
			.compose(request -> request.send())
			.compose(response -> {
				assertEquals(200, response.statusCode());
				return response.body();
			})
			.onComplete(testContext.succeeding(body -> testContext.verify(() -> {
				var loads = body.toJsonObject().getJsonArray("loads");
				assertEquals(1, loads.size());
				assertEquals(91, loads.getJsonObject(0).getInteger("indexer_id"));
				assertEquals(11, loads.getJsonObject(0).getInteger("target_id"));
				assertEquals("HISTORICAL_LOADING", loads.getJsonObject(0).getString("state"));
				assertEquals(null, loads.getJsonObject(0).getValue("source_query"));
				testContext.completeNow();
			})));
	}
}
