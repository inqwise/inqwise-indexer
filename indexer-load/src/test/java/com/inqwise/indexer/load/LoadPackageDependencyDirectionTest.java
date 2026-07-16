package com.inqwise.indexer.load;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.inqwise.indexer.load.repository.LoadIndexerReference;

import org.junit.jupiter.api.Test;

class LoadPackageDependencyDirectionTest {
	private static final Path MAIN_SOURCE = Path.of("src/main/java");
	private static final Path TEST_SOURCE = Path.of("src/test/java");
	private static final String LOAD_PACKAGE = "com.inqwise.indexer.load.";
	private static final Set<String> FORBIDDEN_MAIN_IMPORT_PREFIXES = Set.of(
		"import com.inqwise.indexer.adapters.",
		"import com.inqwise.indexer.gateway.",
		"import com.inqwise.indexer.node.",
		"import com.inqwise.indexer.rest.",
		"import com.inqwise.indexer.service."
	);
	private static final Set<String> STABLE_MAIN_INDEXER_IMPORTS = Set.of(
		"com.inqwise.indexer.actions.Actions",
		"com.inqwise.indexer.actions.CatchUpBarrierActionItem",
		"com.inqwise.indexer.actions.CompleteIndexActionItem",
		"com.inqwise.indexer.actions.IndexerActionItem",
		"com.inqwise.indexer.actions.IndexerActionRouteContext",
		"com.inqwise.indexer.actions.IndexerActionRouteMode",
		"com.inqwise.indexer.actions.IndexerActionType",
		"com.inqwise.indexer.catalog.indexers.IndexResourceOwnership",
		"com.inqwise.indexer.catalog.indexers.IndexerModel",
		"com.inqwise.indexer.catalog.indexers.IndexerRole",
		"com.inqwise.indexer.catalog.indexers.IndexerRuntimeState",
		"com.inqwise.indexer.catalog.indexers.IndexerType",
		"com.inqwise.indexer.cleanup.DeleteIndexerCommand",
		"com.inqwise.indexer.commands.Command",
		"com.inqwise.indexer.commands.CommandEngine",
		"com.inqwise.indexer.commands.CommandHandler",
		"com.inqwise.indexer.commands.CommandPartitionKey",
		"com.inqwise.indexer.commands.CommandPartitionKeyRouter",
		"com.inqwise.indexer.commands.CommandService",
		"com.inqwise.indexer.routing.SubmitIndexActionsCommand",
		"com.inqwise.indexer.errors.RetryableStaleStateException",
		"com.inqwise.indexer.lifecycle.IndexerLifecycleEventBus",
		"com.inqwise.indexer.lifecycle.IndexerMetadataChanged",
		"com.inqwise.indexer.providers.ActionReceiveReadiness",
		"com.inqwise.indexer.providers.IndexerActionReceiveCapability",
		"com.inqwise.indexer.providers.IndexerPlugin",
		"com.inqwise.indexer.providers.PrepareIndexerForActionsRequest",
		"com.inqwise.indexer.providers.PreparedIndexers",
		"com.inqwise.indexer.provisioning.GeneratedIndexerResources",
		"com.inqwise.indexer.provisioning.IndexerResourceNameGenerator",
		"com.inqwise.indexer.runtime.IndexerEvent",
		"com.inqwise.indexer.runtime.IndexerEventPublisher",
		"com.inqwise.indexer.runtime.IndexerEventType",
		"com.inqwise.indexer.providers.IndexerMarkerHandler",
		"com.inqwise.indexer.runtime.IndexerQueueClient",
		"com.inqwise.indexer.runtime.IndexerQueuePublisher"
	);
	private static final Map<String, Set<String>> METADATA_ADAPTER_INDEXER_IMPORTS = Map.ofEntries(
		entry("adapters/metadata/MetadataLoadCreationCatalog.java", Set.of(
			"com.inqwise.indexer.metadata.CreateIndexerOperation",
			"com.inqwise.indexer.metadata.DocumentStoreMetadataRepository",
			"com.inqwise.indexer.metadata.IndexerRecord",
			"com.inqwise.indexer.metadata.InsertIndexer",
			"com.inqwise.indexer.catalog.indexers.MutationState",
			"com.inqwise.indexer.publication.PublicationState",
			"com.inqwise.indexer.catalog.targets.TargetProvisioningState",
			"com.inqwise.indexer.metadata.TargetRecord",
			"com.inqwise.indexer.catalog.targets.TargetStatus"
		)),
		entry("adapters/metadata/MetadataLazyLiveWriterCatalog.java", Set.of(
			"com.inqwise.indexer.metadata.CreateIndexerOperation",
			"com.inqwise.indexer.metadata.DocumentStoreMetadataRepository",
			"com.inqwise.indexer.metadata.InsertIndexer",
			"com.inqwise.indexer.metadata.MetadataIndexerModels",
			"com.inqwise.indexer.catalog.indexers.MutationState",
			"com.inqwise.indexer.publication.PublicationState"
		)),
		entry("adapters/metadata/MetadataLoadPublicationRepository.java", Set.of(
			"com.inqwise.indexer.metadata.DocumentStoreMetadataRepository",
			"com.inqwise.indexer.catalog.indexers.IndexerProvisioningState",
			"com.inqwise.indexer.metadata.IndexerRecord",
			"com.inqwise.indexer.catalog.indexers.IndexerStatus",
			"com.inqwise.indexer.catalog.indexers.MutationState",
			"com.inqwise.indexer.publication.PublicationState",
			"com.inqwise.indexer.metadata.ReplacePublishedIndexer"
		))
	);
	private static final Set<String> ALLOWED_ROOT_TESTS = Set.of(
		"LoadApplicationCompositionTest.java",
		"LoadPackageDependencyDirectionTest.java"
	);
	private static final Set<String> ALLOWED_TEST_SLICES = Set.of(
		"commands",
		"catalog",
		"repository",
		"runtime",
		"testing",
		"workflow"
	);
	private static final Set<String> WORKFLOW_ALLOWED_COMMAND_IMPORTS = Set.of(
		"com.inqwise.indexer.load.commands.CleanupLoadCommand",
		"com.inqwise.indexer.load.commands.LoadPublicationOrchestrator"
	);
	private static final Set<String> RUNTIME_ALLOWED_COMMAND_IMPORTS = Set.of(
		"com.inqwise.indexer.load.commands.LoadPublicationOrchestrator"
	);
	private static final Map<String, Set<String>> ALLOWED_LOAD_IMPORTS = Map.of(
		"api", Set.of(),
		"catalog", Set.of("api"),
		"repository", Set.of("api"),
		"workflow", Set.of("api", "catalog", "commands", "repository"),
		"runtime", Set.of("api", "catalog", "commands", "events", "repository"),
		"commands", Set.of("api", "repository"),
		"events", Set.of(),
		"adapters.local", Set.of("api", "repository"),
		"adapters.metadata", Set.of("api", "catalog", "repository")
	);

	@Test
	void mainLoadPackagesFollowDependencyDirection() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_SOURCE.resolve("com/inqwise/indexer/load"))) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspect(path, violations));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void mainLoadPackagesDoNotImportDeploymentFacingIndexerPackages() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_SOURCE.resolve("com/inqwise/indexer/load"))) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspectForbiddenMainImports(path, violations));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void mainLoadPackagesUseOnlyAcceptedIndexerContractTypes() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_SOURCE.resolve("com/inqwise/indexer/load"))) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspectAcceptedIndexerContractTypes(path, violations));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void publicationAndCleanupReferenceContainsOnlyRequiredIndexerIdentity() {
		assertArrayEquals(
			new String[] { "id", "targetId", "version" },
			Stream.of(LoadIndexerReference.class.getRecordComponents())
				.map(component -> component.getName())
				.toArray(String[]::new)
		);
	}

	@Test
	void rootTestPackageContainsOnlyAcceptedModuleLevelTests() throws IOException {
		Path rootTestPackage = TEST_SOURCE.resolve("com/inqwise/indexer/load");
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.list(rootTestPackage)) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !ALLOWED_ROOT_TESTS.contains(path.getFileName().toString()))
				.forEach(path -> violations.add(path + ": root load test package is reserved for module-level composition and architecture guards"));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void nonRootTestPackagesUseKnownLoadSlices() throws IOException {
		Path rootTestPackage = TEST_SOURCE.resolve("com/inqwise/indexer/load");
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(rootTestPackage)) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !rootTestPackage.equals(path.getParent()))
				.forEach(path -> {
					Path relative = rootTestPackage.relativize(path);
					String testSlice = relative.getName(0).toString();
					if (!ALLOWED_TEST_SLICES.contains(testSlice)) {
						violations.add(path + ": unknown load test slice " + testSlice);
					}
				});
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	private static void inspect(Path path, List<String> violations) {
		String sourceSlice = sourceSlice(path);
		Set<String> allowed = ALLOWED_LOAD_IMPORTS.get(sourceSlice);
		if (allowed == null) {
			violations.add(path + ": unknown load source slice " + sourceSlice);
			return;
		}

		try {
			for (String line : Files.readAllLines(path)) {
				if (!line.startsWith("import " + LOAD_PACKAGE)) {
					continue;
				}
				if (line.endsWith(".*;")) {
					violations.add(path + ": wildcard load import is not allowed: " + line);
					continue;
				}
				String imported = importedType(line);
				String targetSlice = targetSlice(line);
				if (!sourceSlice.equals(targetSlice) && !allowed.contains(targetSlice)) {
					violations.add(path + ": " + sourceSlice + " must not import " + targetSlice + ": " + line);
				}
				if ("commands".equals(targetSlice)
					&& "workflow".equals(sourceSlice)
					&& !WORKFLOW_ALLOWED_COMMAND_IMPORTS.contains(imported)) {
					violations.add(path + ": workflow may import only command payload/handoff types: " + line);
				}
				if ("commands".equals(targetSlice)
					&& "runtime".equals(sourceSlice)
					&& !RUNTIME_ALLOWED_COMMAND_IMPORTS.contains(imported)) {
					violations.add(path + ": runtime may import only the publication command handoff helper: " + line);
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to inspect " + path, e);
		}
	}

	private static void inspectForbiddenMainImports(Path path, List<String> violations) {
		try {
			for (String line : Files.readAllLines(path)) {
				for (String prefix : FORBIDDEN_MAIN_IMPORT_PREFIXES) {
					if (line.startsWith(prefix)) {
						violations.add(path + ": load production code must not import deployment-facing indexer packages: " + line);
					}
				}
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to inspect " + path, e);
		}
	}

	private static void inspectAcceptedIndexerContractTypes(Path path, List<String> violations) {
		try {
			for (String line : Files.readAllLines(path)) {
				if (!line.startsWith("import com.inqwise.indexer.")
					|| line.startsWith("import " + LOAD_PACKAGE)) {
					continue;
				}
				String imported = importedType(line);
				if (STABLE_MAIN_INDEXER_IMPORTS.contains(imported)) {
					continue;
				}
				if (isMetadataAdapterImport(path, imported)) {
					continue;
				}
				violations.add(path + ": load production code must use only accepted indexer contract/support types: " + line);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Failed to inspect " + path, e);
		}
	}

	private static boolean isMetadataAdapterImport(Path path, String imported) {
		return METADATA_ADAPTER_INDEXER_IMPORTS
			.getOrDefault(relativeMainPath(path), Set.of())
			.contains(imported);
	}

	private static String relativeMainPath(Path path) {
		return MAIN_SOURCE.resolve("com/inqwise/indexer/load").relativize(path)
			.toString()
			.replace('\\', '/');
	}

	private static String sourceSlice(Path path) {
		Path relative = MAIN_SOURCE.resolve("com/inqwise/indexer/load").relativize(path);
		String first = relative.getName(0).toString();
		if ("adapters".equals(first)) {
			return "adapters." + relative.getName(1);
		}
		return first;
	}

	private static String targetSlice(String importLine) {
		String imported = importedType(importLine).substring(LOAD_PACKAGE.length());
		String first = imported.substring(0, imported.indexOf('.'));
		if ("adapters".equals(first)) {
			String rest = imported.substring("adapters.".length());
			return "adapters." + rest.substring(0, rest.indexOf('.'));
		}
		return first;
	}

	private static String importedType(String importLine) {
		return importLine.substring("import ".length(), importLine.length() - 1);
	}
}
