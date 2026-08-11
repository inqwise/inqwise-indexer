package com.inqwise.indexer.service.admin;

import java.util.List;
import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@DataObject
public class AdminNodeStatusResult {
	public static final class Keys {
		public static final String STARTED = "started";
		public static final String READY = "ready";
		public static final String RECOVERY_ONLY = "recovery_only";
		public static final String STOPPING = "stopping";
		public static final String CLUSTERED = "clustered";
		public static final String DEPLOYMENT_COUNT = "deployment_count";
		public static final String CONTROL_PLANE_DEPLOYMENTS = "control_plane_deployments";
		public static final String DATA_PLANE_DEPLOYMENTS = "data_plane_deployments";
		public static final String INFRASTRUCTURE_DEPLOYMENTS = "infrastructure_deployments";
		public static final String LIFECYCLE_EVENT_NAMESPACE = "lifecycle_event_namespace";
		public static final String TARGET_INVALIDATION_PROVIDER = "target_invalidation_provider";
		public static final String TARGET_INVALIDATION_NAMESPACE = "target_invalidation_namespace";
		public static final String TARGET_INVALIDATION_MAX_TARGETS = "target_invalidation_max_targets";
		public static final String SERVICES = "services";

		private Keys() {
		}
	}

	private boolean started;
	private boolean ready;
	private boolean recoveryOnly;
	private boolean stopping;
	private boolean clustered;
	private int deploymentCount;
	private int controlPlaneDeployments;
	private int dataPlaneDeployments;
	private int infrastructureDeployments;
	private String lifecycleEventNamespace;
	private String targetInvalidationProvider;
	private String targetInvalidationNamespace;
	private int targetInvalidationMaxTargets;
	private List<AdminNodeServiceView> services = List.of();

	public AdminNodeStatusResult() {
	}

	public AdminNodeStatusResult(JsonObject json) {
		this.started = json.getBoolean(Keys.STARTED, false);
		this.ready = json.getBoolean(Keys.READY, false);
		this.recoveryOnly = json.getBoolean(Keys.RECOVERY_ONLY, false);
		this.stopping = json.getBoolean(Keys.STOPPING, false);
		this.clustered = json.getBoolean(Keys.CLUSTERED, false);
		this.deploymentCount = json.getInteger(Keys.DEPLOYMENT_COUNT, 0);
		this.controlPlaneDeployments = json.getInteger(Keys.CONTROL_PLANE_DEPLOYMENTS, 0);
		this.dataPlaneDeployments = json.getInteger(Keys.DATA_PLANE_DEPLOYMENTS, 0);
		this.infrastructureDeployments = json.getInteger(Keys.INFRASTRUCTURE_DEPLOYMENTS, 0);
		this.lifecycleEventNamespace = json.getString(Keys.LIFECYCLE_EVENT_NAMESPACE);
		this.targetInvalidationProvider = json.getString(Keys.TARGET_INVALIDATION_PROVIDER);
		this.targetInvalidationNamespace = json.getString(Keys.TARGET_INVALIDATION_NAMESPACE);
		this.targetInvalidationMaxTargets = json.getInteger(
			Keys.TARGET_INVALIDATION_MAX_TARGETS,
			0
		);
		this.services = json.getJsonArray(Keys.SERVICES, new JsonArray()).stream()
			.map(JsonObject.class::cast)
			.map(AdminNodeServiceView::new)
			.toList();
	}

	public static Builder builder() {
		return new Builder();
	}

	public JsonObject toJson() {
		return new JsonObject()
			.put(Keys.STARTED, started)
			.put(Keys.READY, ready)
			.put(Keys.RECOVERY_ONLY, recoveryOnly)
			.put(Keys.STOPPING, stopping)
			.put(Keys.CLUSTERED, clustered)
			.put(Keys.DEPLOYMENT_COUNT, deploymentCount)
			.put(Keys.CONTROL_PLANE_DEPLOYMENTS, controlPlaneDeployments)
			.put(Keys.DATA_PLANE_DEPLOYMENTS, dataPlaneDeployments)
			.put(Keys.INFRASTRUCTURE_DEPLOYMENTS, infrastructureDeployments)
			.put(Keys.LIFECYCLE_EVENT_NAMESPACE, lifecycleEventNamespace)
			.put(Keys.TARGET_INVALIDATION_PROVIDER, targetInvalidationProvider)
			.put(Keys.TARGET_INVALIDATION_NAMESPACE, targetInvalidationNamespace)
			.put(Keys.TARGET_INVALIDATION_MAX_TARGETS, targetInvalidationMaxTargets)
			.put(Keys.SERVICES, new JsonArray(services.stream()
				.map(AdminNodeServiceView::toJson)
				.toList()));
	}

	public static final class Builder {
		private boolean started;
		private boolean ready;
		private boolean recoveryOnly;
		private boolean stopping;
		private boolean clustered;
		private Integer deploymentCount;
		private Integer controlPlaneDeployments;
		private Integer dataPlaneDeployments;
		private Integer infrastructureDeployments;
		private String lifecycleEventNamespace;
		private String targetInvalidationProvider;
		private String targetInvalidationNamespace;
		private Integer targetInvalidationMaxTargets;
		private List<AdminNodeServiceView> services = List.of();

		private Builder() {
		}

		public Builder withStarted(boolean value) {
			started = value;
			return this;
		}

		public Builder withReady(boolean value) {
			ready = value;
			return this;
		}

		public Builder withRecoveryOnly(boolean value) {
			recoveryOnly = value;
			return this;
		}

		public Builder withStopping(boolean value) {
			stopping = value;
			return this;
		}

		public Builder withClustered(boolean value) {
			clustered = value;
			return this;
		}

		public Builder withDeploymentCount(int value) {
			deploymentCount = value;
			return this;
		}

		public Builder withControlPlaneDeployments(int value) {
			controlPlaneDeployments = value;
			return this;
		}

		public Builder withDataPlaneDeployments(int value) {
			dataPlaneDeployments = value;
			return this;
		}

		public Builder withInfrastructureDeployments(int value) {
			infrastructureDeployments = value;
			return this;
		}

		public Builder withLifecycleEventNamespace(String value) {
			lifecycleEventNamespace = value;
			return this;
		}

		public Builder withTargetInvalidationProvider(String value) {
			targetInvalidationProvider = value;
			return this;
		}

		public Builder withTargetInvalidationNamespace(String value) {
			targetInvalidationNamespace = value;
			return this;
		}

		public Builder withTargetInvalidationMaxTargets(int value) {
			targetInvalidationMaxTargets = value;
			return this;
		}

		public Builder withServices(List<AdminNodeServiceView> value) {
			services = value == null ? List.of() : List.copyOf(value);
			return this;
		}

		public AdminNodeStatusResult build() {
			AdminNodeStatusResult result = new AdminNodeStatusResult();
			result.started = started;
			result.ready = ready;
			result.recoveryOnly = recoveryOnly;
			result.stopping = stopping;
			result.clustered = clustered;
			result.deploymentCount = Objects.requireNonNull(deploymentCount, "deploymentCount");
			result.controlPlaneDeployments = Objects.requireNonNull(
				controlPlaneDeployments,
				"controlPlaneDeployments"
			);
			result.dataPlaneDeployments = Objects.requireNonNull(
				dataPlaneDeployments,
				"dataPlaneDeployments"
			);
			result.infrastructureDeployments = Objects.requireNonNull(
				infrastructureDeployments,
				"infrastructureDeployments"
			);
			result.lifecycleEventNamespace = Objects.requireNonNull(
				lifecycleEventNamespace,
				"lifecycleEventNamespace"
			);
			result.targetInvalidationProvider = Objects.requireNonNull(
				targetInvalidationProvider,
				"targetInvalidationProvider"
			);
			result.targetInvalidationNamespace = Objects.requireNonNull(
				targetInvalidationNamespace,
				"targetInvalidationNamespace"
			);
			result.targetInvalidationMaxTargets = Objects.requireNonNull(
				targetInvalidationMaxTargets,
				"targetInvalidationMaxTargets"
			);
			result.services = services;
			return result;
		}
	}
}
