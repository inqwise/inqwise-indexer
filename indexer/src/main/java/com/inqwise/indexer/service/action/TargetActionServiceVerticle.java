package com.inqwise.indexer.service.action;

import java.util.Objects;

import com.inqwise.indexer.hot.HotIndexActionsService;
import com.inqwise.indexer.monitoring.IndexerOperationalMonitor;
import com.inqwise.indexer.service.ServiceProxyVerticle;

import io.vertx.serviceproxy.ProxyHandler;

public class TargetActionServiceVerticle extends ServiceProxyVerticle<TargetActionService> {
	private final TargetActionService service;

	public TargetActionServiceVerticle(HotIndexActionsService hotActions) {
		this(hotActions, IndexerOperationalMonitor.NOOP);
	}

	public TargetActionServiceVerticle(
		HotIndexActionsService hotActions,
		IndexerOperationalMonitor monitor
	) {
		this.service = new MonitoredTargetActionService(
			new TargetActionServiceImpl(Objects.requireNonNull(hotActions, "hotActions")),
			Objects.requireNonNull(monitor, "monitor")
		);
	}

	@Override
	protected String address() {
		return TargetActionServices.DEFAULT_ADDRESS;
	}

	@Override
	protected TargetActionService service() {
		return service;
	}

	@Override
	protected ProxyHandler handler(TargetActionService service) {
		return new TargetActionServiceVertxProxyHandler(vertx, service);
	}
}
