package com.inqwise.indexer.example.hn.reports.local;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.inqwise.indexer.adapters.local.InMemoryIndexerDocumentStore;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummary;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryQuery;
import com.inqwise.indexer.example.hn.reports.HackerNewsAuthorSummaryQueryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsSourceFilter;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoriesReportDefinition;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryFilter;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryOrder;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryQuery;
import com.inqwise.indexer.example.hn.reports.HackerNewsStoryQueryResult;
import com.inqwise.indexer.example.hn.reports.HackerNewsStorySummary;
import com.inqwise.indexer.example.hn.model.HackerNewsDocument;
import com.inqwise.indexer.example.hn.model.HackerNewsDocumentCodec;
import com.inqwise.indexer.query.AllOfQueryFilter;
import com.inqwise.indexer.query.AllQueryFilter;
import com.inqwise.indexer.query.DocumentQueryExecution;
import com.inqwise.indexer.query.DocumentQueryProvider;
import com.inqwise.indexer.query.DocumentQueryResult;
import com.inqwise.indexer.query.QueryFilter;

import io.vertx.core.Future;

public final class LocalHackerNewsQueryProvider implements DocumentQueryProvider {
	private static final HackerNewsDocumentCodec DOCUMENT_CODEC =
		new HackerNewsDocumentCodec();

	private final InMemoryIndexerDocumentStore documents;

	public LocalHackerNewsQueryProvider(InMemoryIndexerDocumentStore documents) {
		this.documents = Objects.requireNonNull(documents, "documents");
	}

	@Override
	public Future<DocumentQueryResult> execute(DocumentQueryExecution execution) {
		Objects.requireNonNull(execution, "execution");
		if (!HackerNewsStoriesReportDefinition.SCHEMA.equals(execution.schema())) {
			return Future.failedFuture("Unsupported Hacker News schema: " + execution.schema());
		}

		validateFilter(execution.filter());
		Map<Long, HackerNewsStorySummary> stories = stories(execution);
		if (execution.query() instanceof HackerNewsStoryQuery query) {
			return Future.succeededFuture(storyResult(execution, query, stories));
		}
		if (execution.query() instanceof HackerNewsAuthorSummaryQuery query) {
			return Future.succeededFuture(authorResult(execution, query, stories));
		}
		return Future.failedFuture(
			"Unsupported Hacker News query capability: " + execution.query().capability()
		);
	}

	private Map<Long, HackerNewsStorySummary> stories(DocumentQueryExecution execution) {
		Map<Long, HackerNewsStorySummary> stories = new LinkedHashMap<>();
		execution.indexes().forEach(index -> documents.documents(index.indexName())
			.values()
			.stream()
			.map(DOCUMENT_CODEC::decode)
			.filter(document -> matches(execution.filter(), document))
			.map(this::decodeStory)
			.filter(story -> withinPeriod(execution, story.time()))
			.forEach(story -> stories.merge(story.id(), story, this::preferred)));
		return stories;
	}

	private DocumentQueryResult storyResult(
		DocumentQueryExecution execution,
		HackerNewsStoryQuery query,
		Map<Long, HackerNewsStorySummary> stories
	) {
		List<HackerNewsStorySummary> page = stories.values().stream()
			.sorted(HackerNewsStoryOrder.COMPARATOR)
			.filter(story -> query.cursor() == null
				|| HackerNewsStoryOrder.after(story, query.cursor()))
			.limit((long) execution.limit() + 1)
			.toList();
		boolean hasMore = page.size() > execution.limit();
		return HackerNewsStoryQueryResult.builder()
			.withStories(page.stream()
				.limit(execution.limit())
				.toList())
			.withHasMore(hasMore)
			.withRequestFingerprint(query.requestFingerprint())
			.build();
	}

	private DocumentQueryResult authorResult(
		DocumentQueryExecution execution,
		HackerNewsAuthorSummaryQuery query,
		Map<Long, HackerNewsStorySummary> stories
	) {
		Map<String, HackerNewsAuthorSummary> authors = new LinkedHashMap<>();
		stories.values().stream()
			.filter(story -> story.author() != null && !story.author().isBlank())
			.forEach(story -> authors.merge(
				story.author(),
				summary(story),
				this::merge
			));
		return HackerNewsAuthorSummaryQueryResult.builder()
			.withOrderBy(query.orderBy())
			.withAuthors(authors.values().stream()
				.sorted(HackerNewsAuthorSummary.comparator(query.orderBy()))
				.limit(execution.limit())
				.toList())
			.build();
	}

	private HackerNewsAuthorSummary summary(HackerNewsStorySummary story) {
		return HackerNewsAuthorSummary.builder()
			.withAuthor(story.author())
			.withStoryCount(1)
			.withTotalScore(story.score())
			.withMaxScore(story.score())
			.withLatestStoryTime(story.time())
			.build();
	}

	private HackerNewsAuthorSummary merge(
		HackerNewsAuthorSummary first,
		HackerNewsAuthorSummary second
	) {
		return HackerNewsAuthorSummary.builder()
			.withAuthor(first.author())
			.withStoryCount(Math.addExact(first.storyCount(), second.storyCount()))
			.withTotalScore(Math.addExact(first.totalScore(), second.totalScore()))
			.withMaxScore(Math.max(first.maxScore(), second.maxScore()))
			.withLatestStoryTime(first.latestStoryTime().isAfter(second.latestStoryTime())
				? first.latestStoryTime()
				: second.latestStoryTime())
			.build();
	}

	private void validateFilter(QueryFilter filter) {
		if (filter == AllQueryFilter.INSTANCE
			|| filter == HackerNewsSourceFilter.INSTANCE
			|| filter instanceof HackerNewsStoryFilter) {
			return;
		}
		if (filter instanceof AllOfQueryFilter allOf) {
			allOf.filters().forEach(this::validateFilter);
			return;
		}
		throw new IllegalArgumentException(
			"Unsupported Hacker News query filter: " + filter.getClass().getName()
		);
	}

	private boolean matches(QueryFilter filter, HackerNewsDocument document) {
		if (filter == AllQueryFilter.INSTANCE) {
			return true;
		}
		if (filter == HackerNewsSourceFilter.INSTANCE) {
			return HackerNewsSourceFilter.INSTANCE.source().equals(document.source());
		}
		if (filter instanceof HackerNewsStoryFilter story) {
			return story.itemType().equals(document.type())
				&& document.score() >= story.minimumScore();
		}
		if (filter instanceof AllOfQueryFilter allOf) {
			return allOf.filters().stream().allMatch(child -> matches(child, document));
		}
		throw new IllegalArgumentException(
			"Unsupported Hacker News query filter: " + filter.getClass().getName()
		);
	}

	private HackerNewsStorySummary decodeStory(HackerNewsDocument document) {
		return HackerNewsStorySummary.builder()
			.withId(document.id())
			.withAuthor(document.author())
			.withTitle(document.title())
			.withUrl(document.url())
			.withTime(Instant.ofEpochSecond(document.time()))
			.withScore(document.score())
			.withDescendants(document.descendants())
			.build();
	}

	private boolean withinPeriod(DocumentQueryExecution execution, Instant time) {
		return !time.isBefore(execution.fromInclusive())
			&& time.isBefore(execution.toExclusive());
	}

	private HackerNewsStorySummary preferred(
		HackerNewsStorySummary existing,
		HackerNewsStorySummary candidate
	) {
		return candidate.time().isBefore(existing.time()) ? existing : candidate;
	}
}
