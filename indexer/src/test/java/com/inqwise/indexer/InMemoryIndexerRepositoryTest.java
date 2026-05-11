package com.inqwise.indexer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Future;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;

@ExtendWith(VertxExtension.class)
class InMemoryIndexerRepositoryTest {
	@Test
	void savesAndFindsIndexerModels(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		IndexerModel model = IndexerModel.builder()
			.withUid("customers-current")
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();

		repository.save(model)
			.compose(repository::get)
			.onComplete(testContext.succeeding(found -> testContext.verify(() -> {
				assertTrue(found.isPresent());
				assertEquals("customers_1", found.get().getIndexName());
				testContext.completeNow();
			})));
	}

	@Test
	void listsByTargetNameAndDeletesByUid(VertxTestContext testContext) {
		InMemoryIndexerRepository repository = new InMemoryIndexerRepository();
		IndexerModel first = IndexerModel.builder()
			.withUid("customers-current")
			.withTargetId(10)
			.withTargetName("customers")
			.withIndexName("customers_1")
			.build();
		IndexerModel second = IndexerModel.builder()
			.withUid("orders-current")
			.withTargetId(20)
			.withTargetName("orders")
			.withIndexName("orders_1")
			.build();

		repository.save(first)
			.compose(firstId -> repository.save(second)
				.compose(ignored -> repository.getByTargetId(10))
				.compose(customers -> {
					assertEquals(1, customers.size());
					assertEquals("customers-current", customers.get(0).getUid());
					return Future.succeededFuture();
				})
				.compose(ignored -> repository.delete(firstId)))
			.compose(deleted -> {
				assertTrue(deleted);
				return Future.succeededFuture();
			})
			.compose(ignored -> repository.list())
			.onComplete(testContext.succeeding(remaining -> testContext.verify(() -> {
				assertEquals(1, remaining.size());
				assertEquals("orders-current", remaining.get(0).getUid());
				testContext.completeNow();
			})));
	}
}
