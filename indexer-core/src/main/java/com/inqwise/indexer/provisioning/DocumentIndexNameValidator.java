package com.inqwise.indexer.provisioning;

public final class DocumentIndexNameValidator {
	private DocumentIndexNameValidator() {
	}

	public static String requireConcrete(String indexName) {
		if (indexName == null) {
			throw new NullPointerException("indexName");
		}
		if (indexName.isBlank()
			|| indexName.equals("_all")
			|| indexName.contains("*")
			|| indexName.contains("?")
			|| indexName.contains(",")) {
			throw new IllegalArgumentException(
				"Document index name is not a concrete identity: " + indexName
			);
		}

		return indexName;
	}
}
