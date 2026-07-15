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
				.forEach(path -> inspect(path, violations));
		}

		assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
	}

	private static boolean isDeploymentEnvelope(Path path) {
		Path relative = MAIN_PACKAGE.relativize(path);
		return relative.getNameCount() > 1
			&& ENVELOPE_PACKAGES.contains(relative.getName(0).toString());
	}

	private static void inspect(Path path, List<String> violations) {
		try {
			for (String line : Files.readAllLines(path)) {
				for (String envelopePackage : ENVELOPE_PACKAGES) {
					String typePrefix = "com.inqwise.indexer." + envelopePackage + ".";
					if (line.startsWith("import " + typePrefix)
						|| line.startsWith("import static " + typePrefix)) {
						violations.add(
							path + ": inward package must not import deployment envelope: " + line
						);
					}
				}
			}
		} catch (IOException error) {
			throw new IllegalStateException("Failed to inspect " + path, error);
		}
	}
}
