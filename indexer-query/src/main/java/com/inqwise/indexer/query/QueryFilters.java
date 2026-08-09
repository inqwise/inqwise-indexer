package com.inqwise.indexer.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class QueryFilters {
	private QueryFilters() {
	}

	public static QueryFilter all() {
		return AllQueryFilter.INSTANCE;
	}

	public static QueryFilter allOf(QueryFilter... filters) {
		return allOf(List.of(filters));
	}

	public static QueryFilter allOf(List<QueryFilter> filters) {
		Objects.requireNonNull(filters, "filters");
		List<QueryFilter> combined = new ArrayList<>();
		for (QueryFilter filter : filters) {
			Objects.requireNonNull(filter, "filter");
			if (filter == AllQueryFilter.INSTANCE) {
				continue;
			}
			if (filter instanceof AllOfQueryFilter allOf) {
				combined.addAll(allOf.filters());
			} else {
				combined.add(filter);
			}
		}
		if (combined.isEmpty()) {
			return all();
		}
		if (combined.size() == 1) {
			return combined.getFirst();
		}
		return AllOfQueryFilter.builder().withFilters(combined).build();
	}
}
