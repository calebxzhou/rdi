#!/bin/bash
set -euo pipefail

: "${START_PARAMS:?START_PARAMS env is required}"


#ls -la /opt/server >&2
chown -R rdi:rdi /home/rdi /opt/server /data
# Get the Docker host IP (default gateway)
HOST_IP=$(ip route | grep default | awk '{print $3}')
echo "Detected Docker host IP (gateway): ${HOST_IP}" >&2

# Also resolve the IPv4 address of host.docker.internal if available (Docker Desktop uses a different IP)
HOST_DOCKER_INTERNAL_IP=""
if getent ahostsv4 host.docker.internal >/dev/null 2>&1; then
    HOST_DOCKER_INTERNAL_IP=$(getent ahostsv4 host.docker.internal | awk 'NR == 1 {print $1}')
    echo "Detected host.docker.internal IP: ${HOST_DOCKER_INTERNAL_IP}" >&2
fi

# Whitelist configuration (comma-separated ip:port pairs)
# Default: host machine IP with port 65231
# Format: "ip1:port1,ip2:port2" e.g., "192.168.1.1:65231,10.0.0.1:443"
DEFAULT_WHITELIST="${HOST_IP}:65231"
if [ -n "${HOST_DOCKER_INTERNAL_IP}" ] && [ "${HOST_DOCKER_INTERNAL_IP}" != "${HOST_IP}" ]; then
    DEFAULT_WHITELIST="${DEFAULT_WHITELIST},${HOST_DOCKER_INTERNAL_IP}:65231"
fi
OUTGOING_WHITELIST="${OUTGOING_WHITELIST:-${DEFAULT_WHITELIST}}"
DEFAULT_HTTP_HOSTS="mojang.com,launchermeta.mojang.com,launcher.mojang.com,piston-meta.mojang.com,piston-data.mojang.com,minecraftforge.net,files.minecraftforge.net,maven.minecraftforge.net,neoforged.net,maven.neoforged.net"
OUTGOING_HTTP_HOSTS="${OUTGOING_HTTP_HOSTS:-${DEFAULT_HTTP_HOSTS}}"
OUTGOING_HTTP_PORTS="${OUTGOING_HTTP_PORTS:-80,443}"

allow_host_ports() {
    local host="$1"
    shift
    if [ -z "${host}" ]; then
        return
    fi
    local resolved_ips
    resolved_ips=$(getent ahostsv4 "${host}" | awk '{print $1}' | sort -u || true)
    if [ -z "${resolved_ips}" ]; then
        echo "Warning: unable to resolve ${host}, skip HTTP allowlist" >&2
        return
    fi
    while IFS= read -r ip; do
        [ -n "${ip}" ] || continue
        for port in "$@"; do
            [ -n "${port}" ] || continue
            echo "Allowing outgoing HTTP(S) to ${host} (${ip}:${port})" >&2
            iptables -A OUTPUT -d "${ip}" -p tcp --dport "${port}" -j ACCEPT
        done
    done <<< "${resolved_ips}"
}

# Restrict outgoing connections to whitelisted ip:port combinations only
echo "Applying firewall rules..." >&2

# Flush existing rules to be safe
iptables -F INPUT
iptables -F OUTPUT

# Allow all incoming connections
iptables -P INPUT ACCEPT

# Allow loopback (localhost)
iptables -A OUTPUT -o lo -j ACCEPT

# Allow established and related connections (allows replies to incoming connections)
iptables -A OUTPUT -m conntrack --ctstate RELATED,ESTABLISHED -j ACCEPT

# Allow outgoing connections to whitelisted ip:port combinations
IFS=',' read -ra WHITELIST_ENTRIES <<< "${OUTGOING_WHITELIST}"
for entry in "${WHITELIST_ENTRIES[@]}"; do
    entry=$(echo "${entry}" | xargs)
    if [ -n "${entry}" ]; then
        host="${entry%:*}"
        port="${entry##*:}"

        if [ -n "${host}" ] && [ -n "${port}" ]; then
            echo "Allowing outgoing to ${host}:${port}" >&2
            iptables -A OUTPUT -d "${host}" -p tcp --dport "${port}" -j ACCEPT
            iptables -A OUTPUT -d "${host}" -p udp --dport "${port}" -j ACCEPT
        fi
    fi
done

# Allow outbound HTTP(S) to selected Mojang/Forge/NeoForged hosts.
# Note: iptables is IP-based, so wildcard DNS suffixes must be expanded into concrete hosts before startup.
IFS=',' read -ra HTTP_PORT_ENTRIES <<< "${OUTGOING_HTTP_PORTS}"
IFS=',' read -ra HTTP_HOST_ENTRIES <<< "${OUTGOING_HTTP_HOSTS}"
for host in "${HTTP_HOST_ENTRIES[@]}"; do
    host=$(echo "${host}" | xargs)
    [ -n "${host}" ] || continue
    allow_host_ports "${host}" "${HTTP_PORT_ENTRIES[@]}"
done

# Allow DNS resolution (needed to resolve host.docker.internal and other hostnames)
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
iptables -A OUTPUT -p tcp --dport 53 -j ACCEPT

# Immediately reject non-whitelisted egress instead of silently dropping it.
# This prevents client code in container from waiting on long socket timeouts.
iptables -A OUTPUT -p tcp -j REJECT --reject-with tcp-reset
iptables -A OUTPUT -p udp -j REJECT --reject-with icmp-port-unreachable
iptables -A OUTPUT -j REJECT --reject-with icmp-proto-unreachable

# Keep DROP as fallback policy (normal traffic should already be handled above).
iptables -P OUTPUT DROP

# Drop to the non-root server user before launching Java
cd /opt/server
export HOME=/home/rdi
eval "JAVA_ARGS=(${START_PARAMS})"
exec gosu rdi java "${JAVA_ARGS[@]}"
