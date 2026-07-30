package com.inqwise.indexer.example.hn;

import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public final class HackerNewsDocumentProjector {
	public HackerNewsProjection project(HackerNewsItem item) {
		String uid = Long.toString(item.id());
		if (item.deleted() || item.dead()) {
			return HackerNewsProjection.builder()
				.withItemId(item.id())
				.withFingerprint("REMOVE")
				.withAction(RemoveDocumentActionItem.builder().withUid(uid).build())
				.build();
		}

		JsonObject document = new JsonObject()
			.put("id", item.id())
			.put("type", item.type())
			.put("by", item.by())
			.put("time", item.time())
			.put("title", item.title())
			.put("url", item.url())
			.put("text", item.text())
			.put("parent", item.parent())
			.put("poll", item.poll())
			.put("score", item.score())
			.put("descendants", item.descendants())
			.put("kids", new JsonArray(item.kids()))
			.put("parts", new JsonArray(item.parts()))
			.put("source", "hacker-news");

		return HackerNewsProjection.builder()
			.withItemId(item.id())
			.withFingerprint(document.encode())
			.withAction(PutDocumentActionItem.builder()
				.withUid(uid)
				.withDocument(document)
				.build())
			.build();
	}
}
