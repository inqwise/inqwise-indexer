detect_indexer_public_host() {
	if [ -n "${INDEXER_PUBLIC_HOST:-}" ]; then
		printf '%s\n' "$INDEXER_PUBLIC_HOST"
		return 0
	fi

	case "$(uname -s)" in
		Darwin)
			interface=$(route -n get default 2>/dev/null | awk '/interface:/{print $2; exit}')
			if [ -n "$interface" ]; then
				address=$(ipconfig getifaddr "$interface" 2>/dev/null || true)
				if [ -n "$address" ]; then
					printf '%s\n' "$address"
					return 0
				fi
			fi
			;;
		Linux)
			address=$(ip -4 route get 1.1.1.1 2>/dev/null \
				| awk '{for (i = 1; i <= NF; i++) if ($i == "src") {print $(i + 1); exit}}')
			if [ -n "$address" ]; then
				printf '%s\n' "$address"
				return 0
			fi
			;;
	esac

	printf '%s\n' "Unable to detect a routable host address; set INDEXER_PUBLIC_HOST" >&2
	return 1
}

indexer_env_value() {
	file=$1
	name=$2
	if [ ! -f "$file" ]; then
		return 0
	fi

	awk -v name="$name" '
		index($0, name "=") == 1 {
			value = substr($0, length(name) + 2)
		}
		END {
			if (value != "") print value
		}
	' "$file"
}

resolve_indexer_setting() {
	current=$1
	file=$2
	name=$3
	if [ -n "$current" ]; then
		printf '%s\n' "$current"
		return 0
	fi

	indexer_env_value "$file" "$name"
}
