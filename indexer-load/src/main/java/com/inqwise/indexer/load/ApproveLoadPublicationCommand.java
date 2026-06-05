package com.inqwise.indexer.load;

import java.time.Instant;
import java.util.Objects;

import com.inqwise.indexer.commands.Command;

import io.vertx.core.json.JsonObject;

public class ApproveLoadPublicationCommand implements Command {
	public static final String TYPE = "indexer.load.approve-publication";

	private final Integer indexerId;
	private final Instant approvedAt;
	private final String approvedBy;
	private final String approvalReason;
	private final long expectedLoadVersion;

	public ApproveLoadPublicationCommand(
		Integer indexerId,
		Instant approvedAt,
		String approvedBy,
		String approvalReason,
		long expectedLoadVersion
	) {
		this.indexerId = Objects.requireNonNull(indexerId, "indexerId");
		this.approvedAt = approvedAt;
		this.approvedBy = approvedBy;
		this.approvalReason = approvalReason;
		this.expectedLoadVersion = expectedLoadVersion;
	}

	public ApproveLoadPublicationCommand(JsonObject json) {
		this(
			json.getInteger("indexer_id"),
			parseInstant(json.getString("approved_at")),
			json.getString("approved_by"),
			json.getString("approval_reason"),
			json.getLong("expected_load_version")
		);
	}

	@Override
	public String getType() {
		return TYPE;
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public String getApprovalReason() {
		return approvalReason;
	}

	public long getExpectedLoadVersion() {
		return expectedLoadVersion;
	}

	@Override
	public JsonObject toJson() {
		JsonObject json = new JsonObject()
			.put("indexer_id", indexerId)
			.put("expected_load_version", expectedLoadVersion);

		if (approvedAt != null) {
			json.put("approved_at", approvedAt.toString());
		}
		if (approvedBy != null) {
			json.put("approved_by", approvedBy);
		}
		if (approvalReason != null) {
			json.put("approval_reason", approvalReason);
		}

		return json;
	}

	private static Instant parseInstant(String value) {
		return value == null ? null : Instant.parse(value);
	}
}
