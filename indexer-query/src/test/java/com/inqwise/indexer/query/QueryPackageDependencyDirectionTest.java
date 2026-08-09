package com.inqwise.indexer.query;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class QueryPackageDependencyDirectionTest {
	private static final List<String> FORBIDDEN_IMPORTS = List.of(
		"com.inqwise.indexer.adapters",
		"com.inqwise.indexer.commands",
		"com.inqwise.indexer.load",
		"com.inqwise.indexer.node",
		"com.inqwise.indexer.rest",
		"com.inqwise.indexer.routing",
		"com.inqwise.indexer.runtime"
	);

	@Test
	void mainSourcesDoNotDependOnOutOfScopePackages() throws IOException {
		Path sources = Path.of("src/main/java");
		try (Stream<Path> files = Files.walk(sources)) {
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
