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
		JsonObject request = Objects.requireNonNull(json, "json");
		setReportName(request.getString("report_name"));
		setParameters(request.getJsonObject("parameters", new JsonObject()));
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
		reportName = requireReportName(value);
		return this;
	}

	public JsonObject getParameters() {
		return parameters.copy();
	}

	public ReportExecutionRequest setParameters(JsonObject value) {
		parameters = Objects.requireNonNull(value, "parameters").copy();
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static String requireReportName(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("reportName must not be blank");
		}
		return value;
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
			return new ReportExecutionRequest()
				.setReportName(reportName)
				.setParameters(Objects.requireNonNull(parameters, "parameters"));
		}
	}
}
