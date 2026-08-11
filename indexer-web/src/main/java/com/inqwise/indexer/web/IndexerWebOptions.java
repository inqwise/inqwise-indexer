package com.inqwise.indexer.web;

import java.util.Objects;

import io.vertx.core.json.JsonObject;

public final class IndexerWebOptions {
	public static final class Keys {
		public static final String HOST = "host";
		public static final String PORT = "port";
		public static final String ADMIN_HOST = "admin_host";
		public static final String ADMIN_PORT = "admin_port";
		public static final String TARGET_ACTION_HOST = "target_action_host";
		public static final String TARGET_ACTION_PORT = "target_action_port";
		public static final String RUNTIME_HOST = "runtime_host";
		public static final String RUNTIME_PORT = "runtime_port";
		public static final String HEALTH_HOST = "health_host";
		public static final String HEALTH_PORT = "health_port";
		public static final String METRICS_HOST = "metrics_host";
		public static final String METRICS_PORT = "metrics_port";
		public static final String REPORTS_HOST = "reports_host";
		public static final String REPORTS_PORT = "reports_port";

		private Keys() {
		}
	}

	public static final String DEFAULT_HOST = "127.0.0.1";
	public static final int DEFAULT_PORT = 3000;
	public static final String DEFAULT_UPSTREAM_HOST = "127.0.0.1";
	public static final int DEFAULT_ADMIN_PORT = 8080;
	public static final int DEFAULT_TARGET_ACTION_PORT = 8081;
	public static final int DEFAULT_RUNTIME_PORT = 8083;
	public static final int DEFAULT_HEALTH_PORT = 8084;
	public static final int DEFAULT_METRICS_PORT = 9090;
	public static final int DEFAULT_REPORTS_PORT = 8086;

	private final String host;
	private final int port;
	private final String adminHost;
	private final int adminPort;
	private final String targetActionHost;
	private final int targetActionPort;
	private final String runtimeHost;
	private final int runtimePort;
	private final String healthHost;
	private final int healthPort;
	private final String metricsHost;
	private final int metricsPort;
	private final String reportsHost;
	private final int reportsPort;

	private IndexerWebOptions(Builder builder) {
		host = builder.host;
		port = builder.port;
		adminHost = builder.adminHost;
		adminPort = builder.adminPort;
		targetActionHost = builder.targetActionHost;
		targetActionPort = builder.targetActionPort;
		runtimeHost = builder.runtimeHost;
		runtimePort = builder.runtimePort;
		healthHost = builder.healthHost;
		healthPort = builder.healthPort;
		metricsHost = builder.metricsHost;
		metricsPort = builder.metricsPort;
		reportsHost = builder.reportsHost;
		reportsPort = builder.reportsPort;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static IndexerWebOptions from(JsonObject json) {
		JsonObject source = json == null ? new JsonObject() : json;
		return builder()
			.withHost(source.getString(Keys.HOST, DEFAULT_HOST))
			.withPort(source.getInteger(Keys.PORT, DEFAULT_PORT))
			.withAdminHost(source.getString(Keys.ADMIN_HOST, DEFAULT_UPSTREAM_HOST))
			.withAdminPort(source.getInteger(Keys.ADMIN_PORT, DEFAULT_ADMIN_PORT))
			.withTargetActionHost(source.getString(
				Keys.TARGET_ACTION_HOST,
				DEFAULT_UPSTREAM_HOST
			))
			.withTargetActionPort(source.getInteger(
				Keys.TARGET_ACTION_PORT,
				DEFAULT_TARGET_ACTION_PORT
			))
			.withRuntimeHost(source.getString(Keys.RUNTIME_HOST, DEFAULT_UPSTREAM_HOST))
			.withRuntimePort(source.getInteger(Keys.RUNTIME_PORT, DEFAULT_RUNTIME_PORT))
			.withHealthHost(source.getString(Keys.HEALTH_HOST, DEFAULT_UPSTREAM_HOST))
			.withHealthPort(source.getInteger(Keys.HEALTH_PORT, DEFAULT_HEALTH_PORT))
			.withMetricsHost(source.getString(Keys.METRICS_HOST, DEFAULT_UPSTREAM_HOST))
			.withMetricsPort(source.getInteger(Keys.METRICS_PORT, DEFAULT_METRICS_PORT))
			.withReportsHost(source.getString(Keys.REPORTS_HOST, DEFAULT_UPSTREAM_HOST))
			.withReportsPort(source.getInteger(Keys.REPORTS_PORT, DEFAULT_REPORTS_PORT))
			.build();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.HOST, host)
			.put(Keys.PORT, port)
			.put(Keys.ADMIN_HOST, adminHost)
			.put(Keys.ADMIN_PORT, adminPort)
			.put(Keys.TARGET_ACTION_HOST, targetActionHost)
			.put(Keys.TARGET_ACTION_PORT, targetActionPort)
			.put(Keys.RUNTIME_HOST, runtimeHost)
			.put(Keys.RUNTIME_PORT, runtimePort)
			.put(Keys.HEALTH_HOST, healthHost)
			.put(Keys.HEALTH_PORT, healthPort)
			.put(Keys.METRICS_HOST, metricsHost)
			.put(Keys.METRICS_PORT, metricsPort)
			.put(Keys.REPORTS_HOST, reportsHost)
			.put(Keys.REPORTS_PORT, reportsPort);
	}

	public String host() {
		return host;
	}

	public int port() {
		return port;
	}

	public String adminHost() {
		return adminHost;
	}

	public int adminPort() {
		return adminPort;
	}

	public String targetActionHost() {
		return targetActionHost;
	}

	public int targetActionPort() {
		return targetActionPort;
	}

	public String runtimeHost() {
		return runtimeHost;
	}

	public int runtimePort() {
		return runtimePort;
	}

	public String healthHost() {
		return healthHost;
	}

	public int healthPort() {
		return healthPort;
	}

	public String metricsHost() {
		return metricsHost;
	}

	public int metricsPort() {
		return metricsPort;
	}

	public String reportsHost() {
		return reportsHost;
	}

	public int reportsPort() {
		return reportsPort;
	}

	public static final class Builder {
		private String host = DEFAULT_HOST;
		private int port = DEFAULT_PORT;
		private String adminHost = DEFAULT_UPSTREAM_HOST;
		private int adminPort = DEFAULT_ADMIN_PORT;
		private String targetActionHost = DEFAULT_UPSTREAM_HOST;
		private int targetActionPort = DEFAULT_TARGET_ACTION_PORT;
		private String runtimeHost = DEFAULT_UPSTREAM_HOST;
		private int runtimePort = DEFAULT_RUNTIME_PORT;
		private String healthHost = DEFAULT_UPSTREAM_HOST;
		private int healthPort = DEFAULT_HEALTH_PORT;
		private String metricsHost = DEFAULT_UPSTREAM_HOST;
		private int metricsPort = DEFAULT_METRICS_PORT;
		private String reportsHost = DEFAULT_UPSTREAM_HOST;
		private int reportsPort = DEFAULT_REPORTS_PORT;

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

		public Builder withAdminHost(String value) {
			adminHost = value;
			return this;
		}

		public Builder withAdminPort(int value) {
			adminPort = value;
			return this;
		}

		public Builder withTargetActionHost(String value) {
			targetActionHost = value;
			return this;
		}

		public Builder withTargetActionPort(int value) {
			targetActionPort = value;
			return this;
		}

		public Builder withRuntimeHost(String value) {
			runtimeHost = value;
			return this;
		}

		public Builder withRuntimePort(int value) {
			runtimePort = value;
			return this;
		}

		public Builder withHealthHost(String value) {
			healthHost = value;
			return this;
		}

		public Builder withHealthPort(int value) {
			healthPort = value;
			return this;
		}

		public Builder withMetricsHost(String value) {
			metricsHost = value;
			return this;
		}

		public Builder withMetricsPort(int value) {
			metricsPort = value;
			return this;
		}

		public Builder withReportsHost(String value) {
			reportsHost = value;
			return this;
		}

		public Builder withReportsPort(int value) {
			reportsPort = value;
			return this;
		}

		public IndexerWebOptions build() {
			requireText(host, "host");
			validatePort(port, "port", true);
			requireText(adminHost, "adminHost");
			validatePort(adminPort, "adminPort", false);
			requireText(targetActionHost, "targetActionHost");
			validatePort(targetActionPort, "targetActionPort", false);
			requireText(runtimeHost, "runtimeHost");
			validatePort(runtimePort, "runtimePort", false);
			requireText(healthHost, "healthHost");
			validatePort(healthPort, "healthPort", false);
			requireText(metricsHost, "metricsHost");
			validatePort(metricsPort, "metricsPort", false);
			requireText(reportsHost, "reportsHost");
			validatePort(reportsPort, "reportsPort", false);
			return new IndexerWebOptions(this);
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}

	private static void validatePort(int value, String name, boolean allowZero) {
		int minimum = allowZero ? 0 : 1;
		if (value < minimum || value > 65535) {
			throw new IllegalArgumentException(
				name + " must be between " + minimum + " and 65535"
			);
		}
	}
}
