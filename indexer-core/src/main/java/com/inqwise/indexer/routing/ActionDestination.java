package com.inqwise.indexer.routing;

import java.util.Objects;

import com.inqwise.indexer.actions.CatchUpBarrierActionItem;
import com.inqwise.indexer.actions.CompleteIndexActionItem;
import com.inqwise.indexer.actions.IndexerActionItem;
import com.inqwise.indexer.actions.PutDocumentActionItem;
import com.inqwise.indexer.actions.RemoveDocumentActionItem;

public final class ActionDestination {
	private final Integer targetId;
	private final Integer indexerId;
	private final String indexName;

	private ActionDestination(Integer targetId, Integer indexerId, String indexName) {
		this.targetId = targetId;
		this.indexerId = indexerId;
		this.indexName = indexName;
	}

	public static ActionDestination from(IndexerActionItem action) {
		return switch (action.getActionType()) {
			case PUT_DOCUMENT -> {
				PutDocumentActionItem put = (PutDocumentActionItem) action;
				yield builder()
					.withTargetId(put.getTargetId())
					.withIndexerId(put.getIndexerId())
					.withIndexName(put.getIndexName())
					.build();
			}
			case REMOVE_DOCUMENT -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) action;
				yield builder()
					.withTargetId(remove.getTargetId())
					.withIndexerId(remove.getIndexerId())
					.withIndexName(remove.getIndexName())
					.build();
			}
			case COMPLETE -> {
				CompleteIndexActionItem complete = (CompleteIndexActionItem) action;
				yield builder()
					.withTargetId(complete.getTargetId())
					.withIndexerId(complete.getIndexerId())
					.build();
			}
			case CATCH_UP_BARRIER -> {
				CatchUpBarrierActionItem barrier = (CatchUpBarrierActionItem) action;
				yield builder()
					.withTargetId(barrier.getTargetId())
					.withIndexerId(barrier.getIndexerId())
					.build();
			}
		};
	}

	private static Builder builder() {
		return new Builder();
	}

	public Integer targetId() {
		return targetId;
	}

	public Integer indexerId() {
		return indexerId;
	}

	public String indexName() {
		return indexName;
	}

	public boolean isEmpty() {
		return targetId == null && indexerId == null && indexName == null;
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}

		if (!(other instanceof ActionDestination that)) {
			return false;
		}

		return Objects.equals(targetId, that.targetId)
			&& Objects.equals(indexerId, that.indexerId)
			&& Objects.equals(indexName, that.indexName);
	}

	@Override
	public int hashCode() {
		return Objects.hash(targetId, indexerId, indexName);
	}

	private static final class Builder {
		private Integer targetId;
		private Integer indexerId;
		private String indexName;

		private Builder withTargetId(Integer value) {
			targetId = value;
			return this;
		}

		private Builder withIndexerId(Integer value) {
			indexerId = value;
			return this;
		}

		private Builder withIndexName(String value) {
			indexName = value;
			return this;
		}

		private ActionDestination build() {
			return new ActionDestination(targetId, indexerId, indexName);
		}
	}
}
