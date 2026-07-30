package com.inqwise.indexer.service.document;

import java.util.List;

import com.inqwise.indexer.documents.DocumentQueryResult;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class DocumentSearchResult {
	private List<DocumentHitView> hits = List.of();
	private int offset;
	private int limit;
	private boolean hasMore;
	private int publishedIndexCount;

	public DocumentSearchResult() {
	}

	public DocumentSearchResult(JsonObject json) {
		hits = json.getJsonArray("hits", new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(DocumentHitView::new)
			.toList();
		offset = json.getInteger("offset", 0);
		limit = json.getInteger("limit", 0);
		hasMore = json.getBoolean("has_more", false);
		publishedIndexCount = json.getInteger("published_index_count", 0);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("hits", new JsonArray(hits.stream().map(DocumentHitView::toJson).toList()))
			.put("offset", offset)
			.put("limit", limit)
			.put("has_more", hasMore)
			.put("published_index_count", publishedIndexCount);
	}

	public List<DocumentHitView> getHits() {
		return List.copyOf(hits);
	}

	public DocumentSearchResult setHits(List<DocumentHitView> value) {
		hits = value == null ? List.of() : List.copyOf(value);
		return this;
	}

	public int getOffset() {
		return offset;
	}

	public DocumentSearchResult setOffset(int value) {
		offset = value;
		return this;
	}

	public int getLimit() {
		return limit;
	}

	public DocumentSearchResult setLimit(int value) {
		limit = value;
		return this;
	}

	public boolean isHasMore() {
		return hasMore;
	}

	public DocumentSearchResult setHasMore(boolean value) {
		hasMore = value;
		return this;
	}

	public int getPublishedIndexCount() {
		return publishedIndexCount;
	}

	public DocumentSearchResult setPublishedIndexCount(int value) {
		publishedIndexCount = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private List<DocumentHitView> hits = List.of();
		private int offset;
		private int limit;
		private boolean hasMore;
		private int publishedIndexCount;

		private Builder() {
		}

		public Builder withResult(DocumentQueryResult value) {
			hits = value.hits().stream()
				.map(hit -> DocumentHitView.builder().withHit(hit).build())
				.toList();
			offset = value.offset();
			limit = value.limit();
			hasMore = value.hasMore();
			publishedIndexCount = value.publishedIndexCount();
			return this;
		}

		public DocumentSearchResult build() {
			return new DocumentSearchResult()
				.setHits(hits)
				.setOffset(offset)
				.setLimit(limit)
				.setHasMore(hasMore)
				.setPublishedIndexCount(publishedIndexCount);
		}
	}
}
