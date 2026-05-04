package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RemoveDocumentActionItem implements IndexerActionItem {
  public static final String TYPE = "type";
  public static final String TARGET_NAME = "target_name";
  public static final String UID = "uid";

  private final String targetName;
  private final String uid;

  public RemoveDocumentActionItem(JsonObject json) {
    this(
      json.getString(TARGET_NAME),
      json.getString(UID)
    );
  }

  private RemoveDocumentActionItem(String targetName, String uid) {
    this.targetName = targetName;
    this.uid = Objects.requireNonNull(uid, "uid");
  }

  @Override
  public IndexerActionType getActionType() {
    return IndexerActionType.REMOVE_DOCUMENT;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(TYPE, getActionType().name())
      .put(UID, uid);

    if (targetName != null) {
      json.put(TARGET_NAME, targetName);
    }

    return json;
  }

  public String getTargetName() {
    return targetName;
  }

  public String getUid() {
    return uid;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private String targetName;
    private String uid;

    private Builder() {
    }

    public Builder withTargetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    public Builder withUid(String uid) {
      this.uid = uid;
      return this;
    }

    public RemoveDocumentActionItem build() {
      return new RemoveDocumentActionItem(targetName, uid);
    }
  }
}
