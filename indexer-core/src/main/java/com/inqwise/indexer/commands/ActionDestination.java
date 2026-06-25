package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.CatchUpBarrierActionItem;
import com.inqwise.indexer.CompleteIndexActionItem;
import com.inqwise.indexer.IndexerActionItem;
import com.inqwise.indexer.PutDocumentActionItem;
import com.inqwise.indexer.RemoveDocumentActionItem;

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
				yield new ActionDestination(put.getTargetId(), put.getIndexerId(), put.getIndexName());
			}
			case REMOVE_DOCUMENT -> {
				RemoveDocumentActionItem remove = (RemoveDocumentActionItem) action;
				yield new ActionDestination(remove.getTargetId(), remove.getIndexerId(), remove.getIndexName());
			}
			case COMPLETE -> {
				CompleteIndexActionItem complete = (CompleteIndexActionItem) action;
				yield new ActionDestination(complete.getTargetId(), complete.getIndexerId(), null);
			}
			case CATCH_UP_BARRIER -> {
				CatchUpBarrierActionItem barrier = (CatchUpBarrierActionItem) action;
				yield new ActionDestination(barrier.getTargetId(), barrier.getIndexerId(), null);
			}
		};
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
}
