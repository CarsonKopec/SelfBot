package com.github.imagineforgee.selfbotlib.commands;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandArgs {

    private final List<String> positional = new ArrayList<>();
    private final Map<String, String> keyValueArgs = new HashMap<>();
    private final Set<String> flags = new HashSet<>();

    private static final Pattern ARG_PATTERN = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    public CommandArgs(String input) {
        Matcher matcher = ARG_PATTERN.matcher(input);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (token == null) token = matcher.group(2);
            if (token == null) token = matcher.group(3);

            if (token == null) continue;
            parseToken(token);
        }
    }

    private void parseToken(String token) {
        if (token.startsWith("--")) {
            flags.add(token.substring(2).toLowerCase());
        } else if (token.contains("=")) {
            String[] parts = token.split("=", 2);
            keyValueArgs.put(parts[0].toLowerCase(), parts[1]);
        } else {
            positional.add(token);
        }
    }

    public String get(int index) {
        return index < positional.size() ? positional.get(index) : null;
    }

    public String getAllJoinedFrom(int index) {
        if (index >= positional.size()) return "";
        return String.join(" ", positional.subList(index, positional.size()));
    }

    public String getKey(String key) {
        return keyValueArgs.getOrDefault(key.toLowerCase(), null);
    }

    public boolean getFlag(String flagName) {
        return flags.contains(flagName.toLowerCase());
    }

    public boolean getBool(String key, boolean defaultVal) {
        String val = getKey(key);
        return val == null ? defaultVal : val.equalsIgnoreCase("true");
    }

    public int getInt(String key, int defaultVal) {
        try {
            return Integer.parseInt(getKey(key));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public List<String> getPositional() {
        return Collections.unmodifiableList(positional);
    }

    public Map<String, String> getKeyValueMap() {
        return Collections.unmodifiableMap(keyValueArgs);
    }

    public Set<String> getFlags() {
        return Collections.unmodifiableSet(flags);
    }
}

