package com.inqwise.indexer.rest.target;

import com.inqwise.indexer.service.target.TargetCatalogServices;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetCatalogRestOptions {
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8085;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/targets.yaml";

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;
	private String serviceAddress = TargetCatalogServices.DEFAULT_ADDRESS;

	public TargetCatalogRestOptions() {
	}

	public TargetCatalogRestOptions(JsonObject json) {
		host = json.getString("host", DEFAULT_HOST);
		port = json.getInteger("port", DEFAULT_PORT);
		openApiPath = json.getString("open_api_path", DEFAULT_OPEN_API_PATH);
		serviceAddress = json.getString(
			"service_address",
			TargetCatalogServices.DEFAULT_ADDRESS
		);
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

	public TargetCatalogRestOptions setHost(String value) {
		host = value == null ? DEFAULT_HOST : value;
		return this;
	}

	public int getPort() {
		return port;
	}

	public TargetCatalogRestOptions setPort(int value) {
		port = value;
		return this;
	}

	public String getOpenApiPath() {
		return openApiPath;
	}

	public TargetCatalogRestOptions setOpenApiPath(String value) {
		openApiPath = value == null ? DEFAULT_OPEN_API_PATH : value;
		return this;
	}

	public String getServiceAddress() {
		return serviceAddress;
	}

	public TargetCatalogRestOptions setServiceAddress(String value) {
		serviceAddress = value == null ? TargetCatalogServices.DEFAULT_ADDRESS : value;
		return this;
	}
}
