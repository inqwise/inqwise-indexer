package com.inqwise.indexer.query;

import java.util.Collection;
import java.util.Optional;

import com.inqwise.indexer.query.presentation.ReportPresentation;

public interface ReportCatalog {
	Optional<ReportDefinition<?, ?>> find(String reportName);

	Collection<ReportDescriptor> descriptors();

	Collection<ReportPresentation> presentations();
}
