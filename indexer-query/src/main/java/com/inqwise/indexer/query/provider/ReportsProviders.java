package com.inqwise.indexer.query.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceLoader;

import com.inqwise.indexer.query.ReportCatalog;
import com.inqwise.indexer.query.ReportDefinition;
import com.inqwise.indexer.query.ReportDescriptor;
import com.inqwise.indexer.query.ReportNotFoundException;
import com.inqwise.indexer.query.PresentedReportDefinition;
import com.inqwise.indexer.query.presentation.ReportPresentation;
import com.inqwise.indexer.query.service.ReportsService;

import io.vertx.core.Future;

public final class ReportsProviders {
	private final ReportCatalog catalog;
	private final ReportsService service;

	private ReportsProviders(
		ReportCatalog catalog,
		ReportsService service
	) {
		this.catalog = catalog;
		this.service = service;
	}

	public static ReportsProviders load(ReportsProviderContext context) {
		List<ReportsProviderFactory> factories = ServiceLoader
			.load(ReportsProviderFactory.class)
			.stream()
			.map(ServiceLoader.Provider::get)
			.sorted(Comparator.comparing(factory -> requireId(factory.id())))
			.toList();
		return create(factories, context);
	}

	public static ReportsProviders create(
		Collection<? extends ReportsProviderFactory> factories,
		ReportsProviderContext context
	) {
		Objects.requireNonNull(factories, "factories");
		Objects.requireNonNull(context, "context");
		Map<String, ReportsProvider> providersById = new LinkedHashMap<>();
		Map<String, ReportDefinition<?, ?>> definitions = new LinkedHashMap<>();
		Map<String, ReportsService> servicesByReport = new LinkedHashMap<>();
		for (ReportsProviderFactory factory : factories) {
			Objects.requireNonNull(factory, "factory");
			String providerId = requireId(factory.id());
			ReportsProvider provider = Objects.requireNonNull(
				factory.create(context),
				"reports provider"
			);
			if (providersById.putIfAbsent(providerId, provider) != null) {
				throw new IllegalArgumentException(
					"Duplicate reports provider id: " + providerId
				);
			}
			for (ReportDescriptor descriptor : provider.catalog().descriptors()) {
				String reportName = descriptor.name();
				ReportDefinition<?, ?> definition = provider.catalog().find(reportName)
					.orElseThrow(() -> new IllegalStateException(
						"Report catalog descriptor has no definition: " + reportName
					));
				if (definitions.putIfAbsent(reportName, definition) != null) {
					throw new IllegalArgumentException(
						"Duplicate report name: " + reportName
					);
				}
				servicesByReport.put(reportName, provider.service());
			}
		}
		ReportCatalog catalog = new CompositeCatalog(definitions);
		ReportsService service = request -> {
			String reportName = request == null ? null : request.getReportName();
			ReportsService provider = servicesByReport.get(reportName);
			if (provider == null) {
				return Future.failedFuture(new ReportNotFoundException(reportName));
			}
			return provider.execute(request);
		};
		return new ReportsProviders(catalog, service);
	}

	public ReportCatalog catalog() {
		return catalog;
	}

	public ReportsService service() {
		return service;
	}

	private static String requireId(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("Reports provider id must not be blank");
		}
		return value;
	}

	private static final class CompositeCatalog implements ReportCatalog {
		private final Map<String, ReportDefinition<?, ?>> definitions;

		private CompositeCatalog(Map<String, ReportDefinition<?, ?>> definitions) {
			this.definitions = Map.copyOf(definitions);
		}

		@Override
		public Optional<ReportDefinition<?, ?>> find(String reportName) {
			return Optional.ofNullable(definitions.get(reportName));
		}

		@Override
		public Collection<ReportDescriptor> descriptors() {
			return definitions.values().stream()
				.map(ReportDefinition::descriptor)
				.sorted(Comparator.comparing(ReportDescriptor::name))
				.toList();
		}

		@Override
		public Collection<ReportPresentation> presentations() {
			List<ReportPresentation> presentations = new ArrayList<>();
			definitions.values().stream()
				.map(ReportDefinition::descriptor)
				.map(ReportDescriptor::name)
				.sorted()
				.forEach(reportName -> {
					ReportDefinition<?, ?> definition = definitions.get(reportName);
					if (definition instanceof PresentedReportDefinition<?, ?> presented) {
						presentations.add(new ReportPresentation(
							presented.presentation().toJson()
						));
					}
				});
			return List.copyOf(presentations);
		}
	}
}
