package com.inqwise.indexer.node.application.monitoring;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import com.inqwise.indexer.query.monitoring.ReportExecutionOutcome;
import com.inqwise.indexer.query.monitoring.ReportOperationalMonitor;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

public final class MicrometerReportOperationalMonitor
	implements ReportOperationalMonitor {
	public static final String EXECUTIONS = "inqwise.indexer.report.executions";
	public static final String ACTIVE = "inqwise.indexer.report.executions.active";
	public static final String DURATION = "inqwise.indexer.report.execution.duration.seconds";
	public static final String UNKNOWN_REPORT = "unknown";

	private final MeterRegistry registry;
	private final Set<String> reportNames;
	private final Map<String, AtomicInteger> active = new LinkedHashMap<>();

	public MicrometerReportOperationalMonitor(
		MeterRegistry registry,
		Set<String> reportNames
	) {
		this.registry = Objects.requireNonNull(registry, "registry");
		this.reportNames = Set.copyOf(Objects.requireNonNull(reportNames, "reportNames"));
		for (String reportName : this.reportNames) {
			register(reportName);
		}
		if (!active.containsKey(UNKNOWN_REPORT)) {
			register(UNKNOWN_REPORT);
		}
	}

	@Override
	public void executionStarted(String reportName) {
		active.get(label(reportName)).incrementAndGet();
	}

	@Override
	public void executionCompleted(
		String reportName,
		ReportExecutionOutcome outcome,
		long durationNanos
	) {
		String label = label(reportName);
		active.get(label).updateAndGet(value -> Math.max(0, value - 1));
		registry.counter(
			EXECUTIONS,
			"report", label,
			"outcome", outcome.name().toLowerCase(Locale.ROOT)
		).increment();
		registry.counter(DURATION, "report", label).increment(
			Math.max(0L, durationNanos) / 1_000_000_000.0
		);
	}

	private void register(String reportName) {
		AtomicInteger current = new AtomicInteger();
		active.put(reportName, current);
		Gauge.builder(ACTIVE, current, AtomicInteger::get)
			.description("Report executions currently active in this process")
			.tag("report", reportName)
			.register(registry);
		for (ReportExecutionOutcome outcome : ReportExecutionOutcome.values()) {
			registry.counter(
				EXECUTIONS,
				"report", reportName,
				"outcome", outcome.name().toLowerCase(Locale.ROOT)
			);
		}
		registry.counter(DURATION, "report", reportName);
	}

	private String label(String reportName) {
		return reportName != null && reportNames.contains(reportName)
			? reportName
			: UNKNOWN_REPORT;
	}
}
