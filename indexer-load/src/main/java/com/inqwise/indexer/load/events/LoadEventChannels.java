package com.inqwise.indexer.load.events;

import com.inqwise.events.EventChannel;

public final class LoadEventChannels {
	public static final String LAZY_LIVE_WRITER_PREPARATION_CONFLICT_TYPE =
		"LAZY_LIVE_WRITER_PREPARATION_CONFLICT";

	public static final EventChannel<LazyLiveWriterPreparationConflictEvent>
		LAZY_LIVE_WRITER_PREPARATION_CONFLICT =
			EventChannel.<LazyLiveWriterPreparationConflictEvent>builder()
				.withName("indexer.load.operational")
				.withPayloadType(LazyLiveWriterPreparationConflictEvent.class)
				.build();

	private LoadEventChannels() {
	}
}
