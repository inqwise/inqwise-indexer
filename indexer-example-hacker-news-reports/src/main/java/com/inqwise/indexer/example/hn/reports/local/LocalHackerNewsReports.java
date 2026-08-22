package com.inqwise.indexer.example.hn.reports.local;

import java.util.Objects;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportCatalog;
import com.inqwise.indexer.example.hn.reports.HackerNewsReportConstants;
import com.inqwise.indexer.publication.PublishedIndexResolver;
import com.inqwise.indexer.query.DefaultReportsFacade;
import com.inqwise.indexer.query.ReportExecutionContextResolver;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;
import com.inqwise.indexer.query.provider.DocumentSnapshotReader;
import com.inqwise.indexer.query.service.ReportCaller;
import com.inqwise.indexer.query.service.ReportsService;
import com.inqwise.indexer.query.service.ReportsServiceImpl;

public final class LocalHackerNewsReports {
	private LocalHackerNewsReports() {
	}

	public static ReportsService create(
		PublishedIndexResolver publishedIndexes,
		InMemoryIndexerDocumentStore documents,
		ReportExecutionContextResolver contexts
	) {
		return create(
			publishedIndexes,
			Objects.requireNonNull(documents, "documents")::documents,
			contexts
		);
	}

	public static ReportsService create(
		PublishedIndexResolver publishedIndexes,
		InMemoryIndexerDocumentStore documents,
		ReportExecutionContextResolver contexts,
		ReportOperationalMonitor monitor
	) {
		return create(
			publishedIndexes,
			Objects.requireNonNull(documents, "documents")::documents,
			contexts,
			monitor
		);
	}

	public static ReportsService create(
		PublishedIndexResolver publishedIndexes,
		DocumentSnapshotReader documents,
		ReportExecutionContextResolver contexts
	) {
		return create(publishedIndexes, documents, contexts, ReportOperationalMonitor.NOOP);
	}

	public static ReportsService create(
		PublishedIndexResolver publishedIndexes,
		DocumentSnapshotReader documents,
		ReportExecutionContextResolver contexts,
		ReportOperationalMonitor monitor
	) {
		return new ReportsServiceImpl(
			new DefaultReportsFacade(
				HackerNewsReportCatalog.create(),
				Objects.requireNonNull(publishedIndexes, "publishedIndexes"),
				new LocalHackerNewsQueryProvider(
					Objects.requireNonNull(documents, "documents")
				)
			),
			Objects.requireNonNull(contexts, "contexts"),
			ReportCaller.builder()
				.withConsumerName(HackerNewsReportConstants.CONSUMER_NAME)
				.withSubject("local-hacker-news-reports")
				.build(),
			Objects.requireNonNull(monitor, "monitor")
		);
	}
}
