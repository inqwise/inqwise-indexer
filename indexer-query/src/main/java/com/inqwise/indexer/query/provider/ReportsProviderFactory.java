package com.inqwise.indexer.query.provider;

public interface ReportsProviderFactory {
	String id();

	ReportsProvider create(ReportsProviderContext context);
}
