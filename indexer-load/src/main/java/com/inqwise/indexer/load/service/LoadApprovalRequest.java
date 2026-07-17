package com.inqwise.indexer.load.service;

import java.time.Instant;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadApprovalRequest {
	private Integer indexerId;
	private Instant approvedAt;
	private String approvedBy;
	private String approvalReason;
	private Long expectedVersion;

	public LoadApprovalRequest() {
	}

	public LoadApprovalRequest(JsonObject json) {
		indexerId = json.getInteger("indexer_id");
		String approvedAtValue = json.getString("approved_at");
		approvedAt = approvedAtValue == null ? null : Instant.parse(approvedAtValue);
		approvedBy = json.getString("approved_by");
		approvalReason = json.getString("approval_reason");
		expectedVersion = json.getLong("expected_version");
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("indexer_id", indexerId)
			.put("approved_at", approvedAt == null ? null : approvedAt.toString())
			.put("approved_by", approvedBy)
			.put("approval_reason", approvalReason)
			.put("expected_version", expectedVersion);
	}

	public Integer getIndexerId() {
		return indexerId;
	}

	public LoadApprovalRequest setIndexerId(Integer value) {
		indexerId = value;
		return this;
	}

	public Instant getApprovedAt() {
		return approvedAt;
	}

	public LoadApprovalRequest setApprovedAt(Instant value) {
		approvedAt = value;
		return this;
	}

	public String getApprovedBy() {
		return approvedBy;
	}

	public LoadApprovalRequest setApprovedBy(String value) {
		approvedBy = value;
		return this;
	}

	public String getApprovalReason() {
		return approvalReason;
	}

	public LoadApprovalRequest setApprovalReason(String value) {
		approvalReason = value;
		return this;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public LoadApprovalRequest setExpectedVersion(Long value) {
		expectedVersion = value;
		return this;
	}
}
