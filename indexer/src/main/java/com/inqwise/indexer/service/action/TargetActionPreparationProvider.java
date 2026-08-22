package com.inqwise.indexer.service.action;

import java.util.Map;

public interface TargetActionPreparationProvider {
	String id();

	Map<String, TargetActionPreparer> preparers();
}
