package com.inqwise.indexer.node;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class NodeHealthRestOptions {
	public static final class Keys {
		public static final String HOST = "host";
		public static final String PORT = "port";

		private Keys() {
		}
	}

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8084;

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;

	public NodeHealthRestOptions() {
	}

	public NodeHealthRestOptions(JsonObject json) {
		this.host = json.getString(Keys.HOST, DEFAULT_HOST);
		this.port = json.getInteger(Keys.PORT, DEFAULT_PORT);
		validate(host, port);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOST, host)
			.put(Keys.PORT, port);
	}

	public String getHost() {
		return host;
	}

	public NodeHealthRestOptions setHost(String host) {
		this.host = host == null ? DEFAULT_HOST : host;
		return this;
	}

	public int getPort() {
		return port;
	}

	public NodeHealthRestOptions setPort(int port) {
		this.port = port;
		return this;
	}

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;

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

		public NodeHealthRestOptions build() {
			validate(host, port);
			return new NodeHealthRestOptions()
				.setHost(host)
				.setPort(port);
		}
	}

	private static void validate(String host, int port) {
		Objects.requireNonNull(host, "host");
		if (host.isBlank()) {
			throw new IllegalArgumentException("host must not be blank");
		}
		if (port < 0 || port > 65535) {
			throw new IllegalArgumentException("port must be between 0 and 65535");
		}
	}
}
