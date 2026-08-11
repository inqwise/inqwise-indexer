package com.inqwise.indexer.query.service;

import java.util.List;

import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public final class ReportDiscoveryResult {
	private List<ReportPresentation> reports = List.of();

	public ReportDiscoveryResult() {
	}

	public ReportDiscoveryResult(JsonObject json) {
		reports = json.getJsonArray("reports", new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(ReportPresentation::new)
			.toList();
	}

	public JsonObject toJson() {
		return new JsonObject().put("reports", new JsonArray(reports.stream()
			.map(ReportPresentation::toJson)
			.toList()));
	}

	public List<ReportPresentation> getReports() {
		return copy(reports);
	}

	public ReportDiscoveryResult setReports(List<ReportPresentation> value) {
		reports = value == null ? List.of() : copy(value);
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	private static List<ReportPresentation> copy(List<ReportPresentation> values) {
		return List.copyOf(values).stream()
			.map(ReportPresentation::toJson)
			.map(ReportPresentation::new)
			.toList();
	}

	public static final class Builder {
		private List<ReportPresentation> reports = List.of();

		private Builder() {
		}

		public Builder withReports(List<ReportPresentation> value) {
			reports = value == null ? List.of() : copy(value);
			return this;
		}

		public ReportDiscoveryResult build() {
			return new ReportDiscoveryResult().setReports(reports);
		}
	}
}
