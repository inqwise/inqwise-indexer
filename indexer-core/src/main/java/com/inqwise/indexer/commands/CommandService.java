package com.inqwise.indexer.commands;

import io.vertx.core.Future;

public interface CommandService {
	/**
	 * Submits a command to the command transport.
	 * <p>
	 * The returned future represents producer-side acceptance of the command by
	 * this service, such as an in-memory dispatch or a durable queue/broker
	 * acknowledgement. It must not be interpreted as completion of the command's
	 * distributed workflow. Implementations that execute commands inline may only
	 * complete this future after the inline handler finishes, but that behavior is
	 * an implementation detail and not part of the generic service contract.
	 *
	 * @param command the command to submit
	 * @return a future completed when the command submission is accepted, or failed
	 * 	when the command cannot be accepted
	 */
	Future<Void> submit(Command command);
}
