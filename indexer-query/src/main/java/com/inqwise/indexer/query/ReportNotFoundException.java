package com.inqwise.indexer.query;

public final class ReportNotFoundException extends RuntimeException {
	private final String reportName;

	public ReportNotFoundException(String reportName) {
		super("Report not found: " + reportName);
		this.reportName = reportName;
	}

	public String reportName() {
		return reportName;
	}
}
