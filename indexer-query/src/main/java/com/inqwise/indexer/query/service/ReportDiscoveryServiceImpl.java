package com.inqwise.indexer.query.service;

import java.util.Comparator;
import java.util.Objects;

import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.presentation.ReportPresentation;

import io.vertx.core.Future;

public final class ReportDiscoveryServiceImpl implements ReportDiscoveryService {
	private static final int MAX_PRESENTATIONS = 256;
	private final ReportCatalog reports;

	public ReportDiscoveryServiceImpl(ReportCatalog reports) {
		this.reports = Objects.requireNonNull(reports, "reports");
	}

	@Override
	public Future<ReportDiscoveryResult> discover() {
		try {
			var presentations = reports.presentations();
			if (presentations.size() > MAX_PRESENTATIONS) {
				throw new IllegalStateException("Report presentation catalog is too large");
			}
			return Future.succeededFuture(ReportDiscoveryResult.builder()
				.withReports(presentations.stream()
					.sorted(Comparator.comparing(ReportPresentation::getName))
					.toList())
				.build());
		} catch (Throwable error) {
			return Future.failedFuture(QueryErrors.normalize(error));
		}
	}
}
