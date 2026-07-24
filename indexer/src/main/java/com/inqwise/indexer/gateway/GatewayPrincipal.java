package com.inqwise.indexer.gateway;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public final class GatewayPrincipal {
	private final String subject;
	private final String authenticationScheme;
	private final boolean authenticated;
	private final Set<String> roles;

	private GatewayPrincipal(
		String subject,
		String authenticationScheme,
		boolean authenticated,
		Set<String> roles
	) {
		this.subject = subject;
		this.authenticationScheme = authenticationScheme;
		this.authenticated = authenticated;
		this.roles = roles;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String subject() {
		return subject;
	}

	public String authenticationScheme() {
		return authenticationScheme;
	}

	public boolean authenticated() {
		return authenticated;
	}

	public Set<String> roles() {
		return roles;
	}

	public static final class Builder {
		private String subject;
		private String authenticationScheme;
		private boolean authenticated;
		private Set<String> roles = Set.of();

		private Builder() {
		}

		public Builder withSubject(String value) {
			subject = value;
			return this;
		}

		public Builder withAuthenticationScheme(String value) {
			authenticationScheme = value;
			return this;
		}

		public Builder withAuthenticated(boolean value) {
			authenticated = value;
			return this;
		}

		public Builder withRoles(Collection<String> value) {
			roles = Set.copyOf(Objects.requireNonNull(value, "value"));
			return this;
		}

		public GatewayPrincipal build() {
			requireText(subject, "subject");
			requireText(authenticationScheme, "authenticationScheme");
			roles.forEach(role -> requireText(role, "role"));
			return new GatewayPrincipal(
				subject,
				authenticationScheme,
				authenticated,
				Set.copyOf(roles)
			);
		}
	}

	private static void requireText(String value, String name) {
		Objects.requireNonNull(value, name);
		if (value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
