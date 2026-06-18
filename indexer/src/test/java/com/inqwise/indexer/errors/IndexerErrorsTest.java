package com.inqwise.indexer.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IndexerErrorsTest {
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
