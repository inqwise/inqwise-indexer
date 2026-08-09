package com.inqwise.indexer.query.service;

import com.inqwise.errors.ErrorTicket;
import com.inqwise.indexer.query.InvalidReportRequestException;
import com.inqwise.indexer.query.ReportNotFoundException;
import com.inqwise.indexer.query.UnsupportedReportSchemaException;

final class QueryErrors {
	private QueryErrors() {
	}

	static ErrorTicket normalize(Throwable error) {
		if (error instanceof ErrorTicket ticket) {
			return ticket;
		}
		if (error instanceof ReportNotFoundException) {
			return ticket(QueryErrorCodes.ReportNotFound, error.getMessage());
		}
		if (error instanceof UnsupportedReportSchemaException) {
			return ticket(QueryErrorCodes.UnsupportedSchema, error.getMessage());
		}
		if (error instanceof InvalidReportRequestException) {
			return ticket(QueryErrorCodes.InvalidRequest, error.getMessage());
		}
		return ErrorTicket.propagate(error, builder -> builder.withError(QueryErrorCodes.InternalError));
	}

	private static ErrorTicket ticket(QueryErrorCodes code, String details) {
		return ErrorTicket.builder()
			.withError(code)
			.withDetails(details)
			.build();
	}
}
