package com.inqwise.indexer.commands;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.inqwise.indexer.IndexResourceOwnership;
import com.inqwise.indexer.IndexerRuntimeState;
import com.inqwise.indexer.IndexerRole;
import com.inqwise.indexer.IndexerType;
import com.inqwise.indexer.metadata.MutationState;
import com.inqwise.indexer.metadata.PublicationState;

import io.vertx.core.json.JsonObject;

public class CreateTargetCommand implements Command {
	public static final String TYPE = "target.create";

	private final String commandId;
	private final String prefix;
	private final String targetName;
	private final Instant timestamp;
	private final CreateIndexer createIndexer;

	public CreateTargetCommand(
		String prefix,
		String targetName,
		Instant timestamp,
		CreateIndexer createIndexer
	) {
		this(UUID.randomUUID().toString(), prefix, targetName, timestamp, createIndexer);
	}

	public CreateTargetCommand(
		String commandId,
		String prefix,
		String targetName,
		Instant timestamp,
		CreateIndexer createIndexer
	) {
		this.commandId = Objects.requireNonNull(commandId, "commandId");
		this.prefix = prefix;
		this.targetName = Objects.requireNonNull(targetName, "targetName");
		this.timestamp = timestamp;
		this.createIndexer = createIndexer;
	}

	public CreateTargetCommand(JsonObject json) {
		this(
			json.getString("command_id"),
			json.getString("prefix"),
			json.getString("target_name"),
			json.getString("timestamp") == null ? null : Instant.parse(json.getString("timestamp")),
			json.getJsonObject("create_indexer") == null
				? null
				: new CreateIndexer(json.getJsonObject("create_indexer"))
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public String getCommandId() {
		return commandId;
	}

	public String getPrefix() {
		return prefix;
	}

	public String getTargetName() {
		return targetName;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public CreateIndexer getCreateIndexer() {
		return createIndexer;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("command_id", commandId)
			.put("prefix", prefix)
			.put("target_name", targetName)
			.put("timestamp", timestamp == null ? null : timestamp.toString());

		if (createIndexer != null) {
			json.put("create_indexer", createIndexer.toJson());
		}

		return json;
	}

	public static class CreateIndexer {
		private final String prefix;
		private final String indexName;
		private final String queueName;
		private final IndexerType indexerType;
		private final IndexerRole role;
		private final IndexResourceOwnership indexOwnership;
		private final IndexerRuntimeState runtimeState;
		private final PublicationState publicationState;
		private final MutationState mutationState;
		private final InitialPublicationMode initialPublicationMode;

		public CreateIndexer(
			String prefix,
			String indexName,
			String queueName,
			IndexerType indexerType,
			IndexerRole role,
			IndexResourceOwnership indexOwnership,
			IndexerRuntimeState runtimeState,
			PublicationState publicationState,
			MutationState mutationState,
			InitialPublicationMode initialPublicationMode
		) {
			this.prefix = prefix;
			this.indexName = Objects.requireNonNull(indexName, "indexName");
			this.queueName = queueName;
			this.indexerType = indexerType == null ? IndexerType.INDEX : indexerType;
			this.role = role == null ? IndexerRole.LIVE_WRITER : role;
			this.indexOwnership = indexOwnership == null ? IndexResourceOwnership.OWNER : indexOwnership;
			this.runtimeState = runtimeState == null ? IndexerRuntimeState.NON_ACTIVE : runtimeState;
			this.publicationState = publicationState == null ? PublicationState.UNPUBLISHED : publicationState;
			this.mutationState = mutationState == null ? MutationState.WRITABLE : mutationState;
			this.initialPublicationMode = Objects.requireNonNull(
				initialPublicationMode,
				"initialPublicationMode"
			);
		}

		public CreateIndexer(JsonObject json) {
			this(
				json.getString("prefix"),
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
				MutationState.valueOf(json.getString("mutation_state", MutationState.WRITABLE.name())),
				InitialPublicationMode.valueOf(json.getString("initial_publication_mode"))
			);
		}

		public String getPrefix() {
			return prefix;
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

		public InitialPublicationMode getInitialPublicationMode() {
			return initialPublicationMode;
		}

		public JsonObject toJson() {
			return new JsonObject()
				.put("prefix", prefix)
				.put("index_name", indexName)
				.put("queue_name", queueName)
				.put("indexer_type", indexerType.name())
				.put("role", role.name())
				.put("index_ownership", indexOwnership.name())
				.put("runtime_state", runtimeState.name())
				.put("publication_state", publicationState.name())
				.put("mutation_state", mutationState.name())
				.put("initial_publication_mode", initialPublicationMode.name());
		}
	}
}
