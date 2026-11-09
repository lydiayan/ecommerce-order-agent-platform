package com.css.mallorderagent.skill.source;

import com.css.mallorderagent.config.OrderAgentProperties;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SkillSourcePolicy {

    private final OrderAgentProperties.SkillProperties properties;

    public SkillSourcePolicy(OrderAgentProperties properties) {
        this.properties = properties.getSkill();
    }

    public URI validate(URI uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("skill source must use HTTPS");
        }
        if (uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null
                || uri.getRawQuery() != null) {
            throw new IllegalArgumentException("skill source URL is invalid");
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            throw new IllegalArgumentException("skill source only supports HTTPS port 443");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        Set<String> allowed = properties.getAllowedHosts().stream()
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());
        if (!allowed.contains(host)) {
            throw new IllegalArgumentException("skill source host is not allowlisted: " + host);
        }
        if (!properties.isAllowPrivateHosts()) {
            assertPublicAddresses(host);
        }
        return uri.normalize();
    }

    private static void assertPublicAddresses(String host) {
        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                        || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                        || address.isMulticastAddress() || isUniqueLocalIpv6(address)) {
                    throw new IllegalArgumentException("skill source resolves to a non-public address");
                }
            }
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("skill source host cannot be resolved", e);
        }
    }

    private static boolean isUniqueLocalIpv6(InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return false;
        }
        int first = Byte.toUnsignedInt(address.getAddress()[0]);
        return (first & 0xFE) == 0xFC;
    }
}
