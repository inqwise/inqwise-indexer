package com.inqwise.indexer.gateway;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class GatewayRestOptions {
	public static final class Keys {
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String OPEN_API_PATH = "open_api_path";
		public static final String ADMIN_REST_BASE_URI = "admin_rest_base_uri";
		public static final String REQUEST_TIMEOUT_MS = "request_timeout_ms";
		public static final String API_KEY = "api_key";
		public static final String API_KEY_HEADER = "api_key_header";
		public static final String RATE_LIMIT_REQUESTS = "rate_limit_requests";
		public static final String RATE_LIMIT_WINDOW_MS = "rate_limit_window_ms";

		private Keys() {
		}
	}

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8082;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/gateway.yaml";
	public static final long DEFAULT_REQUEST_TIMEOUT_MS = 5000L;
	public static final String DEFAULT_API_KEY_HEADER = "x-api-key";
	public static final long DEFAULT_RATE_LIMIT_WINDOW_MS = 60000L;

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;
	private String adminRestBaseUri;
	private long requestTimeoutMs = DEFAULT_REQUEST_TIMEOUT_MS;
	private String apiKey;
	private String apiKeyHeader = DEFAULT_API_KEY_HEADER;
	private int rateLimitRequests;
	private long rateLimitWindowMs = DEFAULT_RATE_LIMIT_WINDOW_MS;

	public GatewayRestOptions() {
	}

	public GatewayRestOptions(JsonObject json) {
		this.host = json.getString(Keys.HOST, DEFAULT_HOST);
		this.port = json.getInteger(Keys.PORT, DEFAULT_PORT);
		this.openApiPath = json.getString(Keys.OPEN_API_PATH, DEFAULT_OPEN_API_PATH);
		this.adminRestBaseUri = json.getString(Keys.ADMIN_REST_BASE_URI);
		this.requestTimeoutMs = json.getLong(Keys.REQUEST_TIMEOUT_MS, DEFAULT_REQUEST_TIMEOUT_MS);
		this.apiKey = json.getString(Keys.API_KEY);
		this.apiKeyHeader = json.getString(Keys.API_KEY_HEADER, DEFAULT_API_KEY_HEADER);
		this.rateLimitRequests = json.getInteger(Keys.RATE_LIMIT_REQUESTS, 0);
		this.rateLimitWindowMs = json.getLong(Keys.RATE_LIMIT_WINDOW_MS, DEFAULT_RATE_LIMIT_WINDOW_MS);
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOST, host)
			.put(Keys.PORT, port)
			.put(Keys.OPEN_API_PATH, openApiPath)
			.put(Keys.ADMIN_REST_BASE_URI, adminRestBaseUri)
			.put(Keys.REQUEST_TIMEOUT_MS, requestTimeoutMs)
			.put(Keys.API_KEY_HEADER, apiKeyHeader)
			.put(Keys.RATE_LIMIT_REQUESTS, rateLimitRequests)
			.put(Keys.RATE_LIMIT_WINDOW_MS, rateLimitWindowMs);
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

	public String getOpenApiPath() {
		return openApiPath;
	}

	public GatewayRestOptions setOpenApiPath(String openApiPath) {
		this.openApiPath = openApiPath == null ? DEFAULT_OPEN_API_PATH : openApiPath;
		return this;
	}

	public String getAdminRestBaseUri() {
		return adminRestBaseUri;
	}

	public GatewayRestOptions setAdminRestBaseUri(String adminRestBaseUri) {
		this.adminRestBaseUri = adminRestBaseUri;
		return this;
	}

	public long getRequestTimeoutMs() {
		return requestTimeoutMs;
	}

	public GatewayRestOptions setRequestTimeoutMs(long requestTimeoutMs) {
		this.requestTimeoutMs = requestTimeoutMs;
		return this;
	}

	public String getApiKey() {
		return apiKey;
	}

	public GatewayRestOptions setApiKey(String apiKey) {
		this.apiKey = apiKey;
		return this;
	}

	public String getApiKeyHeader() {
		return apiKeyHeader;
	}

	public GatewayRestOptions setApiKeyHeader(String apiKeyHeader) {
		this.apiKeyHeader = apiKeyHeader == null ? DEFAULT_API_KEY_HEADER : apiKeyHeader;
		return this;
	}

	public int getRateLimitRequests() {
		return rateLimitRequests;
	}

	public GatewayRestOptions setRateLimitRequests(int rateLimitRequests) {
		this.rateLimitRequests = rateLimitRequests;
		return this;
	}

	public long getRateLimitWindowMs() {
		return rateLimitWindowMs;
	}

	public GatewayRestOptions setRateLimitWindowMs(long rateLimitWindowMs) {
		this.rateLimitWindowMs = rateLimitWindowMs;
		return this;
	}
}
