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

		return new Parsed(
			uid.substring(0, separatorIndex),
			Integer.parseInt(uid.substring(separatorIndex + 1), 36)
		);
	}

	public record Parsed(String prefix, Integer id) {
	}
}
