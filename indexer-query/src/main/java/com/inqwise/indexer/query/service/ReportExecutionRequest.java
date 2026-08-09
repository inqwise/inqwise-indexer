package com.inqwise.indexer.query.service;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class ReportExecutionRequest {
	private String reportName;
	private JsonObject parameters = new JsonObject();

	public ReportExecutionRequest() {
	}

	public ReportExecutionRequest(JsonObject json) {
		reportName = json.getString("report_name");
		parameters = json.getJsonObject("parameters", new JsonObject()).copy();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("report_name", reportName)
			.put("parameters", parameters.copy());
	}

	public String getReportName() {
		return reportName;
	}

	public ReportExecutionRequest setReportName(String value) {
		reportName = value;
		return this;
	}

	public JsonObject getParameters() {
		return parameters.copy();
	}

	public ReportExecutionRequest setParameters(JsonObject value) {
		parameters = value == null ? new JsonObject() : value.copy();
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String reportName;
		private JsonObject parameters = new JsonObject();

		private Builder() {
		}

		public Builder withReportName(String value) {
			reportName = value;
			return this;
		}

		public Builder withParameters(JsonObject value) {
			parameters = value == null ? null : value.copy();
			return this;
		}

		public ReportExecutionRequest build() {
			if (reportName == null || reportName.isBlank()) {
				throw new IllegalArgumentException("reportName must not be blank");
			}
			return new ReportExecutionRequest()
				.setReportName(reportName)
				.setParameters(Objects.requireNonNull(parameters, "parameters"));
		}
	}
}
