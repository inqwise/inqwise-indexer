package com.inqwise.indexer.rest.runtime;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RuntimeRestOptions {
	public static final class Keys {
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String OPEN_API_PATH = "open_api_path";

		private Keys() {
		}
	}

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8083;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/runtime.yaml";

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;

	public RuntimeRestOptions() {
	}

	public RuntimeRestOptions(JsonObject json) {
		this.host = json.getString(Keys.HOST, DEFAULT_HOST);
		this.port = json.getInteger(Keys.PORT, DEFAULT_PORT);
		this.openApiPath = json.getString(Keys.OPEN_API_PATH, DEFAULT_OPEN_API_PATH);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOST, host)
			.put(Keys.PORT, port)
			.put(Keys.OPEN_API_PATH, openApiPath);
	}

	public String getHost() {
		return host;
	}

	public RuntimeRestOptions setHost(String host) {
		this.host = host == null ? DEFAULT_HOST : host;
		return this;
	}

	public int getPort() {
		return port;
	}

	public RuntimeRestOptions setPort(int port) {
		this.port = port;
		return this;
	}

	public String getOpenApiPath() {
		return openApiPath;
	}

	public RuntimeRestOptions setOpenApiPath(String openApiPath) {
		this.openApiPath = openApiPath == null ? DEFAULT_OPEN_API_PATH : openApiPath;
		return this;
	}

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String openApiPath = DEFAULT_OPEN_API_PATH;

		private Builder() {
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

		public RuntimeRestOptions build() {
			validate(host, port, openApiPath);
			return new RuntimeRestOptions()
				.setHost(host)
				.setPort(port)
				.setOpenApiPath(openApiPath);
		}
	}

	private static void validate(String host, int port, String openApiPath) {
		requireText(host, "host");
		requireText(openApiPath, "openApiPath");
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("port must be between 0 and 65535");
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
