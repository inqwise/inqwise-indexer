package com.inqwise.indexer.node.application;

import java.util.Objects;

import com.inqwise.indexer.query.service.ReportDiscoveryServices;
import com.inqwise.indexer.query.service.ReportsServices;

import io.vertx.core.json.JsonObject;

public record HackerNewsReportsDeploymentOptions(
	boolean enabled,
	int instances,
	String address,
	String discoveryAddress
) {
	public static final String CONFIG_KEY = "hacker_news_reports";

	public HackerNewsReportsDeploymentOptions {
		if (instances < 1) {
			throw new IllegalArgumentException("instances must be positive");
		}
		if (address == null || address.isBlank()) {
			throw new IllegalArgumentException("address must not be blank");
		}
		if (discoveryAddress == null || discoveryAddress.isBlank()) {
			throw new IllegalArgumentException("discoveryAddress must not be blank");
		}
	}

	public static HackerNewsReportsDeploymentOptions from(JsonObject root) {
		JsonObject config = root == null
			? new JsonObject()
			: root.getJsonObject(CONFIG_KEY, new JsonObject());
		return builder()
			.withEnabled(config.getBoolean("enabled", false))
			.withInstances(config.getInteger("instances", 1))
			.withAddress(config.getString("address", ReportsServices.DEFAULT_ADDRESS))
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
		private int instances = 1;
		private String address = ReportsServices.DEFAULT_ADDRESS;
		private String discoveryAddress = ReportDiscoveryServices.DEFAULT_ADDRESS;

		private Builder() {
		}

		public Builder withEnabled(boolean value) {
			enabled = value;
			return this;
		}

		public Builder withInstances(int value) {
			instances = value;
			return this;
		}

		public Builder withAddress(String value) {
			address = value;
			return this;
		}

		public Builder withDiscoveryAddress(String value) {
			discoveryAddress = value;
			return this;
		}

		public HackerNewsReportsDeploymentOptions build() {
			return new HackerNewsReportsDeploymentOptions(
				enabled,
				instances,
				Objects.requireNonNull(address, "address"),
				Objects.requireNonNull(discoveryAddress, "discoveryAddress")
			);
		}
	}
}
