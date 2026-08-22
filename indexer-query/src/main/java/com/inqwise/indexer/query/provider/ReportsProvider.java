package com.inqwise.indexer.query.provider;

import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.service.ReportsService;

public interface ReportsProvider {
	ReportCatalog catalog();

	ReportsService service();
}
