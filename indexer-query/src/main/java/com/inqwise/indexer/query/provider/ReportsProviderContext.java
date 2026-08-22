package com.inqwise.indexer.query.provider;

import java.util.Objects;

import com.inqwise.indexer.publication.PublishedIndexResolver;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;

public record ReportsProviderContext(
	PublishedIndexResolver publishedIndexes,
	DocumentSnapshotReader documents,
	ReportOperationalMonitor monitor
) {
	public ReportsProviderContext {
		Objects.requireNonNull(publishedIndexes, "publishedIndexes");
		Objects.requireNonNull(documents, "documents");
		Objects.requireNonNull(monitor, "monitor");
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private PublishedIndexResolver publishedIndexes;
		private DocumentSnapshotReader documents;
		private ReportOperationalMonitor monitor = ReportOperationalMonitor.NOOP;

		private Builder() {
		}

		public Builder withPublishedIndexes(PublishedIndexResolver value) {
			publishedIndexes = value;
			return this;
		}

		public Builder withDocuments(DocumentSnapshotReader value) {
			documents = value;
			return this;
		}

		public Builder withMonitor(ReportOperationalMonitor value) {
			monitor = value;
			return this;
		}

		public ReportsProviderContext build() {
			return new ReportsProviderContext(
				publishedIndexes,
				documents,
				monitor
			);
		}
	}
}
