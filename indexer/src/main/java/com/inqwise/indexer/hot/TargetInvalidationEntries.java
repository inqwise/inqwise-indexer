package com.inqwise.indexer.hot;

import java.util.List;
import java.util.Objects;

public record TargetInvalidationEntries(
	List<TargetInvalidationEntry> entries,
	boolean truncated
) {
	public TargetInvalidationEntries {
		entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
	}
}
