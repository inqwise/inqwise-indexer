package com.inqwise.indexer.load;

import com.inqwise.events.EventChannel;

public final class LoadEventChannels {
	public static final String LAZY_LIVE_WRITER_PREPARATION_CONFLICT_TYPE =
		"LAZY_LIVE_WRITER_PREPARATION_CONFLICT";

	public static final EventChannel<LazyLiveWriterPreparationConflictEvent>
		LAZY_LIVE_WRITER_PREPARATION_CONFLICT = new EventChannel<>(
			"indexer.load.operational",
			LazyLiveWriterPreparationConflictEvent.class
		);

	private LoadEventChannels() {
	}
}
