package com.inqwise.indexer.example.hn.reports;

import java.util.List;

import com.inqwise.indexer.query.DefaultReportCatalog;
import com.inqwise.indexer.query.ReportCatalog;

public final class HackerNewsReportCatalog {
	private HackerNewsReportCatalog() {
	}

	public static ReportCatalog create() {
		return DefaultReportCatalog.builder()
			.withDefinitions(List.of(
				new HackerNewsStoriesReportDefinition(),
				new HackerNewsAuthorSummaryReportDefinition()
			))
			.build();
	}
}
