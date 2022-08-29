// Copyright (c) 2017, the Rex project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.errors.DesugaredLibraryMismatchDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

/** Abstraction for hidden dex marker intended for the main dex file. */
public class Marker {

  public static final String VERSION = "version";
  public static final String MIN_API = "min-api";
  public static final String DESUGARED_LIBRARY_IDENTIFIERS = "desugared-library-identifiers";
  public static final String SHA1 = "sha-1";
  public static final String COMPILATION_MODE = "compilation-mode";
  public static final String HAS_CHECKSUMS = "has-checksums";
  public static final String BACKEND = "backend";
  public static final String PG_MAP_ID = "pg-map-id";
  public static final String R8_MODE = "r8-mode";
  private static final String NO_LIBRARY_DESUGARING = "<no-library-desugaring>";
  private static final String ANDROID_PLATFORM_BUILD = "platform";

  public enum Tool {
    D8,
    R8,
    L8,
    Relocator;

    public static Tool[] valuesR8andD8() {
      return new Tool[] {Tool.D8, Tool.R8};
    }
  }

  public enum Backend {
    CF,
    DEX
  }

  private static final char PREFIX_CHAR = '~';
  private static final String PREFIX = "~~";
  private static final String D8_PREFIX = PREFIX + Tool.D8 + "{";
  private static final String R8_PREFIX = PREFIX + Tool.R8 + "{";
  private static final String L8_PREFIX = PREFIX + Tool.L8 + "{";

  private final JsonObject jsonObject;
  private final Tool tool;

  public Marker(Tool tool) {
    this(tool, new JsonObject());
  }

  private Marker(Tool tool, JsonObject jsonObject) {
    this.tool = tool;
    this.jsonObject = jsonObject;
  }

  public static void checkCompatibleDesugaredLibrary(Set<Marker> markers, Reporter reporter) {
    if (markers.size() <= 1) {
      return;
    }
    // In L8 compilation, the compilation has two markers, a L8 marker, which has a desugared
    // library property, and either a D8 or a R8 marker, which has no desugared library property.
    // In other compilations, the desugared library versions have to be consistent.
    Set<String> desugaredLibraryIdentifiers = new HashSet<>();
    for (Marker marker : markers) {
      if (marker.tool == Tool.L8) {
        assert marker.getDesugaredLibraryIdentifiers().length > 0;
        assert markers.stream()
            .allMatch(m -> m.tool == Tool.L8 || m.getDesugaredLibraryIdentifiers().length == 0);
      } else {
        String[] identifiers = marker.getDesugaredLibraryIdentifiers();
        String identifier = null;
        switch (identifiers.length) {
          case 0:
            // Only add the <no-library-desugaring> identifier for DEX. A marker from CF is
            // assumed to go though desugaring for compiling to DEX, and that will introduce the
            // DEX marker with the final library desugaring identifier.
            if (marker.isDexBackend()) {
              identifier = NO_LIBRARY_DESUGARING;
            } else {
              assert marker.isCfBackend();
            }
            break;
          case 1:
            identifier = identifiers[0];
            break;
          default:
            // To be implemented once D8/R8 compilation supports multiple desugared libraries.
            throw reporter.fatalError(
                new StringDiagnostic(
                    "Merging program compiled with multiple desugared libraries."));
        }
        if (marker.isDesugared() && identifier != null) {
          desugaredLibraryIdentifiers.add(identifier);
        } else {
          assert identifier == null || identifier.equals(NO_LIBRARY_DESUGARING);
        }
      }
    }

    if (desugaredLibraryIdentifiers.size() > 1) {
      reporter.error(new DesugaredLibraryMismatchDiagnostic(desugaredLibraryIdentifiers, markers));
    }
  }

  public Tool getTool() {
    return tool;
  }

  public boolean isD8() {
    return tool == Tool.D8;
  }

  public boolean isR8() {
    return tool == Tool.R8;
  }

  public boolean isL8() {
    return tool == Tool.L8;
  }

  public boolean isRelocator() {
    return tool == Tool.Relocator;
  }

  public String getVersion() {
    return jsonObject.get(VERSION).getAsString();
  }

  public Marker setVersion(String version) {
    assert !jsonObject.has(VERSION);
    jsonObject.addProperty(VERSION, version);
    return this;
  }

  public boolean isDesugared() {
    // For both DEX and CF output from D8 and R8 a min-api setting implies that the code has been
    // desugared, as even the highest min-api require desugaring of lambdas.
    return hasMinApi();
  }

  public boolean hasMinApi() {
    return jsonObject.has(MIN_API);
  }

  public Long getMinApi() {
    return jsonObject.get(MIN_API).getAsLong();
  }

  public Marker setMinApi(long minApi) {
    assert !jsonObject.has(MIN_API);
    jsonObject.addProperty(MIN_API, minApi);
    return this;
  }

  public boolean hasDesugaredLibraryIdentifiers() {
    return jsonObject.has(DESUGARED_LIBRARY_IDENTIFIERS);
  }

  public String[] getDesugaredLibraryIdentifiers() {
    if (jsonObject.has(DESUGARED_LIBRARY_IDENTIFIERS)) {
      JsonArray array = jsonObject.get(DESUGARED_LIBRARY_IDENTIFIERS).getAsJsonArray();
      String[] identifiers = new String[array.size()];
      for (int i = 0; i < array.size(); i++) {
        identifiers[i] = array.get(i).getAsString();
      }
      return identifiers;
    }
    return new String[0];
  }

  public Marker setDesugaredLibraryIdentifiers(String... identifiers) {
    assert !jsonObject.has(DESUGARED_LIBRARY_IDENTIFIERS);
    JsonArray jsonIdentifiers = new JsonArray();
    for (String identifier : identifiers) {
      jsonIdentifiers.add(identifier);
    }
    jsonObject.add(DESUGARED_LIBRARY_IDENTIFIERS, jsonIdentifiers);
    return this;
  }

  public String getSha1() {
    return jsonObject.get(SHA1).getAsString();
  }

  public Marker setSha1(String sha1) {
    assert !jsonObject.has(SHA1);
    jsonObject.addProperty(SHA1, sha1);
    return this;
  }

  public String getCompilationMode() {
    return jsonObject.get(COMPILATION_MODE).getAsString();
  }

  public Marker setCompilationMode(CompilationMode mode) {
    assert !jsonObject.has(COMPILATION_MODE);
    jsonObject.addProperty(COMPILATION_MODE, mode.toString().toLowerCase());
    return this;
  }

  public boolean hasBackend() {
    return jsonObject.has(BACKEND);
  }

  public String getBackend() {
    if (!hasBackend()) {
      // Before adding backend we would always compile to dex if min-api was specified.
      return hasMinApi() ? Backend.DEX.name().toLowerCase() : Backend.CF.name().toLowerCase();
    }
    return jsonObject.get(BACKEND).getAsString();
  }

  public boolean isCfBackend() {
    return getBackend().equals(Backend.CF.name().toLowerCase());
  }

  public boolean isDexBackend() {
    return getBackend().equals(Backend.DEX.name().toLowerCase());
  }

  public Marker setBackend(Backend backend) {
    assert !hasBackend();
    jsonObject.addProperty(BACKEND, backend.name().toLowerCase());
    return this;
  }

  public boolean getHasChecksums() {
    return jsonObject.get(HAS_CHECKSUMS).getAsBoolean();
  }

  public Marker setHasChecksums(boolean hasChecksums) {
    assert !jsonObject.has(HAS_CHECKSUMS);
    jsonObject.addProperty(HAS_CHECKSUMS, hasChecksums);
    return this;
  }

  public String getPgMapId() {
    return jsonObject.get(PG_MAP_ID).getAsString();
  }

  public Marker setPgMapId(String pgMapId) {
    assert !jsonObject.has(PG_MAP_ID);
    jsonObject.addProperty(PG_MAP_ID, pgMapId);
    return this;
  }

  public String getR8Mode() {
    return jsonObject.get(R8_MODE).getAsString();
  }

  public Marker setR8Mode(String r8Mode) {
    assert !jsonObject.has(R8_MODE);
    jsonObject.addProperty(R8_MODE, r8Mode);
    return this;
  }

  public boolean isAndroidPlatformBuild() {
    return jsonObject.has(ANDROID_PLATFORM_BUILD)
        && jsonObject.get(ANDROID_PLATFORM_BUILD).getAsBoolean();
  }

  public Marker setAndroidPlatformBuild() {
    assert !jsonObject.has(ANDROID_PLATFORM_BUILD);
    jsonObject.addProperty(ANDROID_PLATFORM_BUILD, true);
    return this;
  }

  @Override
  public String toString() {
    // In order to make printing of markers deterministic we sort the entries by key.
    final JsonObject sortedJson = new JsonObject();
    jsonObject.entrySet().stream()
        .sorted(Comparator.comparing(Entry::getKey))
        .forEach(entry -> sortedJson.add(entry.getKey(), entry.getValue()));
    return PREFIX + tool + sortedJson;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof Marker) {
      Marker other = (Marker) obj;
      return (tool == other.tool) && jsonObject.equals(other.jsonObject);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return tool.hashCode() + 3 * jsonObject.hashCode();
  }

  public DexString toDexString(DexItemFactory factory) {
    return factory.createString(toString());
  }

  // Try to parse str as a marker.
  // Returns null if parsing fails.
  public static Marker parse(DexString dexString) {
    if (dexString.size > 2
        && dexString.content[0] == PREFIX_CHAR
        && dexString.content[1] == PREFIX_CHAR) {
      String str = dexString.toString();
      if (str.startsWith(D8_PREFIX)) {
        return internalParse(Tool.D8, str.substring(D8_PREFIX.length() - 1));
      }
      if (str.startsWith(R8_PREFIX)) {
        return internalParse(Tool.R8, str.substring(R8_PREFIX.length() - 1));
      }
      if (str.startsWith(L8_PREFIX)) {
        return internalParse(Tool.L8, str.substring(L8_PREFIX.length() - 1));
      }
    }
    return null;
  }

  private static Marker internalParse(Tool tool, String str) {
    try {
      JsonElement result = new JsonParser().parse(str);
      if (result.isJsonObject()) {
        return new Marker(tool, result.getAsJsonObject());
      }
    } catch (JsonSyntaxException e) {
      // Fall through.
    }
    return null;
  }
}
