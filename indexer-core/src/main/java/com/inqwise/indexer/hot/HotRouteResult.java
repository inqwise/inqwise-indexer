package com.inqwise.indexer.hot;

import java.util.List;
import java.util.Objects;

import com.inqwise.indexer.routing.RoutedIndexActions;

public sealed interface HotRouteResult permits HotRouteResult.Routed, HotRouteResult.Miss {
	record Routed(List<RoutedIndexActions> groups) implements HotRouteResult {
		public Routed {
			groups = List.copyOf(groups);
		}

		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private List<RoutedIndexActions> groups;

			private Builder() {
			}

			public Builder withGroups(List<RoutedIndexActions> value) {
				groups = value == null ? null : List.copyOf(value);
				return this;
			}

			public Routed build() {
				return new Routed(Objects.requireNonNull(groups, "groups"));
			}
		}
	}

	record Miss(String reason) implements HotRouteResult {
		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private String reason;

			private Builder() {
			}

			public Builder withReason(String value) {
				reason = value;
				return this;
			}

			public Miss build() {
				return new Miss(Objects.requireNonNull(reason, "reason"));
			}
		}
	}
}
