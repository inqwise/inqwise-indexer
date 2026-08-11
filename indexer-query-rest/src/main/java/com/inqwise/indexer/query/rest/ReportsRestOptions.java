package com.inqwise.indexer.query.rest;

import java.util.Objects;

import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.json.JsonObject;

public record ReportsRestOptions(
	boolean enabled,
	String host,
	int port,
	String openApiPath,
	String reportsAddress,
	String discoveryAddress
) {
	public static final String CONFIG_KEY = "reports_rest";
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8086;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/reports.yaml";

	public ReportsRestOptions {
		requireText(host, "host");
		requireText(openApiPath, "openApiPath");
		requireText(reportsAddress, "reportsAddress");
		requireText(discoveryAddress, "discoveryAddress");
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("port must be between 0 and 65535");
		}
	}

	public static ReportsRestOptions from(JsonObject root) {
		JsonObject config = root == null
			? new JsonObject()
			: root.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withEnabled(config.getBoolean("enabled", false))
			.withHost(config.getString("host", DEFAULT_HOST))
			.withPort(config.getInteger("port", DEFAULT_PORT))
			.withOpenApiPath(config.getString("open_api_path", DEFAULT_OPEN_API_PATH))
			.withReportsAddress(config.getString(
				"reports_address",
				ReportsServices.DEFAULT_ADDRESS
			))
			.withDiscoveryAddress(config.getString(
				"discovery_address",
				ReportDiscoveryServices.DEFAULT_ADDRESS
			))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private boolean enabled;
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String openApiPath = DEFAULT_OPEN_API_PATH;
		private String reportsAddress = ReportsServices.DEFAULT_ADDRESS;
		private String discoveryAddress = ReportDiscoveryServices.DEFAULT_ADDRESS;

		private Builder() {
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public Builder withHost(String value) {
			host = value;
			return this;
		}

		public Builder withPort(int value) {
			port = value;
			return this;
		}

		public Builder withOpenApiPath(String value) {
			openApiPath = value;
			return this;
		}

		public Builder withReportsAddress(String value) {
			reportsAddress = value;
			return this;
		}

		public Builder withDiscoveryAddress(String value) {
			discoveryAddress = value;
			return this;
		}

		public ReportsRestOptions build() {
			return new ReportsRestOptions(
				enabled,
				Objects.requireNonNull(host, "host"),
				port,
				Objects.requireNonNull(openApiPath, "openApiPath"),
				Objects.requireNonNull(reportsAddress, "reportsAddress"),
				Objects.requireNonNull(discoveryAddress, "discoveryAddress")
			);
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
