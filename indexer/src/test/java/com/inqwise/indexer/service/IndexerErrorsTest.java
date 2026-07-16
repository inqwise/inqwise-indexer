package com.inqwise.indexer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.inqwise.indexer.catalog.targets.TargetDefinitionNotFoundException;
import com.inqwise.indexer.errors.IndexerErrorCodes;
import com.inqwise.indexer.errors.RetryableStaleStateException;

import org.junit.jupiter.api.Test;

class IndexerErrorsTest {
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
