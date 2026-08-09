package com.inqwise.indexer.query;

public final class UnsupportedReportSchemaException extends RuntimeException {
	private final String reportName;
	private final IndexSchema schema;

	public UnsupportedReportSchemaException(String reportName, IndexSchema schema) {
		super("Report " + reportName + " does not support schema " + schema.name()
			+ ":" + schema.version());
		this.reportName = reportName;
		this.schema = schema;
	}

	public String reportName() {
		return reportName;
	}

	public IndexSchema schema() {
		return schema;
	}
}
