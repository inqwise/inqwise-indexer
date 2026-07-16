package com.inqwise.indexer.hot;

import java.util.List;

import com.inqwise.indexer.routing.RoutedIndexActions;

public sealed interface HotRouteResult permits HotRouteResult.Routed, HotRouteResult.Miss {
	record Routed(List<RoutedIndexActions> groups) implements HotRouteResult {
		public Routed {
			groups = List.copyOf(groups);
		}
	}

	record Miss(String reason) implements HotRouteResult {
	}
}
