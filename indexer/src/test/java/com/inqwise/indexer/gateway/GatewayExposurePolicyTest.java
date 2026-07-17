package com.inqwise.indexer.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.openapi.contract.OpenAPIContract;
import io.vertx.openapi.contract.Operation;

@ExtendWith(VertxExtension.class)
class GatewayExposurePolicyTest {
	private static final Set<PublicOperation> APPROVED_OPERATIONS = Set.of(
		new PublicOperation(HttpMethod.GET, "/gateway/status", "gatewayStatus"),
		new PublicOperation(HttpMethod.GET, "/gateway/admin/targets", "gatewayListTargets"),
		new PublicOperation(HttpMethod.GET, "/gateway/admin/indexers", "gatewayListIndexers")
	);

	@Test
	void exposesOnlyApprovedPublicOperations(Vertx vertx, VertxTestContext testContext) {
		OpenAPIContract.from(vertx, GatewayRestOptions.DEFAULT_OPEN_API_PATH)
			.onComplete(testContext.succeeding(contract -> testContext.verify(() -> {
				assertEquals(APPROVED_OPERATIONS.size(), contract.operations().size());
				Set<PublicOperation> actual = contract.operations().stream()
					.map(PublicOperation::from)
					.collect(Collectors.toUnmodifiableSet());

				assertEquals(APPROVED_OPERATIONS, actual);
				contract.operations().forEach(this::assertPublicResponses);
				testContext.completeNow();
			})));
	}

	private void assertPublicResponses(Operation operation) {
		assertNotNull(operation.getResponse(200), operation.getOperationId() + " must declare success");
		assertNotNull(
			operation.getDefaultResponse(),
			operation.getOperationId() + " must declare the safe public error response"
		);
	}

	private record PublicOperation(HttpMethod method, String path, String operationId) {
		private static PublicOperation from(Operation operation) {
			return new PublicOperation(
				operation.getHttpMethod(),
				operation.getOpenAPIPath(),
				operation.getOperationId()
			);
		}
	}
}
