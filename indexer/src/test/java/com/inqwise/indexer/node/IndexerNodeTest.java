package com.inqwise.indexer.node;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.inqwise.indexer.service.admin.AdminServices;
import com.inqwise.indexer.service.runtime.RuntimeServices;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class IndexerNodeTest {
	@Test
	void deploysEnabledServices(Vertx vertx, VertxTestContext testContext) {
		IndexerNode node = IndexerNode.create(vertx, new IndexerNodeOptions());

		node.start()
			.compose(ignored -> AdminServices.proxy(vertx).listTargets(null))
			.compose(targets -> {
				assertEquals(0, targets.getTargets().size());
				return RuntimeServices.proxy(vertx).status();
			})
			.compose(status -> {
				assertEquals(0, status.getIndexers().size());
				return node.stop();
			})
			.onComplete(testContext.succeeding(ignored -> testContext.completeNow()));
	}
}
