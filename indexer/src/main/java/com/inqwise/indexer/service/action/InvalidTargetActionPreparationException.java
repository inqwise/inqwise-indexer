package com.inqwise.indexer.service.action;

public final class InvalidTargetActionPreparationException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	public InvalidTargetActionPreparationException(String message) {
		super(message);
	}

	public InvalidTargetActionPreparationException(String message, Throwable cause) {
		super(message, cause);
	}
}
