package com.inqwise.indexer.example.hn.reports;

import java.util.Comparator;

public final class HackerNewsStoryOrder {
	public static final Comparator<HackerNewsStorySummary> COMPARATOR = Comparator
		.comparingInt(HackerNewsStorySummary::score).reversed()
		.thenComparing(HackerNewsStorySummary::time, Comparator.reverseOrder())
		.thenComparingLong(HackerNewsStorySummary::id);

	private HackerNewsStoryOrder() {
	}

	public static boolean after(
		HackerNewsStorySummary story,
		HackerNewsStoriesCursor cursor
	) {
		if (story.score() != cursor.score()) {
			return story.score() < cursor.score();
		}
		if (!story.time().equals(cursor.time())) {
			return story.time().isBefore(cursor.time());
		}
		return story.id() > cursor.id();
	}
}
