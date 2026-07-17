package com.inqwise.indexer.load.rest;

import com.inqwise.indexer.load.service.LoadServices;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class LoadRestOptions {
	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 8084;
	public static final String DEFAULT_OPEN_API_PATH = "openapi/load.yaml";

	private String host = DEFAULT_HOST;
	private int port = DEFAULT_PORT;
	private String openApiPath = DEFAULT_OPEN_API_PATH;
	private String serviceAddress = LoadServices.DEFAULT_ADDRESS;

	public LoadRestOptions() {
	}

	public LoadRestOptions(JsonObject json) {
		host = json.getString("host", DEFAULT_HOST);
		port = json.getInteger("port", DEFAULT_PORT);
		openApiPath = json.getString("open_api_path", DEFAULT_OPEN_API_PATH);
		serviceAddress = json.getString("service_address", LoadServices.DEFAULT_ADDRESS);
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

	public LoadRestOptions setHost(String value) {
		host = value == null ? DEFAULT_HOST : value;
		return this;
	}

	public int getPort() {
		return port;
	}

	public LoadRestOptions setPort(int value) {
		port = value;
		return this;
	}

	public String getOpenApiPath() {
		return openApiPath;
	}

	public LoadRestOptions setOpenApiPath(String value) {
		openApiPath = value == null ? DEFAULT_OPEN_API_PATH : value;
		return this;
	}

	public String getServiceAddress() {
		return serviceAddress;
	}

	public LoadRestOptions setServiceAddress(String value) {
		serviceAddress = value == null ? LoadServices.DEFAULT_ADDRESS : value;
		return this;
	}
}
