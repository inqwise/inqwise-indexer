package com.inqwise.indexer.example.hn;

import java.util.Objects;

import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;

import io.vertx.core.json.JsonObject;

public final class HackerNewsDocumentProjector {
	private static final HackerNewsDocumentCodec DOCUMENT_CODEC =
		new HackerNewsDocumentCodec();

	public HackerNewsProjection project(HackerNewsItem item) {
		String uid = Long.toString(item.id());
		if (item.deleted() || item.dead()) {
			return HackerNewsProjection.builder()
				.withItemId(item.id())
				.withFingerprint("REMOVE")
				.withAction(RemoveDocumentActionItem.builder().withUid(uid).build())
				.build();
		}

		HackerNewsDocument model = HackerNewsDocument.builder()
			.withId(item.id())
			.withType(item.type())
			.withAuthor(item.by())
			.withTime(Objects.requireNonNull(item.time(), "item.time"))
			.withTitle(item.title())
			.withUrl(item.url())
			.withText(item.text())
			.withParent(item.parent())
			.withPoll(item.poll())
			.withScore(item.score() == null ? 0 : item.score())
			.withDescendants(item.descendants() == null ? 0 : item.descendants())
			.withKids(item.kids())
			.withParts(item.parts())
			.build();
		JsonObject document = DOCUMENT_CODEC.encode(model);

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
