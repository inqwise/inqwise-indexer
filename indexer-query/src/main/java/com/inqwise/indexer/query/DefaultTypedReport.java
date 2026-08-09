package com.inqwise.indexer.query;

import java.util.Objects;

public final class DefaultTypedReport<Q, R> implements TypedReport<Q, R> {
	private final String name;
	private final ReportRequestCodec<Q> requestCodec;
	private final ReportResultCodec<R> resultCodec;

	private DefaultTypedReport(Builder<Q, R> builder) {
		name = builder.name;
		requestCodec = builder.requestCodec;
		resultCodec = builder.resultCodec;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public ReportRequestCodec<Q> requestCodec() {
		return requestCodec;
	}

	@Override
	public ReportResultCodec<R> resultCodec() {
		return resultCodec;
	}

	public static <Q, R> Builder<Q, R> builder() {
		return new Builder<>();
	}

	public static final class Builder<Q, R> {
		private String name;
		private ReportRequestCodec<Q> requestCodec;
		private ReportResultCodec<R> resultCodec;

		private Builder() {
		}

		public Builder<Q, R> withName(String value) {
			name = value;
			return this;
		}

		public Builder<Q, R> withRequestCodec(ReportRequestCodec<Q> value) {
			requestCodec = value;
			return this;
		}

		public Builder<Q, R> withResultCodec(ReportResultCodec<R> value) {
			resultCodec = value;
			return this;
		}

		public DefaultTypedReport<Q, R> build() {
			if (name == null || name.isBlank()) {
				throw new IllegalArgumentException("name must not be blank");
			}
			Objects.requireNonNull(requestCodec, "requestCodec");
			Objects.requireNonNull(resultCodec, "resultCodec");
			return new DefaultTypedReport<>(this);
		}
	}
}
