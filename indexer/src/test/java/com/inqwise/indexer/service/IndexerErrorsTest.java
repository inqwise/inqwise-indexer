package com.inqwise.indexer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.inqwise.indexer.catalog.indexers.IndexerCatalogConflictException;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogNotFoundException;
import com.inqwise.indexer.catalog.targets.TargetCatalogConflictException;
import com.inqwise.indexer.catalog.targets.TargetCatalogNotFoundException;
import com.inqwise.indexer.catalog.targets.TargetDefinitionNotFoundException;
import com.inqwise.indexer.errors.IndexerErrorCodes;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.publication.IndexPublicationConflictException;
import com.inqwise.indexer.publication.IndexPublicationNotFoundException;

import org.junit.jupiter.api.Test;

class IndexerErrorsTest {
	@Test
	void normalizesTypedCatalogNotFoundErrors() {
		var targetTicket = IndexerErrors.normalize(new TargetCatalogNotFoundException(10));
		var indexerTicket = IndexerErrors.normalize(new IndexerCatalogNotFoundException(20));

		assertEquals(IndexerErrorCodes.NotFound, targetTicket.getError());
		assertEquals(404, targetTicket.getStatus());
		assertEquals("Target not found: 10", targetTicket.getErrorDetails());
		assertEquals(IndexerErrorCodes.NotFound, indexerTicket.getError());
		assertEquals(404, indexerTicket.getStatus());
		assertEquals("Indexer not found: 20", indexerTicket.getErrorDetails());
	}

	@Test
	void normalizesTypedCatalogConflictErrors() {
		var targetTicket = IndexerErrors.normalize(
			new TargetCatalogConflictException(10, "target is not active")
		);
		var indexerTicket = IndexerErrors.normalize(
			new IndexerCatalogConflictException(20, "indexer is already active")
		);

		assertEquals(IndexerErrorCodes.Conflict, targetTicket.getError());
		assertEquals(409, targetTicket.getStatus());
		assertEquals(
			"Target conflict for id 10: target is not active",
			targetTicket.getErrorDetails()
		);
		assertEquals(IndexerErrorCodes.Conflict, indexerTicket.getError());
		assertEquals(409, indexerTicket.getStatus());
		assertEquals(
			"Indexer conflict for id 20: indexer is already active",
			indexerTicket.getErrorDetails()
		);
	}

	@Test
	void normalizesTypedPublicationErrors() {
		var missingTicket = IndexerErrors.normalize(
			IndexPublicationNotFoundException.publicationByIndexer(20)
		);
		var conflictTicket = IndexerErrors.normalize(
			IndexPublicationConflictException.indexer(20, "index is not ready")
		);

		assertEquals(IndexerErrorCodes.NotFound, missingTicket.getError());
		assertEquals(404, missingTicket.getStatus());
		assertEquals(
			"Publication not found for indexer: 20",
			missingTicket.getErrorDetails()
		);
		assertEquals(IndexerErrorCodes.Conflict, conflictTicket.getError());
		assertEquals(409, conflictTicket.getStatus());
		assertEquals(
			"Indexer publication conflict for id 20: index is not ready",
			conflictTicket.getErrorDetails()
		);
	}

	@Test
	void normalizesMissingTargetDefinition() {
		var ticket = IndexerErrors.normalize(new TargetDefinitionNotFoundException("customers"));

		assertEquals(IndexerErrorCodes.NotFound, ticket.getError());
		assertEquals(404, ticket.getStatus());
		assertEquals("Target definition not found by name: customers", ticket.getErrorDetails());
	}

	@Test
	void normalizesRetryableStaleState() {
		var ticket = IndexerErrors.normalize(new RetryableStaleStateException("metadata changed"));

		assertEquals(IndexerErrorCodes.RetryableStaleState, ticket.getError());
		assertEquals(IndexerErrorCodes.GROUP, ticket.getErrorGroup());
		assertEquals(503, ticket.getStatus());
		assertEquals("metadata changed", ticket.getErrorDetails());
	}

	@Test
	void retryableStaleStateProvidesTicket() {
		var ticket = new RetryableStaleStateException("retry from fresh snapshot").toErrorTicket();

		assertEquals(IndexerErrorCodes.RetryableStaleState, ticket.getError());
		assertEquals(IndexerErrorCodes.GROUP, ticket.getErrorGroup());
		assertEquals(503, ticket.getStatus());
		assertEquals("retry from fresh snapshot", ticket.getErrorDetails());
	}
}
