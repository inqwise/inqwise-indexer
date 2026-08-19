package com.inqwise.indexer.service.admin;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.hot.HotRoutingTargetSnapshot;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminHotTargetView {
	public static final class Keys {
		public static final String TARGET_ID = "target_id";
		public static final String TARGET_NAME = "target_name";
		public static final String HOT_INDEXER_IDS = "hot_indexer_ids";
		public static final String INDEXERS_TRUNCATED = "indexers_truncated";

		private Keys() {
		}
	}

	private Integer targetId;
	private String targetName;
	private List<Integer> hotIndexerIds = List.of();
	private boolean indexersTruncated;

	public AdminHotTargetView() {
	}

	public AdminHotTargetView(JsonObject json) {
		targetId = json.getInteger(Keys.TARGET_ID);
		targetName = json.getString(Keys.TARGET_NAME);
		hotIndexerIds = json.getJsonArray(Keys.HOT_INDEXER_IDS, new JsonArray()).stream()
			.map(Number.class::cast)
			.map(Number::intValue)
			.toList();
		indexersTruncated = json.getBoolean(Keys.INDEXERS_TRUNCATED, false);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static AdminHotTargetView from(HotRoutingTargetSnapshot snapshot) {
		Objects.requireNonNull(snapshot, "snapshot");
		return builder()
			.withTargetId(snapshot.targetId())
			.withTargetName(snapshot.targetName())
			.withHotIndexerIds(snapshot.hotIndexerIds())
			.withIndexersTruncated(snapshot.indexersTruncated())
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.TARGET_ID, targetId)
			.put(Keys.TARGET_NAME, targetName)
			.put(Keys.HOT_INDEXER_IDS, new JsonArray(hotIndexerIds))
			.put(Keys.INDEXERS_TRUNCATED, indexersTruncated);
	}

	public static final class Builder {
		private Integer targetId;
		private String targetName;
		private List<Integer> hotIndexerIds;
		private boolean indexersTruncated;

		private Builder() {
		}

		public Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		public Builder withTargetName(String value) {
			targetName = value;
			return this;
		}

		public Builder withHotIndexerIds(List<Integer> value) {
			hotIndexerIds = value == null ? null : List.copyOf(value);
			return this;
		}

		public Builder withIndexersTruncated(boolean value) {
			indexersTruncated = value;
			return this;
		}

		public AdminHotTargetView build() {
			AdminHotTargetView view = new AdminHotTargetView();
			view.targetId = Objects.requireNonNull(targetId, "targetId");
			view.targetName = Objects.requireNonNull(targetName, "targetName");
			view.hotIndexerIds = List.copyOf(Objects.requireNonNull(
				hotIndexerIds,
				"hotIndexerIds"
			));
			view.indexersTruncated = indexersTruncated;
			return view;
		}
	}
}
