package com.inqwise.indexer.catalog.targets;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;

public class TargetPeriodResolver {
	public TargetPeriod resolve(TargetPeriodStrategy strategy, Instant timestamp) {
		TargetPeriodStrategy resolvedStrategy = strategy == null
			? TargetPeriodStrategy.NONE
			: strategy;

		if (resolvedStrategy == TargetPeriodStrategy.NONE) {
			return TargetPeriod.builder()
				.withStrategy(TargetPeriodStrategy.NONE)
				.build();
		}

		if (timestamp == null) {
			throw new NullPointerException("timestamp");
		}

		LocalDate date = timestamp.atZone(ZoneOffset.UTC).toLocalDate();
		return switch (resolvedStrategy) {
			case NONE -> TargetPeriod.builder()
				.withStrategy(TargetPeriodStrategy.NONE)
				.build();
			case MONTHLY -> monthly(date);
			case HALF_YEARLY -> halfYearly(date);
			case YEARLY -> yearly(date);
		};
	}

	private TargetPeriod monthly(LocalDate date) {
		LocalDate start = LocalDate.of(date.getYear(), date.getMonth(), 1);
		return TargetPeriod.builder()
			.withStrategy(TargetPeriodStrategy.MONTHLY)
			.withKey("%04d-%02d".formatted(start.getYear(), start.getMonthValue()))
			.withStartInclusive(start.atStartOfDay().toInstant(ZoneOffset.UTC))
			.withEndExclusive(start.plusMonths(1).atStartOfDay().toInstant(ZoneOffset.UTC))
			.build();
	}

	private TargetPeriod halfYearly(LocalDate date) {
		int half = date.getMonthValue() <= Month.JUNE.getValue() ? 1 : 2;
		Month startMonth = half == 1 ? Month.JANUARY : Month.JULY;
		LocalDate start = LocalDate.of(date.getYear(), startMonth, 1);
		return TargetPeriod.builder()
			.withStrategy(TargetPeriodStrategy.HALF_YEARLY)
			.withKey("%04d-h%d".formatted(start.getYear(), half))
			.withStartInclusive(start.atStartOfDay().toInstant(ZoneOffset.UTC))
			.withEndExclusive(start.plusMonths(6).atStartOfDay().toInstant(ZoneOffset.UTC))
			.build();
	}

	private TargetPeriod yearly(LocalDate date) {
		LocalDate start = LocalDate.of(date.getYear(), Month.JANUARY, 1);
		return TargetPeriod.builder()
			.withStrategy(TargetPeriodStrategy.YEARLY)
			.withKey("%04d".formatted(start.getYear()))
			.withStartInclusive(start.atStartOfDay().toInstant(ZoneOffset.UTC))
			.withEndExclusive(start.plusYears(1).atStartOfDay().toInstant(ZoneOffset.UTC))
			.build();
	}
}
