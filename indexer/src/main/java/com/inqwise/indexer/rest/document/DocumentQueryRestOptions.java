package com.inqwise.indexer.rest.document;

import java.util.Objects;

import com.inqwise.indexer.service.document.DocumentQueryServices;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class DocumentQueryRestOptions {
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8087;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/documents.yaml";

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;
	private String serviceAddress = DocumentQueryServices.DEFAULT_ADDRESS;

	public DocumentQueryRestOptions() {
	}

	public DocumentQueryRestOptions(JsonObject json) {
		host = json.getString("host", DEFAULT_HOST);
		port = json.getInteger("port", DEFAULT_PORT);
		openApiPath = json.getString("open_api_path", DEFAULT_OPEN_API_PATH);
		serviceAddress = json.getString(
			"service_address",
			DocumentQueryServices.DEFAULT_ADDRESS
		);
		validate(host, port, openApiPath, serviceAddress);
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

	public DocumentQueryRestOptions setHost(String value) {
		host = value == null ? DEFAULT_HOST : value;
		return this;
	}

	public int getPort() {
		return port;
	}

	public DocumentQueryRestOptions setPort(int value) {
		port = value;
		return this;
	}

	public String getOpenApiPath() {
		return openApiPath;
	}

	public DocumentQueryRestOptions setOpenApiPath(String value) {
		openApiPath = value == null ? DEFAULT_OPEN_API_PATH : value;
		return this;
	}

	public String getServiceAddress() {
		return serviceAddress;
	}

	public DocumentQueryRestOptions setServiceAddress(String value) {
		serviceAddress = value == null ? DocumentQueryServices.DEFAULT_ADDRESS : value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String openApiPath = DEFAULT_OPEN_API_PATH;
		private String serviceAddress = DocumentQueryServices.DEFAULT_ADDRESS;

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

		public DocumentQueryRestOptions build() {
			validate(host, port, openApiPath, serviceAddress);
			return new DocumentQueryRestOptions()
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
