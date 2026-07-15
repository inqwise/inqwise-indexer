package com.inqwise.indexer.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class DeploymentPackageDependencyDirectionTest {
	private static final Path MAIN_PACKAGE = Path.of("src/main/java/com/inqwise/indexer");
	private static final Path CORE_MAIN_PACKAGE = Path.of(
		"../indexer-core/src/main/java/com/inqwise/indexer"
	);
	private static final Set<String> ENVELOPE_PACKAGES = Set.of(
		"gateway",
		"node",
		"rest",
		"service"
	);

	@Test
	void domainAndAdapterPackagesDoNotImportDeploymentEnvelopes() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_PACKAGE)) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !isDeploymentEnvelope(path))
				.forEach(path -> inspectImports(
					path,
					ENVELOPE_PACKAGES,
					"inward package must not import deployment envelope",
					violations
				));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void domainPackagesDoNotImportLocalAdapters() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(MAIN_PACKAGE)) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.filter(path -> !isDeploymentEnvelope(path))
				.filter(path -> !isPackage(path, "adapters"))
				.forEach(path -> inspectImports(
					path,
					Set.of("adapters"),
					"domain package must not import local adapter",
					violations
				));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void targetCatalogAndActionRoutingDependOnProvisioningContract() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path packagePath : List.of(
			MAIN_PACKAGE.resolve("catalog/targets"),
			MAIN_PACKAGE.resolve("routing")
		)) {
			try (Stream<Path> files = Files.walk(packagePath)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectText(
						path,
						"MetadataIndexerProvisioningService",
						"domain consumer must depend on the provisioning contract",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void provisioningContractsDoNotExposeMetadataPersistence() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(CORE_MAIN_PACKAGE.resolve("provisioning"))) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspectImports(
					path,
					Set.of("metadata"),
					"provisioning contract must not expose metadata persistence",
					violations
				));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void publicationContractsDoNotExposeMetadataPersistence() throws IOException {
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(CORE_MAIN_PACKAGE.resolve("publication"))) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspectImports(
					path,
					Set.of("metadata"),
					"publication contract must not expose metadata persistence",
					violations
				));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void actionRoutingDoesNotDependOnHotFastPath() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path routingPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("routing"),
			MAIN_PACKAGE.resolve("routing")
		)) {
			try (Stream<Path> files = Files.walk(routingPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("hot"),
						"action routing must not depend on its hot fast path",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void catalogDoesNotDependOnCleanupWorkflow() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path catalogPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("catalog"),
			MAIN_PACKAGE.resolve("catalog")
		)) {
			try (Stream<Path> files = Files.walk(catalogPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("cleanup"),
						"catalog must not depend on cleanup workflow",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void catalogDoesNotDependOnPhysicalDefinitions() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path catalogPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("catalog"),
			MAIN_PACKAGE.resolve("catalog")
		)) {
			try (Stream<Path> files = Files.walk(catalogPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("definitions"),
						"catalog must own target definitions and not depend on physical definitions",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void commandInfrastructureDoesNotDependOnCleanupWorkflow() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path commandsPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("commands"),
			MAIN_PACKAGE.resolve("commands")
		)) {
			if (!Files.exists(commandsPackage)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(commandsPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("cleanup"),
						"command infrastructure must not depend on cleanup workflow",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void domainErrorsDoNotDependOnCommandInfrastructure() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path errorsPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("errors"),
			MAIN_PACKAGE.resolve("errors")
		)) {
			if (!Files.exists(errorsPackage)) {
				continue;
			}
			try (Stream<Path> files = Files.walk(errorsPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("commands"),
						"domain errors must not depend on command infrastructure",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void lifecycleDoesNotDependOnHotRouting() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path lifecyclePackage : List.of(
			CORE_MAIN_PACKAGE.resolve("lifecycle"),
			MAIN_PACKAGE.resolve("lifecycle")
		)) {
			try (Stream<Path> files = Files.walk(lifecyclePackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("hot"),
						"lifecycle must not depend on hot routing",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void providersDoNotDependOnRuntimeOrHotRouting() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path providersPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("providers"),
			MAIN_PACKAGE.resolve("providers")
		)) {
			try (Stream<Path> files = Files.walk(providersPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("hot", "runtime"),
						"provider boundary must not depend on runtime or hot routing",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	@Test
	void actionsDoNotDependOnGenericSpiPackage() throws IOException {
		List<String> violations = new ArrayList<>();
		for (Path actionsPackage : List.of(
			CORE_MAIN_PACKAGE.resolve("actions"),
			MAIN_PACKAGE.resolve("actions")
		)) {
			try (Stream<Path> files = Files.walk(actionsPackage)) {
				files
					.filter(path -> path.toString().endsWith(".java"))
					.forEach(path -> inspectImports(
						path,
						Set.of("spi"),
						"action boundary must own its extension contracts",
						violations
					));
			}
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	private static boolean isDeploymentEnvelope(Path path) {
		return ENVELOPE_PACKAGES.stream().anyMatch(packageName -> isPackage(path, packageName));
	}

	private static boolean isPackage(Path path, String packageName) {
		Path relative = MAIN_PACKAGE.relativize(path);
		return relative.getNameCount() > 1
			&& packageName.equals(relative.getName(0).toString());
	}

	private static void inspectImports(
		Path path,
		Set<String> forbiddenPackages,
		String message,
		List<String> violations
	) {
		try {
			for (String line : Files.readAllLines(path)) {
				for (String forbiddenPackage : forbiddenPackages) {
					String typePrefix = "com.inqwise.indexer." + forbiddenPackage + ".";
					if (line.startsWith("import " + typePrefix)
						|| line.startsWith("import static " + typePrefix)) {
						violations.add(path + ": " + message + ": " + line);
					}
				}
			}
		} catch (IOException error) {
			throw new IllegalStateException("Failed to inspect " + path, error);
		}
	}

	private static void inspectText(
		Path path,
		String forbiddenText,
		String message,
		List<String> violations
	) {
		try {
			for (String line : Files.readAllLines(path)) {
				if (line.contains(forbiddenText)) {
					violations.add(path + ": " + message + ": " + line.trim());
				}
			}
		} catch (IOException error) {
			throw new IllegalStateException("Failed to inspect " + path, error);
		}
	}
}
