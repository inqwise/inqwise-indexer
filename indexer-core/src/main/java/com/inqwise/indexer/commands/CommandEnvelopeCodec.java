package com.inqwise.indexer.commands;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public final class CommandEnvelopeCodec {
	public Buffer encode(CommandEnvelope envelope) {
		Objects.requireNonNull(envelope, "envelope");
		return Buffer.buffer(envelope.toJson().encode(), StandardCharsets.UTF_8.name());
	}

	public CommandEnvelope decode(Buffer encoded) {
		Objects.requireNonNull(encoded, "encoded");
		return CommandEnvelope.fromJson(
			new JsonObject(encoded.toString(StandardCharsets.UTF_8.name()))
		);
	}
}
