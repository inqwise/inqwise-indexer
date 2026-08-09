package com.inqwise.indexer.example.hn.reports;

public enum HackerNewsAuthorOrder {
	TOTAL_SCORE("total_score"),
	STORY_COUNT("story_count"),
	MAX_SCORE("max_score"),
	LATEST_STORY("latest_story");

	private final String value;

	HackerNewsAuthorOrder(String value) {
		this.value = value;
	}

	public String value() {
		return value;
	}

	public static HackerNewsAuthorOrder parse(String value) {
		for (HackerNewsAuthorOrder order : values()) {
			if (order.value.equals(value)) {
				return order;
			}
		}
		throw new IllegalArgumentException("Unsupported author order: " + value);
	}
}
