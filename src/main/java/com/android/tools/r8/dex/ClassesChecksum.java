package com.android.tools.r8.dex;

import com.android.tools.r8.graph.DexString;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Comparator;
import java.util.Map;

public class ClassesChecksum {

  private static final char PREFIX_CHAR0 = '~';
  private static final char PREFIX_CHAR1 = '~';
  private static final char PREFIX_CHAR2 = '~';


  // private final JsonObject dictionary;
  Map<String, Long> dictionary = Maps.newHashMap();

  /**
   * Checksum to be inserted.
   */
  public ClassesChecksum() {
  }

  private ClassesChecksum(JsonObject json) {
    json.entrySet().forEach(entry ->
        dictionary.put(entry.getKey(), Long.parseLong(entry.getValue().getAsString(), 16)));
  }

  public synchronized ClassesChecksum addChecksum(String classDescriptor, Long crc) {
    dictionary.put(classDescriptor, crc);
    return this;
  }

  public synchronized ImmutableMap<String, Long> getChecksums() {
    return ImmutableMap.copyOf(dictionary);
  }

  public synchronized ClassesChecksum merge(ClassesChecksum other) {
    if (other != null) {
      other.dictionary.entrySet().stream().forEach(entry -> this.dictionary.put(
          entry.getKey(), entry.getValue()));
    }
    return this;
  }

  @Override
  public synchronized String toString() {
    // In order to make printing of markers deterministic we sort the entries by key.
    final JsonObject sortedJson = new JsonObject();
    dictionary.entrySet()
        .stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(
            entry -> sortedJson.addProperty(entry.getKey(), Long.toHexString(entry.getValue())));
    return "" + PREFIX_CHAR0 + PREFIX_CHAR1 + PREFIX_CHAR2 + sortedJson;
  }

  // Try to parse str as a marker.
  // Returns null if parsing fails.
  public static ClassesChecksum parse(DexString dexString) {
    if (dexString.size > 2
        && dexString.content[0] == PREFIX_CHAR0
        && dexString.content[1] == PREFIX_CHAR1
        && dexString.content[2] == PREFIX_CHAR2) {
      String str = dexString.toString().substring(3);
      try {
        JsonElement result = new JsonParser().parse(str);
        if (result.isJsonObject()) {
          return new ClassesChecksum(result.getAsJsonObject());
        }
      } catch (JsonSyntaxException ignored) {}
    }
    return null;
  }
}
