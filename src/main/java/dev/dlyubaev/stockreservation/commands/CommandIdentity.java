package dev.dlyubaev.stockreservation.commands;

import java.util.Map;
import java.util.TreeMap;
import tools.jackson.databind.json.JsonMapper;

public final class CommandIdentity {
  private static final JsonMapper mapper = new JsonMapper();

  private CommandIdentity() {}

  public static String canonicalize(String operation, String target, Object validatedBody) {
    var body = new TreeMap<String, Object>(mapper.convertValue(validatedBody, Map.class));
    return mapper.writeValueAsString(
        new TreeMap<>(
            Map.of("method", "POST", "operation", operation, "target", target, "body", body)));
  }

  public static String targetOf(String identity) {
    return mapper.readTree(identity).get("target").asText();
  }
}
