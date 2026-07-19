package com.inqwise.indexer.node;

import java.time.Duration;
import java.util.Objects;

import com.inqwise.indexer.hot.TargetInvalidationRegistryConfig;
import com.inqwise.indexer.hot.TargetInvalidationRegistryOptions;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetInvalidationNodeOptions {
	public enum Provider {
		IN_MEMORY,
		VERTX_SHARED_DATA
	}

	public static final class Keys {
		public static final String NAMESPACE = "namespace";
		public static final String PROVIDER = "provider";
		public static final String POLL_INTERVAL_MS = "poll_interval_ms";
		public static final String RETENTION_FACTOR = "retention_factor";
		public static final String MAX_TARGETS = "max_targets";

		private Keys() {
		}
	}

	public static final String DEFAULT_NAMESPACE = "local";
	public static final long DEFAULT_POLL_INTERVAL_MS = 30_000L;
	public static final int DEFAULT_RETENTION_FACTOR = 3;
	public static final int DEFAULT_MAX_TARGETS = 10_000;

	private String namespace = DEFAULT_NAMESPACE;
	private Provider provider = Provider.VERTX_SHARED_DATA;
	private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
	private int retentionFactor = DEFAULT_RETENTION_FACTOR;
	private int maxTargets = DEFAULT_MAX_TARGETS;

	public TargetInvalidationNodeOptions() {
	}

	public TargetInvalidationNodeOptions(JsonObject json) {
		this.namespace = json.getString(Keys.NAMESPACE, DEFAULT_NAMESPACE);
		this.provider = Provider.valueOf(json.getString(
			Keys.PROVIDER,
			Provider.VERTX_SHARED_DATA.name()
		));
		this.pollIntervalMs = json.getLong(
			Keys.POLL_INTERVAL_MS,
			DEFAULT_POLL_INTERVAL_MS
		);
		this.retentionFactor = json.getInteger(
			Keys.RETENTION_FACTOR,
			DEFAULT_RETENTION_FACTOR
		);
		this.maxTargets = json.getInteger(Keys.MAX_TARGETS, DEFAULT_MAX_TARGETS);
		validate();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.NAMESPACE, namespace)
			.put(Keys.PROVIDER, provider.name())
			.put(Keys.POLL_INTERVAL_MS, pollIntervalMs)
			.put(Keys.RETENTION_FACTOR, retentionFactor)
			.put(Keys.MAX_TARGETS, maxTargets);
	}

	public TargetInvalidationRegistryConfig registryConfig() {
		return TargetInvalidationRegistryConfig.builder()
			.withNamespace(namespace)
			.withOptions(registryOptions())
			.build();
	}

	public TargetInvalidationRegistryOptions registryOptions() {
		return TargetInvalidationRegistryOptions.builder()
			.withPollInterval(Duration.ofMillis(pollIntervalMs))
			.withRetentionFactor(retentionFactor)
			.withMaxTargets(maxTargets)
			.build();
	}

	public TargetInvalidationNodeOptions validate() {
		Objects.requireNonNull(provider, "provider");
		namespace = registryConfig().namespace();
		return this;
	}

	public String getNamespace() {
		return namespace;
	}

	public TargetInvalidationNodeOptions setNamespace(String namespace) {
		this.namespace = namespace;
		return validate();
	}

	public Provider getProvider() {
		return provider;
	}

	public TargetInvalidationNodeOptions setProvider(Provider provider) {
		this.provider = provider;
		return validate();
	}

	public long getPollIntervalMs() {
		return pollIntervalMs;
	}

	public TargetInvalidationNodeOptions setPollIntervalMs(long pollIntervalMs) {
		this.pollIntervalMs = pollIntervalMs;
		return validate();
	}

	public int getRetentionFactor() {
		return retentionFactor;
	}

	public TargetInvalidationNodeOptions setRetentionFactor(int retentionFactor) {
		this.retentionFactor = retentionFactor;
		return validate();
	}

	public int getMaxTargets() {
		return maxTargets;
	}

	public TargetInvalidationNodeOptions setMaxTargets(int maxTargets) {
		this.maxTargets = maxTargets;
		return validate();
	}

	public static final class Builder {
		private String namespace = DEFAULT_NAMESPACE;
		private Provider provider = Provider.VERTX_SHARED_DATA;
		private long pollIntervalMs = DEFAULT_POLL_INTERVAL_MS;
		private int retentionFactor = DEFAULT_RETENTION_FACTOR;
		private int maxTargets = DEFAULT_MAX_TARGETS;

		private Builder() {
		}

		public Builder withNamespace(String value) {
			namespace = value;
			return this;
		}

		public Builder withProvider(Provider value) {
			provider = value;
			return this;
		}

		public Builder withPollIntervalMs(long value) {
			pollIntervalMs = value;
			return this;
		}

		public Builder withRetentionFactor(int value) {
			retentionFactor = value;
			return this;
		}

		public Builder withMaxTargets(int value) {
			maxTargets = value;
			return this;
		}

		public TargetInvalidationNodeOptions build() {
			TargetInvalidationNodeOptions options = new TargetInvalidationNodeOptions();
			options.namespace = namespace;
			options.provider = provider;
			options.pollIntervalMs = pollIntervalMs;
			options.retentionFactor = retentionFactor;
			options.maxTargets = maxTargets;
			return options.validate();
		}
	}
}
