package com.inqwise.indexer.hot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.IndexerActionItems;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.actions.IndexerActionRouteMode;
import com.inqwise.indexer.metadata.TargetPeriodStrategy;

import io.vertx.core.json.JsonObject;

class HotTargetTest {
	@Test
	void routesBatchToSingleLiveWriter() {
		HotTarget target = hotTarget(TargetPeriodStrategy.NONE, List.of(
			concreteTarget(null, List.of(new FakeHotIndexer(10, 100, "queue-a")))
		));

		HotRouteResult result = target.route(new HotIndexActionsRequest(
			"customers",
			null,
			List.of(IndexerActionItems.putDocument("42", new JsonObject().put("name", "Ada")))
		));

		HotRouteResult.Routed routed = assertInstanceOf(HotRouteResult.Routed.class, result);
		assertEquals(1, routed.groups().size());
		assertEquals(100, routed.groups().get(0).indexerId());
		assertEquals("queue-a", routed.groups().get(0).queueName());

		PutDocumentActionItem put = (PutDocumentActionItem) routed.groups().get(0).actions().get(0);
		assertEquals(10, put.getTargetId());
		assertEquals(100, put.getIndexerId());
		assertEquals("customers-index-100", put.getIndexName());
	}

	@Test
	void routesBatchToAllLiveWriters() {
		HotTarget target = hotTarget(TargetPeriodStrategy.NONE, List.of(
			concreteTarget(null, List.of(
				new FakeHotIndexer(10, 100, "queue-a"),
				new FakeHotIndexer(10, 101, "queue-b")
			))
		));

		HotRouteResult result = target.route(new HotIndexActionsRequest(
			"customers",
			null,
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		));

		HotRouteResult.Routed routed = assertInstanceOf(HotRouteResult.Routed.class, result);
		assertEquals(2, routed.groups().size());
		assertEquals(100, routed.groups().get(0).indexerId());
		assertEquals(101, routed.groups().get(1).indexerId());
	}

	@Test
	void missesWhenConcreteTargetHasNoLiveWriters() {
		HotTarget target = hotTarget(TargetPeriodStrategy.NONE, List.of(
			concreteTarget(null, List.of())
		));

		HotRouteResult result = target.route(new HotIndexActionsRequest(
			"customers",
			null,
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		));

		assertInstanceOf(HotRouteResult.Miss.class, result);
	}

	@Test
	void selectsConcreteTargetByPeriod() {
		HotTarget target = hotTarget(TargetPeriodStrategy.MONTHLY, List.of(
			concreteTarget("2026-04", List.of(new FakeHotIndexer(10, 100, "queue-apr"))),
			concreteTarget("2026-05", List.of(new FakeHotIndexer(11, 101, "queue-may")))
		));

		HotRouteResult result = target.route(new HotIndexActionsRequest(
			"customers",
			Instant.parse("2026-05-18T10:15:00Z"),
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		));

		HotRouteResult.Routed routed = assertInstanceOf(HotRouteResult.Routed.class, result);
		assertEquals(1, routed.groups().size());
		assertEquals(101, routed.groups().get(0).indexerId());
		assertEquals("queue-may", routed.groups().get(0).queueName());
	}

	@Test
	void missesWhenAnyActionIsNotAccepted() {
		HotTarget target = hotTarget(TargetPeriodStrategy.NONE, List.of(
			concreteTarget(null, List.of(new RejectingHotIndexer(10, 100, "queue-a")))
		));

		HotRouteResult result = target.route(new HotIndexActionsRequest(
			"customers",
			null,
			List.of(IndexerActionItems.putDocument("42", new JsonObject()))
		));

		assertInstanceOf(HotRouteResult.Miss.class, result);
	}

	private HotTarget hotTarget(
		TargetPeriodStrategy periodStrategy,
		List<HotConcreteTarget> concreteTargets
	) {
		return new HotTarget(
			"customers",
			periodStrategy,
			concreteTargets
		);
	}

	private HotConcreteTarget concreteTarget(
		String periodKey,
		List<HotIndexer> liveWriters
	) {
		return new HotConcreteTarget(
			periodKey == null ? 10 : "2026-05".equals(periodKey) ? 11 : 10,
			periodKey == null ? "customers" : "customers--" + periodKey,
			periodKey,
			null,
			null,
			liveWriters
		);
	}

	private static class FakeHotIndexer implements HotIndexer {
		private final Integer targetId;
		private final Integer indexerId;
		private final String queueName;

		private FakeHotIndexer(Integer targetId, Integer indexerId, String queueName) {
			this.targetId = targetId;
			this.indexerId = indexerId;
			this.queueName = queueName;
		}

		@Override
		public Integer id() {
			return indexerId;
		}

		@Override
		public Integer targetId() {
			return targetId;
		}

		@Override
		public String queueName() {
			return queueName;
		}

		@Override
		public Optional<IndexerActionItem> route(IndexerActionItem item, IndexerActionRouteMode mode) {
			PutDocumentActionItem put = (PutDocumentActionItem) item;
			return Optional.of(IndexerActionItems.concretePutDocument(
				targetId,
				indexerId,
				"customers-index-" + indexerId,
				put.getUid(),
				put.getDocument()
			));
		}
	}

	private static class RejectingHotIndexer extends FakeHotIndexer {
		private RejectingHotIndexer(Integer targetId, Integer indexerId, String queueName) {
			super(targetId, indexerId, queueName);
		}

		@Override
		public Optional<IndexerActionItem> route(IndexerActionItem item, IndexerActionRouteMode mode) {
			return Optional.empty();
		}
	}
}
