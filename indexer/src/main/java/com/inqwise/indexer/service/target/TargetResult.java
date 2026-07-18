package com.inqwise.indexer.service.target;

import java.util.Objects;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class TargetResult {
	private TargetView target;

	public TargetResult() {
	}

	public TargetResult(JsonObject json) {
		JsonObject targetJson = json.getJsonObject("target");
		target = targetJson == null ? null : new TargetView(targetJson);
	}

	public JsonObject toJson() {
		return new JsonObject().put("target", target == null ? null : target.toJson());
	}

	public TargetView getTarget() {
		return target;
	}

	public TargetResult setTarget(TargetView value) {
		target = value;
		return this;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {
		private TargetView target;

		private Builder() {
		}

		public Builder withTarget(TargetView value) {
			target = value;
			return this;
		}

		public TargetResult build() {
			return new TargetResult().setTarget(Objects.requireNonNull(target, "target"));
		}
	}
}
