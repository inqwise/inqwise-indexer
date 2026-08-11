package com.inqwise.indexer.query.rest;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ReportsRestDependencyDirectionTest {
	private static final List<String> FORBIDDEN_IMPORTS = List.of(
		"com.inqwise.indexer.example",
		"com.inqwise.indexer.node",
		"com.inqwise.indexer.runtime",
		"com.inqwise.indexer.actions",
		"com.inqwise.indexer.load"
	);

	@Test
	void mainSourcesStayConsumerAndDeploymentNeutral() throws IOException {
		try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				for (String forbidden : FORBIDDEN_IMPORTS) {
					assertFalse(
						source.contains("import " + forbidden),
						() -> file + " imports out-of-scope package " + forbidden
					);
				}
			}
		}
	}
}
