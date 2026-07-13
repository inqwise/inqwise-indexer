package com.inqwise.indexer.load.repository;

import com.inqwise.indexer.metadata.IndexerRecord;

public record LoadPublication(
	IndexerRecord loadWriter,
	IndexerRecord candidate,
	IndexerRecord oldPublished
) {
}
