package com.inqwise.indexer.load.repository;

public record LoadPublication(
	LoadIndexerReference loadWriter,
	LoadIndexerReference candidate,
	LoadIndexerReference oldPublished
) {
}
