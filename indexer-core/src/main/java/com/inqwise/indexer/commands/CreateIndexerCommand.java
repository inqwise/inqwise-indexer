package com.inqwise.indexer.commands;

import java.util.Objects;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.json.JsonObject;

public class CreateIndexerCommand implements Command {
	public static final String TYPE = "indexer.create";

	private final String prefix;
	private final Integer targetId;
	private final String targetName;
	private final String indexName;
	private final String queueName;
	private final IndexerType indexerType;
	private final IndexerRole role;
	private final IndexResourceOwnership indexOwnership;
	private final IndexerRuntimeState runtimeState;
	private final PublicationState publicationState;
	private final MutationState mutationState;

	public CreateIndexerCommand(
		String prefix,
		Integer targetId,
		String targetName,
		String indexName,
		String queueName,
		IndexerType indexerType,
		IndexerRole role,
		IndexResourceOwnership indexOwnership,
		IndexerRuntimeState runtimeState,
		PublicationState publicationState,
		MutationState mutationState
	) {
		this.prefix = prefix;
		this.targetId = Objects.requireNonNull(targetId, "targetId");
		this.targetName = Objects.requireNonNull(targetName, "targetName");
		this.indexName = Objects.requireNonNull(indexName, "indexName");
		this.queueName = queueName;
		this.indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
		this.role = role == null ? IndexerRole.LIVE_WRITER : role;
		this.indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
		this.runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
		this.publicationState = publicationState == null ? PublicationState.UNPUBLISHED : publicationState;
		this.mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
	}

	public CreateIndexerCommand(JsonObject json) {
		this(
			json.getString("prefix"),
			json.getInteger("target_id"),
			json.getString("target_name"),
			json.getString("index_name"),
			json.getString("queue_name"),
			IndexerType.valueOf(json.getString("indexer_type", IndexerType.INDEX.name())),
			IndexerRole.valueOf(json.getString("role", IndexerRole.LIVE_WRITER.name())),
			IndexResourceOwnership.valueOf(json.getString(
				"index_ownership",
				IndexResourceOwnership.OWNER.name()
			)),
			IndexerRuntimeState.valueOf(json.getString(
				"runtime_state",
				IndexerRuntimeState.NON_ACTIVE.name()
			)),
			PublicationState.valueOf(json.getString(
				"publication_state",
				PublicationState.UNPUBLISHED.name()
			)),
			MutationState.valueOf(json.getString("mutation_state", MutationState.WRITABLE.name()))
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getPrefix() {
		return prefix;
	}

	public Integer getTargetId() {
		return targetId;
	}

	public String getTargetName() {
		return targetName;
	}

	public String getIndexName() {
		return indexName;
	}

	public String getQueueName() {
		return queueName;
	}

	public IndexerType getIndexerType() {
		return indexerType;
	}

	public IndexerRole getRole() {
		return role;
	}

	public IndexResourceOwnership getIndexOwnership() {
		return indexOwnership;
	}

	public IndexerRuntimeState getRuntimeState() {
		return runtimeState;
	}

	public PublicationState getPublicationState() {
		return publicationState;
	}

	public MutationState getMutationState() {
		return mutationState;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("target_id", targetId)
			.put("target_name", targetName)
			.put("index_name", indexName)
			.put("queue_name", queueName)
			.put("indexer_type", indexerType.name())
			.put("role", role.name())
			.put("index_ownership", indexOwnership.name())
			.put("runtime_state", runtimeState.name())
			.put("publication_state", publicationState.name())
			.put("mutation_state", mutationState.name());

		if (prefix != null) {
			json.put("prefix", prefix);
		}

		return json;
	}
}
