package com.mesosphere.sdk.offer.taskdata;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.apache.mesos.Protos.Environment;

/**
 * Utilities relating to environment variable manipulation.
 */
public class EnvUtils {

    private static final Pattern ENVVAR_INVALID_CHARS = Pattern.compile("[^a-zA-Z0-9_]");

    private EnvUtils() {
        // do not instantiate
    }

    /**
     * Returns a Map representation of the provided {@link Environment}.
     * In the event of duplicate labels, the last duplicate wins.
     * This is the inverse of {@link #toProto(Map)}.
     */
    public static Map<String, String> toMap(Environment environment) {
        // sort labels alphabetically for convenience in debugging/logging:
        Map<String, String> map = new TreeMap<>();
        for (Environment.Variable variable : environment.getVariablesList()) {
            map.put(variable.getName(), variable.getValue());
        }
        return map;
    }

    /**
     * Returns a Protobuf representation of the provided {@link Map}.
     */
    static Environment toProto(Map<String, String> environmentMap) {
        Environment.Builder envBuilder = Environment.newBuilder();
        for (Map.Entry<String, String> entry : environmentMap.entrySet()) {
            envBuilder.addVariablesBuilder()
                .setName(entry.getKey())
                .setValue(entry.getValue());
        }
        return envBuilder.build();
    }

    // TODO(nickbp): SCHEDULER ONLY:

    /**
     * Adds or updates the provided environment variable entry in the provided command builder.
     */
    private static Environment withEnvVar(Environment environment, String key, String value) {
        Map<String, String> envMap = toMap(environment);
        envMap.put(key, value);
        return toProto(envMap);
    }

    /**
     * Returns the value of the provided environment variable, or an empty {@link Optional} if no matching environment
     * variable was found.
     */
    private static Optional<String> getEnvVar(Environment environment, String key) {
        return environment.getVariablesList().stream()
                .filter(v -> v.getName().equals(key))
                .map(v -> v.getValue())
                .findFirst();
    }

    /**
     * Converts the provided string to a conventional environment variable name, consisting of numbers, uppercase
     * letters, and underscores. Strictly speaking, lowercase characters are not invalid, but this avoids them to follow
     * convention.
     *
     * For example: {@code hello.There999!} => {@code HELLO_THERE999_}
     */
    public static String toEnvName(String str) {
        return ENVVAR_INVALID_CHARS.matcher(str.toUpperCase()).replaceAll("_");
    }

    /**
     * Returns a environment variable-style rendering of the specified port.
     */
    static String getPortEnvName(String portName, Optional<String> customEnvKey) {
        String draftEnvName = customEnvKey.isPresent()
                ? customEnvKey.get() // use custom name as-is
                : EnvConstants.PORT_NAME_TASKENV_PREFIX + portName; // PORT_[name]
        // Envvar should be uppercased with invalid characters replaced with underscores:
        return toEnvName(draftEnvName);
    }
}
