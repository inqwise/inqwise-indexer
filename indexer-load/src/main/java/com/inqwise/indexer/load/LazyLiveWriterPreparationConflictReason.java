package com.inqwise.indexer.load;

public enum LazyLiveWriterPreparationConflictReason {
	ATTACH_LOST,
	VERSION_CONFLICT,
	LOAD_NOT_ELIGIBLE_AFTER_RELOAD,
	CLEANUP_FAILED
}
