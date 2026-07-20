package com.inqwise.indexer.metadata;

import java.util.Objects;

public final class MetadataUid {
	private static final char SEPARATOR = '-';

	private MetadataUid() {
	}

	public static String toToken(String prefix, Integer id) {
		Objects.requireNonNull(prefix, "prefix");
		Objects.requireNonNull(id, "id");
		return prefix + SEPARATOR + Integer.toString(id, 36);
	}

	public static Parsed parse(String uid) {
		Objects.requireNonNull(uid, "uid");
		int separatorIndex = uid.lastIndexOf(SEPARATOR);
		if (separatorIndex <= 0 || separatorIndex == uid.length() - 1) {
			throw new IllegalArgumentException("Invalid metadata uid: " + uid);
		}

		return Parsed.builder()
			.withPrefix(uid.substring(0, separatorIndex))
			.withId(Integer.parseInt(uid.substring(separatorIndex + 1), 36))
			.build();
	}

	public record Parsed(String prefix, Integer id) {
		public static Builder builder() {
			return new Builder();
		}

		public static final class Builder {
			private String prefix;
			private Integer id;

			private Builder() {
			}

			public Builder withPrefix(String value) {
				prefix = value;
				return this;
			}

			public Builder withId(Integer value) {
				id = value;
				return this;
			}

			public Parsed build() {
				return new Parsed(
					Objects.requireNonNull(prefix, "prefix"),
					Objects.requireNonNull(id, "id")
				);
			}
		}
	}
}
