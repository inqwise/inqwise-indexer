package com.inqwise.indexer.query.monitoring;

public interface ReportOperationalMonitor {
	ReportOperationalMonitor NOOP = new ReportOperationalMonitor() {
		@Override
		public void executionStarted(String reportName) {
		}

		@Override
		public void executionCompleted(
			String reportName,
			ReportExecutionOutcome outcome,
			long durationNanos
		) {
		}
	};

	void executionStarted(String reportName);

	void executionCompleted(
		String reportName,
		ReportExecutionOutcome outcome,
		long durationNanos
	);
}
