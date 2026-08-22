package com.inqwise.indexer.example.hn.actions;

import java.util.Map;

import com.inqwise.indexer.example.hn.model.HackerNewsDocumentConstants;
import com.inqwise.indexer.service.action.TargetActionPreparationProvider;
import com.inqwise.indexer.service.action.TargetActionPreparer;

public final class HackerNewsTargetActionPreparationProvider
	implements TargetActionPreparationProvider {
	@Override
	public String id() {
		return "hacker-news";
	}

	@Override
	public Map<String, TargetActionPreparer> preparers() {
		return Map.of(
			HackerNewsDocumentConstants.TARGET_NAME,
			new HackerNewsTargetActionPreparer()
		);
	}
}
