package com.inqwise.indexer.example.hn.reports;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class HackerNewsReportDependencyDirectionTest {
	private static final List<String> FORBIDDEN_REPORT_IMPORTS = List.of(
		"com.inqwise.indexer.actions",
		"com.inqwise.indexer.service.action",
		"com.inqwise.indexer.example.hn.HackerNewsApplicationVerticle",
		"com.inqwise.indexer.example.hn.HackerNewsClient",
		"com.inqwise.indexer.example.hn.HackerNewsDocumentProjector",
		"com.inqwise.indexer.example.hn.HackerNewsIngestionService",
		"com.inqwise.indexer.example.hn.HackerNewsOptions"
	);

	@Test
	void reportsStayIndependentFromIngestionAndActionTransport() throws IOException {
		Path reports = Path.of("src/main/java/com/inqwise/indexer/example/hn/reports");
		try (Stream<Path> files = Files.walk(reports)) {
			for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
				String source = Files.readString(file);
				for (String forbidden : FORBIDDEN_REPORT_IMPORTS) {
					assertFalse(
						source.contains("import " + forbidden),
						() -> file + " imports out-of-scope dependency " + forbidden
					);
				}
			}
		}
	}

}
