package com.inqwise.indexer.node;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.inqwise.indexer.catalog.targets.TargetDefinition;
import com.inqwise.indexer.catalog.targets.TargetPeriodStrategy;
import com.inqwise.indexer.rest.action.TargetActionRestOptions;
import com.inqwise.indexer.rest.admin.AdminRestOptions;
import com.inqwise.indexer.rest.runtime.RuntimeRestOptions;

import io.vertx.core.Vertx;

public final class LocalIndexerApplication {
	private static final int SHUTDOWN_TIMEOUT_SECONDS = 10;

	private LocalIndexerApplication() {
	}

	public static void main(String[] args) {
		Vertx vertx = Vertx.vertx();
		IndexerNodeOptions options = localOptions();
		IndexerNodeComponents components = new DefaultIndexerNodeComponentsFactory().create(
			vertx,
			options,
			localTargetDefinitions()
		);
		IndexerNode node = new IndexerNode(vertx, options, components);

		node.start().onComplete(result -> {
			if (result.failed()) {
				System.err.println("Local indexer failed to start: " + result.cause().getMessage());
				result.cause().printStackTrace(System.err);
				vertx.close().onComplete(ignored -> System.exit(1));
				return;
			}

			Runtime.getRuntime().addShutdownHook(new Thread(
				() -> stop(node, vertx),
				"indexer-local-shutdown"
			));
			System.out.println("Local indexer started with in-memory adapters.");
			System.out.println("Admin API:        http://127.0.0.1:8080/admin");
			System.out.println("Target Action API: http://127.0.0.1:8081/targets/customers/actions");
			System.out.println("Runtime API:      http://127.0.0.1:8083/runtime/status");
			System.out.println("Press Ctrl+C to stop. In-memory state is discarded on shutdown.");
		});
	}

	static IndexerNodeOptions localOptions() {
		IndexerServiceDeploymentOptions enabled = IndexerServiceDeploymentOptions.builder().build();
		return IndexerNodeOptions.builder()
			.withService(IndexerNodeOptions.Services.ADMIN_REST, enabled)
			.withService(IndexerNodeOptions.Services.TARGET_ACTION_REST, enabled)
			.withService(IndexerNodeOptions.Services.RUNTIME_REST, enabled)
			.withAdminRestOptions(AdminRestOptions.builder().build())
			.withTargetActionRestOptions(TargetActionRestOptions.builder().build())
			.withRuntimeRestOptions(RuntimeRestOptions.builder().build())
			.build();
	}

	static List<TargetDefinition> localTargetDefinitions() {
		return List.of(TargetDefinition.builder()
			.withTargetName("customers")
			.withPeriodStrategy(TargetPeriodStrategy.MONTHLY)
			.withAutoProvisionOnWrite(true)
			.build());
	}

	private static void stop(IndexerNode node, Vertx vertx) {
		try {
			node.stop()
				.compose(ignored -> vertx.close())
				.toCompletionStage()
				.toCompletableFuture()
				.get(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		} catch (Exception error) {
			System.err.println("Local indexer shutdown failed: " + error.getMessage());
		}
	}
}
