package com.inqwise.indexer;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class IndexActionRequest {
  public static final String TYPE = "type";
  public static final String TARGET_NAME = "target_name";
  public static final String UID = "uid";
  public static final String DOCUMENT = "document";

  private final IndexActionType type;
  private final String targetName;
  private final String uid;
  private final JsonObject document;

  public IndexActionRequest(JsonObject json) {
    this(
      IndexActionType.valueOf(json.getString(TYPE)),
      json.getString(TARGET_NAME),
      json.getString(UID),
      json.getJsonObject(DOCUMENT, new JsonObject())
    );
  }

  private IndexActionRequest(IndexActionType type, String targetName, String uid, JsonObject document) {
    this.type = Objects.requireNonNull(type, "type");
    this.targetName = targetName;
    this.uid = Objects.requireNonNull(uid, "uid");
    this.document = document == null ? new JsonObject() : document.copy();
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject()
      .put(TYPE, type.name())
      .put(UID, uid)
      .put(DOCUMENT, document.copy());

    if (targetName != null) {
      json.put(TARGET_NAME, targetName);
    }

    return json;
  }

  public IndexActionType getType() {
    return type;
  }

  public String getTargetName() {
    return targetName;
  }

  public String getUid() {
    return uid;
  }

  public JsonObject getDocument() {
    return document.copy();
  }

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private IndexActionType type;
    private String targetName;
    private String uid;
    private JsonObject document;

    private Builder() {
    }

    public Builder withType(IndexActionType type) {
      this.type = type;
      return this;
    }

    public Builder withTargetName(String targetName) {
      this.targetName = targetName;
      return this;
    }

    public Builder withUid(String uid) {
      this.uid = uid;
      return this;
    }

    public Builder withDocument(JsonObject document) {
      this.document = document;
      return this;
    }

    public IndexActionRequest build() {
      return new IndexActionRequest(type, targetName, uid, document);
    }
  }
}
