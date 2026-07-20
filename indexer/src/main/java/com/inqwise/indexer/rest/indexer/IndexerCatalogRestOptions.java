package com.inqwise.indexer.rest.indexer;

import java.util.Objects;

import com.inqwise.indexer.service.indexer.IndexerCatalogServices;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexerCatalogRestOptions {
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8086;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/indexers.yaml";

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;
	private String serviceAddress = IndexerCatalogServices.DEFAULT_ADDRESS;

	public IndexerCatalogRestOptions() {
	}

	public IndexerCatalogRestOptions(JsonObject json) {
		host = json.getString("host", DEFAULT_HOST);
		port = json.getInteger("port", DEFAULT_PORT);
		openApiPath = json.getString("open_api_path", DEFAULT_OPEN_API_PATH);
		serviceAddress = json.getString(
			"service_address",
			IndexerCatalogServices.DEFAULT_ADDRESS
		);
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put("host", host)
			.put("port", port)
			.put("open_api_path", openApiPath)
			.put("service_address", serviceAddress);
	}

	public String getHost() {
		return host;
	}

	public IndexerCatalogRestOptions setHost(String value) {
		host = value == null ? DEFAULT_HOST : value;
		return this;
	}

	public int getPort() {
		return port;
	}

	public IndexerCatalogRestOptions setPort(int value) {
		port = value;
		return this;
	}

	public String getOpenApiPath() {
		return openApiPath;
	}

	public IndexerCatalogRestOptions setOpenApiPath(String value) {
		openApiPath = value == null ? DEFAULT_OPEN_API_PATH : value;
		return this;
	}

	public String getServiceAddress() {
		return serviceAddress;
	}

	public IndexerCatalogRestOptions setServiceAddress(String value) {
		serviceAddress = value == null ? IndexerCatalogServices.DEFAULT_ADDRESS : value;
		return this;
	}

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String openApiPath = DEFAULT_OPEN_API_PATH;
		private String serviceAddress = IndexerCatalogServices.DEFAULT_ADDRESS;

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

		public Builder withServiceAddress(String value) {
			serviceAddress = value;
			return this;
		}

		public IndexerCatalogRestOptions build() {
			validate(host, port, openApiPath, serviceAddress);
			return new IndexerCatalogRestOptions()
				.setHost(host)
				.setPort(port)
				.setOpenApiPath(openApiPath)
				.setServiceAddress(serviceAddress);
		}
	}

	private static void validate(
		String host,
		int port,
		String openApiPath,
		String serviceAddress
	) {
		requireText(host, "host");
		requireText(openApiPath, "openApiPath");
		requireText(serviceAddress, "serviceAddress");
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
