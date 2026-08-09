package com.inqwise.indexer.query;

public interface TypedReport<Q, R> {
	String name();

	ReportRequestCodec<Q> requestCodec();

	ReportResultCodec<R> resultCodec();
}
