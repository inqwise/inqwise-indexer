package com.inqwise.indexer.example.hn.reports;

import com.inqwise.indexer.query.QueryFilter;

public enum HackerNewsSourceFilter implements QueryFilter {
	INSTANCE;

	public String source() {
		return HackerNewsReportConstants.SOURCE_NAME;
	}
}
