package net.austizz.ultimatebankingsystem.bank.safebox.setup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

final class SafeDepositSetupMaps {
    private SafeDepositSetupMaps() {
    }

    static List<Map<String, Object>> mapList(Object raw) {
        if (!(raw instanceof List<?> rawList)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object entry : rawList) {
            if (entry instanceof Map<?, ?> rawMap) {
                Map<String, Object> map = stringKeyMap(rawMap);
                if (map != null) {
                    result.add(map);
                }
            }
        }
        return result;
    }

    static Map<String, Object> stringKeyMap(Map<?, ?> rawMap) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                return null;
            }
            map.put(key, entry.getValue());
        }
        return map;
    }

    static Set<String> newIdSet() {
        return new LinkedHashSet<>();
    }

    static OptionalInt optionalInt(Object value) {
        Integer parsed = integerObject(value);
        return parsed == null ? OptionalInt.empty() : OptionalInt.of(parsed);
    }

    static Integer integerObject(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static int integer(Object value, int fallback) {
        Integer parsed = integerObject(value);
        return parsed == null ? fallback : parsed;
    }

    static Boolean booleanObject(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = string(value).trim();
        if ("true".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw)) {
            return false;
        }
        return null;
    }

    static float floatValue(Object value, float fallback) {
        if (value instanceof Number number) {
            return number.floatValue();
        }
        try {
            return Float.parseFloat(string(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    static String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
