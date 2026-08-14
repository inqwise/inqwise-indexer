package com.inqwise.indexer.node.application.monitoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.Test;

import com.inqwise.indexer.query.monitoring.ReportExecutionOutcome;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;

class MicrometerReportOperationalMonitorTest {
	@Test
	void recordsFixedReportSeriesAndActiveExecutions() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MicrometerReportOperationalMonitor monitor =
			new MicrometerReportOperationalMonitor(registry, Set.of("stories"));

		monitor.executionStarted("stories");
		assertEquals(
			1,
			registry.get(MicrometerReportOperationalMonitor.ACTIVE)
				.tag("report", "stories")
				.gauge()
				.value()
		);
		monitor.executionCompleted(
			"stories",
			ReportExecutionOutcome.SUCCEEDED,
			250_000_000L
		);

		assertEquals(
			0,
			registry.get(MicrometerReportOperationalMonitor.ACTIVE)
				.tag("report", "stories")
				.gauge()
				.value()
		);
		assertEquals(
			1,
			registry.get(MicrometerReportOperationalMonitor.EXECUTIONS)
				.tags("report", "stories", "outcome", "succeeded")
				.counter()
				.count()
		);
		assertEquals(
			0.25,
			registry.get(MicrometerReportOperationalMonitor.DURATION)
				.tag("report", "stories")
				.counter()
				.count()
		);
	}

	@Test
	void collapsesUnregisteredNamesIntoOneBoundedSeries() {
		SimpleMeterRegistry registry = new SimpleMeterRegistry();
		MicrometerReportOperationalMonitor monitor =
			new MicrometerReportOperationalMonitor(registry, Set.of("stories"));

		monitor.executionStarted("invented-a");
		monitor.executionCompleted("invented-a", ReportExecutionOutcome.INVALID, 1L);
		monitor.executionStarted("invented-b");
		monitor.executionCompleted("invented-b", ReportExecutionOutcome.INVALID, 1L);

		assertEquals(
			2,
			registry.get(MicrometerReportOperationalMonitor.EXECUTIONS)
				.tags("report", "unknown", "outcome", "invalid")
				.counter()
				.count()
		);
		assertEquals(10, registry.getMeters().size());
	}

	@Test
	void exportsTheFixedPrometheusNamesConsumedByTheConsole() {
		PrometheusMeterRegistry registry =
			new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
		MicrometerReportOperationalMonitor monitor =
			new MicrometerReportOperationalMonitor(registry, Set.of("stories"));
		monitor.executionStarted("stories");
		monitor.executionCompleted(
			"stories",
			ReportExecutionOutcome.SUCCEEDED,
			250_000_000L
		);

		String scrape = registry.scrape();
		assertTrue(scrape.contains("inqwise_indexer_report_executions_total"));
		assertTrue(scrape.contains("inqwise_indexer_report_executions_active"));
		assertTrue(scrape.contains(
			"inqwise_indexer_report_execution_duration_seconds_total"
		));
	}
}
