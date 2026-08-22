package com.inqwise.indexer.query.provider;

import java.util.Objects;

import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.service.ReportsService;

public final class DefaultReportsProvider implements ReportsProvider {
	private final ReportCatalog catalog;
	private final ReportsService service;

	private DefaultReportsProvider(Builder builder) {
		catalog = Objects.requireNonNull(builder.catalog, "catalog");
		service = Objects.requireNonNull(builder.service, "service");
	}

	@Override
	public ReportCatalog catalog() {
		return catalog;
	}

	@Override
	public ReportsService service() {
		return service;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private ReportCatalog catalog;
		private ReportsService service;

		private Builder() {
		}

		public Builder withCatalog(ReportCatalog value) {
			catalog = value;
			return this;
		}

		public Builder withService(ReportsService value) {
			service = value;
			return this;
		}

		public DefaultReportsProvider build() {
			return new DefaultReportsProvider(this);
		}
	}
}
