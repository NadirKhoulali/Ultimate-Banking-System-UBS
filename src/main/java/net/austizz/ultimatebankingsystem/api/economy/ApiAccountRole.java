package net.austizz.ultimatebankingsystem.api.economy;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Set;

@ApiStatus.AvailableSince("2.1.0")
public enum ApiAccountRole {
    NONE(Set.of()),
    VIEW(Set.of(ApiAccountCapability.VIEW)),
    DEPOSIT(Set.of(ApiAccountCapability.VIEW, ApiAccountCapability.DEPOSIT)),
    WITHDRAW(Set.of(ApiAccountCapability.VIEW, ApiAccountCapability.DEPOSIT, ApiAccountCapability.WITHDRAW)),
    MANAGE(Set.of(ApiAccountCapability.VIEW, ApiAccountCapability.DEPOSIT,
            ApiAccountCapability.WITHDRAW, ApiAccountCapability.MANAGE)),
    OWNER(Set.of(ApiAccountCapability.VIEW, ApiAccountCapability.DEPOSIT,
            ApiAccountCapability.WITHDRAW, ApiAccountCapability.MANAGE));

    private final Set<ApiAccountCapability> capabilities;

    ApiAccountRole(Set<ApiAccountCapability> capabilities) {
        this.capabilities = Set.copyOf(capabilities);
    }

    public Set<ApiAccountCapability> capabilities() {
        return capabilities;
    }

    public static ApiAccountRole parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
