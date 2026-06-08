package com.inqwise.indexer.gateway;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class GatewayRestOptions {
	public static final class Keys {
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String ADMIN_REST_BASE_URI = "admin_rest_base_uri";

		private Keys() {
		}
	}

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8082;

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String adminRestBaseUri;

	public GatewayRestOptions() {
	}

	public GatewayRestOptions(JsonObject json) {
		this.host = json.getString(Keys.HOST, DEFAULT_HOST);
		this.port = json.getInteger(Keys.PORT, DEFAULT_PORT);
		this.adminRestBaseUri = json.getString(Keys.ADMIN_REST_BASE_URI);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOST, host)
			.put(Keys.PORT, port)
			.put(Keys.ADMIN_REST_BASE_URI, adminRestBaseUri);
	}

	public String getHost() {
		return host;
	}

	public GatewayRestOptions setHost(String host) {
		this.host = host == null ? DEFAULT_HOST : host;
		return this;
	}

	public int getPort() {
		return port;
	}

	public GatewayRestOptions setPort(int port) {
		this.port = port;
		return this;
	}

	public String getAdminRestBaseUri() {
		return adminRestBaseUri;
	}

	public GatewayRestOptions setAdminRestBaseUri(String adminRestBaseUri) {
		this.adminRestBaseUri = adminRestBaseUri;
		return this;
	}
}
