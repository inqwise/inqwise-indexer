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
	void actionRoutingDoesNotConstructProvisioningServices() throws IOException {
		Path routingPackage = MAIN_PACKAGE.resolve("routing");
		List<String> violations = new ArrayList<>();
		try (Stream<Path> files = Files.walk(routingPackage)) {
			files
				.filter(path -> path.toString().endsWith(".java"))
				.forEach(path -> inspectText(
					path,
					"new IndexerProvisioningService(",
					"action routing must receive the provisioning service from composition",
					violations
				));
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
