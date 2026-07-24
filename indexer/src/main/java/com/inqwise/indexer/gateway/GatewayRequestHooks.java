package com.inqwise.indexer.gateway;

import java.util.Objects;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

public class GatewayRequestHooks {
	public static final GatewayRequestHooks NOOP = new GatewayRequestHooks();

	private final GatewayAuthenticator authenticator;
	private final GatewayAuthorizer authorizer;
	private final GatewayRateLimiter rateLimiter;
	private final GatewayAuditSink auditSink;

	public GatewayRequestHooks() {
		this(
			GatewayAuthenticator.ANONYMOUS,
			GatewayAuthorizer.ALLOW_ALL,
			GatewayRateLimiter.UNLIMITED,
			GatewayAuditSink.NOOP
		);
	}

	public GatewayRequestHooks(
		GatewayAuthenticator authenticator,
		GatewayAuthorizer authorizer,
		GatewayRateLimiter rateLimiter,
		GatewayAuditSink auditSink
	) {
		this.authenticator = Objects.requireNonNull(authenticator, "authenticator");
		this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
		this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
		this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
	}

	public Future<GatewayPrincipal> authenticate(
		RoutingContext context,
		GatewayRequestMetadata request
	) {
		return authenticator.authenticate(context, request);
	}

	public Future<Void> authorize(
		GatewayRequestMetadata request,
		GatewayPrincipal principal
	) {
		return authorizer.authorize(request, principal);
	}

	public Future<Void> rateLimit(
		GatewayRequestMetadata request,
		GatewayPrincipal principal
	) {
		return rateLimiter.acquire(request, principal);
	}

	public Future<Void> audit(GatewayAuditEvent event) {
		return auditSink.record(event);
	}
}
