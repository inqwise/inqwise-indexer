package com.inqwise.indexer.service;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogConflictException;
import com.inqwise.indexer.catalog.indexers.IndexerCatalogNotFoundException;
import com.inqwise.indexer.catalog.targets.TargetCatalogConflictException;
import com.inqwise.indexer.catalog.targets.TargetCatalogNotFoundException;
import com.inqwise.indexer.catalog.targets.TargetDefinitionNotFoundException;
import com.inqwise.indexer.commands.CommandFailure;
import com.inqwise.indexer.errors.IndexerErrorCodes;
import com.inqwise.indexer.errors.RetryableStaleStateException;
import com.inqwise.indexer.publication.IndexPublicationConflictException;
import com.inqwise.indexer.publication.IndexPublicationNotFoundException;

public final class IndexerErrors {
	private IndexerErrors() {
	}

	public static ErrorTicket invalidRequest(String message) {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.InvalidRequest)
			.withDetails(message)
			.build();
	}

	public static ErrorTicket notFound(String message) {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.NotFound)
			.withDetails(message)
			.build();
	}

	public static ErrorTicket conflict(String message) {
		return ErrorTicket.builder()
			.withError(IndexerErrorCodes.Conflict)
			.withDetails(message)
			.build();
	}

	public static ErrorTicket normalize(Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			return ticket;
		}

		if (error instanceof CommandFailure failure) {
			return fromCommandFailure(failure);
		}

		if (error instanceof RetryableStaleStateException staleState) {
			return staleState.toErrorTicket();
		}

		if (error instanceof TargetDefinitionNotFoundException missingDefinition) {
			return notFound(missingDefinition.getMessage());
		}

		if (error instanceof TargetCatalogNotFoundException
			|| error instanceof IndexerCatalogNotFoundException
			|| error instanceof IndexPublicationNotFoundException) {
			return notFound(error.getMessage());
		}

		if (error instanceof TargetCatalogConflictException
			|| error instanceof IndexerCatalogConflictException
			|| error instanceof IndexPublicationConflictException) {
			return conflict(error.getMessage());
		}

		return ErrorTicket.propagate(error, builder -> builder.withError(IndexerErrorCodes.InternalError));
	}

	private static ErrorTicket fromCommandFailure(CommandFailure failure) {
		IndexerErrorCodes code;
		if (failure.stableInvalid()) {
			code = IndexerErrorCodes.InvalidRoute;
		} else if (failure.retryable()) {
			code = IndexerErrorCodes.RouteRetryable;
		} else {
			code = IndexerErrorCodes.RouteFailed;
		}

		return ErrorTicket.builder()
			.withError(code)
			.withDetails(failure.getMessage())
			.build();
	}
}
