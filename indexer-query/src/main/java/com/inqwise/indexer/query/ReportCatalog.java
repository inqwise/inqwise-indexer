package com.inqwise.indexer.query;

import java.util.Collection;
import java.util.Optional;

public interface ReportCatalog {
	Optional<ReportDefinition<?, ?>> find(String reportName);

	Collection<ReportDescriptor> descriptors();
}
