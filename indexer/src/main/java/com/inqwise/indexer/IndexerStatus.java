package com.inqwise.indexer;

public enum IndexerStatus {
  NON_ACTIVE,
  STARTED,
  COMPLETED,
  DELETED;

  public boolean isActive() {
    return this == STARTED || this == COMPLETED;
  }
}
