package com.android.tools.r8.dex;

import com.android.tools.r8.graph.DexString;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.Comparator;
import java.util.Map;

public class ClassesChecksum {

  private static final char PREFIX_CHAR0 = '~';
  private static final char PREFIX_CHAR1 = '~';
  private static final char PREFIX_CHAR2 = '~';

  private Object2LongMap<String> dictionary = null;

  public ClassesChecksum() {
  }

  private void ensureMap() {
    if (dictionary == null) {
      dictionary = new Object2LongOpenHashMap<>();
    }
  }

  private void append(JsonObject json) {
    ensureMap();
    json.entrySet()
        .forEach(
            entry ->
                dictionary.put(entry.getKey(), Long.parseLong(entry.getValue().getAsString(), 16)));
  }

  public void addChecksum(String classDescriptor, long crc) {
    ensureMap();
    dictionary.put(classDescriptor, crc);
  }

  public Object2LongMap<String> getChecksums() {
    return dictionary;
  }

  public String toJsonString() {
    // In order to make printing of markers deterministic we sort the entries by key.
    final JsonObject sortedJson = new JsonObject();
    dictionary.object2LongEntrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(
            entry ->
                sortedJson.addProperty(entry.getKey(), Long.toString(entry.getLongValue(), 16)));
    return "" + PREFIX_CHAR0 + PREFIX_CHAR1 + PREFIX_CHAR2 + sortedJson;
  }

  // Try to parse the string as a marker and append its content if successful.
  public void tryParseAndAppend(DexString dexString) {
    if (dexString.size > 2
        && dexString.content[0] == PREFIX_CHAR0
        && dexString.content[1] == PREFIX_CHAR1
        && dexString.content[2] == PREFIX_CHAR2) {
      String str = dexString.toString().substring(3);
      try {
        JsonElement result = new JsonParser().parse(str);
        if (result.isJsonObject()) {
          append(result.getAsJsonObject());
        }
      } catch (JsonSyntaxException ignored) {}
    }
  }

  public static boolean preceedChecksumMarker(DexString string) {
    return string.size < 1 ||
        string.content[0] < PREFIX_CHAR0 ||
        string.size < 2 ||
        string.content[1] < PREFIX_CHAR1 ||
        string.size < 3 ||
        string.content[2] < PREFIX_CHAR2;
  }
}
