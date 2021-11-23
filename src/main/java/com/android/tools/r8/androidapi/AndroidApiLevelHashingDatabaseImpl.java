// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.utils.AndroidApiLevel.ANDROID_PLATFORM;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.structural.DefaultHashingVisitor;
import com.android.tools.r8.utils.structural.HasherWrapper;
import com.android.tools.r8.utils.structural.StructuralItem;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class AndroidApiLevelHashingDatabaseImpl implements AndroidApiLevelDatabase {

  public static HasherWrapper getDefaultHasher() {
    return HasherWrapper.murmur3128Hasher();
  }

  private final Int2ReferenceMap<AndroidApiLevel> lookupNonAmbiguousCache =
      new Int2ReferenceOpenHashMap<AndroidApiLevel>();
  private final Map<String, AndroidApiLevel> ambiguousHashesWithApiLevel = new HashMap<>();
  private final Map<DexReference, AndroidApiLevel> ambiguousCache = new IdentityHashMap<>();

  public AndroidApiLevelHashingDatabaseImpl(
      List<AndroidApiForHashingClass> predefinedApiTypeLookup) {
    loadData();
    predefinedApiTypeLookup.forEach(
        apiClass -> {
          DexType type = apiClass.getType();
          lookupNonAmbiguousCache.put(type.hashCode(), null);
          ambiguousCache.put(type, apiClass.getApiLevel());
          apiClass.visitMethodsWithApiLevels(
              (method, apiLevel) -> {
                lookupNonAmbiguousCache.put(method.hashCode(), null);
                ambiguousCache.put(method, apiLevel);
              });
          apiClass.visitFieldsWithApiLevels(
              (field, apiLevel) -> {
                lookupNonAmbiguousCache.put(field.hashCode(), null);
                ambiguousCache.put(field, apiLevel);
              });
        });
  }

  private void loadData() {
    int[] hashIndices;
    byte[] apiLevels;
    List<String> ambiguous;
    try (InputStream indicesInputStream =
            getClass()
                .getClassLoader()
                .getResourceAsStream("api_database/api_database_hash_lookup.ser");
        ObjectInputStream indicesObjectStream = new ObjectInputStream(indicesInputStream);
        InputStream apiInputStream =
            getClass()
                .getClassLoader()
                .getResourceAsStream("api_database/api_database_api_level.ser");
        ObjectInputStream apiObjectStream = new ObjectInputStream(apiInputStream);
        InputStream ambiguousInputStream =
            getClass()
                .getClassLoader()
                .getResourceAsStream("api_database/api_database_ambiguous.txt")) {
      hashIndices = (int[]) indicesObjectStream.readObject();
      apiLevels = (byte[]) apiObjectStream.readObject();
      ambiguous =
          new BufferedReader(new InputStreamReader(ambiguousInputStream, StandardCharsets.UTF_8))
              .lines()
              .collect(Collectors.toList());
    } catch (IOException | ClassNotFoundException e) {
      throw new RuntimeException("Could not build api database");
    }
    assert hashIndices.length == apiLevels.length;
    for (int i = 0; i < hashIndices.length; i++) {
      byte apiLevel = apiLevels[i];
      lookupNonAmbiguousCache.put(
          hashIndices[i], apiLevel == -1 ? null : AndroidApiLevel.getAndroidApiLevel(apiLevel));
    }
    ambiguous.forEach(this::parseAmbiguous);
  }

  /**
   * All elements in the ambiguous map are on the form <key>:<api-level>. The reason for this
   * additional map is that the keys collide for the items using the ordinary hashing function.
   */
  private void parseAmbiguous(String ambiguous) {
    String[] split = ambiguous.split(":");
    if (split.length != 2) {
      throw new CompilationError("Expected two entries in ambiguous map");
    }
    ambiguousHashesWithApiLevel.put(
        split[0], AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(split[1])));
  }

  @Override
  public AndroidApiLevel getTypeApiLevel(DexType type) {
    return lookupApiLevel(type);
  }

  @Override
  public AndroidApiLevel getMethodApiLevel(DexMethod method) {
    return lookupApiLevel(method);
  }

  @Override
  public AndroidApiLevel getFieldApiLevel(DexField field) {
    return lookupApiLevel(field);
  }

  private AndroidApiLevel lookupApiLevel(DexReference reference) {
    // We use Android platform to track if an element is unknown since no occurrences of that api
    // level exists in the database.
    AndroidApiLevel result =
        lookupNonAmbiguousCache.getOrDefault(reference.hashCode(), ANDROID_PLATFORM);
    if (result != null) {
      return result == ANDROID_PLATFORM ? null : result;
    }
    return ambiguousCache.computeIfAbsent(
        reference,
        ignored -> {
          HasherWrapper defaultHasher = getDefaultHasher();
          reference.accept(
              type -> DefaultHashingVisitor.run(type, defaultHasher, DexType::acceptHashing),
              field ->
                  DefaultHashingVisitor.run(field, defaultHasher, StructuralItem::acceptHashing),
              method ->
                  DefaultHashingVisitor.run(method, defaultHasher, StructuralItem::acceptHashing));
          String existingHash = defaultHasher.hash().toString();
          AndroidApiLevel androidApiLevel = ambiguousHashesWithApiLevel.get(existingHash);
          if (androidApiLevel == null) {
            throw new CompilationError(
                "Failed to find api level for reference: "
                    + reference.toSourceString()
                    + " with hash value: "
                    + existingHash);
          }
          return androidApiLevel;
        });
  }
}
