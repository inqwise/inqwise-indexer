package com.inqwise.indexer.load.rest;

import com.inqwise.indexer.load.service.LoadQueryServices;

import io.vertx.core.json.JsonObject;

public final class LoadQueryRestOptions {
	public static final String CONFIG_KEY = "load_query_rest";
	public static final int DEFAULT_PORT = 8087;
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final String DEFAULT_OPEN_API_PATH = "openapi/load-query.yaml";

	private final String host;
	private final int port;
	private final String serviceAddress;
	private final String openApiPath;

	private LoadQueryRestOptions(Builder builder) {
		host = builder.host;
		port = builder.port;
		serviceAddress = builder.serviceAddress;
		openApiPath = builder.openApiPath;
	}

	public static LoadQueryRestOptions from(JsonObject config) {
		JsonObject source = config == null
			? new JsonObject()
			: config.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withHost(source.getString("host", DEFAULT_HOST))
			.withPort(source.getInteger("port", DEFAULT_PORT))
			.withServiceAddress(source.getString("service_address", LoadQueryServices.DEFAULT_ADDRESS))
			.withOpenApiPath(source.getString("open_api_path", DEFAULT_OPEN_API_PATH))
			.build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public String host() { return host; }
	public int port() { return port; }
	public String serviceAddress() { return serviceAddress; }
	public String openApiPath() { return openApiPath; }

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String serviceAddress = LoadQueryServices.DEFAULT_ADDRESS;
		private String openApiPath = DEFAULT_OPEN_API_PATH;

		private Builder() {
		}

		public Builder withHost(String value) { host = value; return this; }
		public Builder withPort(int value) { port = value; return this; }
		public Builder withServiceAddress(String value) { serviceAddress = value; return this; }
		public Builder withOpenApiPath(String value) { openApiPath = value; return this; }

		public LoadQueryRestOptions build() {
			if (host == null || host.isBlank()) throw new IllegalArgumentException("host is required");
			if (port < 0 || port > 65_535) throw new IllegalArgumentException("port is invalid");
			LoadQueryServices.requireAddress(serviceAddress);
			if (openApiPath == null || openApiPath.isBlank()) {
				throw new IllegalArgumentException("openApiPath is required");
			}
			return new LoadQueryRestOptions(this);
		}
	}
}
