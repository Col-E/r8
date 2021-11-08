// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.naming.MapVersion;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.util.function.Consumer;

public class OutlineCallsiteMappingInformation extends MappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_EXPERIMENTAL;
  public static final String ID = "com.android.tools.r8.outlineCallsite";

  private static final String POSITIONS_KEY = "positions";

  private final Int2IntSortedMap positions;

  private OutlineCallsiteMappingInformation(Int2IntSortedMap positions) {
    this.positions = positions;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    JsonObject result = new JsonObject();
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    JsonObject mappedPositions = new JsonObject();
    positions.forEach(
        (obfuscatedPosition, originalPosition) -> {
          mappedPositions.add(obfuscatedPosition + "", new JsonPrimitive(originalPosition));
        });
    result.add(POSITIONS_KEY, mappedPositions);
    return result.toString();
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isOutlineCallsiteInformation();
  }

  @Override
  public boolean isOutlineCallsiteInformation() {
    return true;
  }

  @Override
  public OutlineCallsiteMappingInformation asOutlineCallsiteInformation() {
    return this;
  }

  public int rewritePosition(int originalPosition) {
    return positions.getOrDefault(originalPosition, originalPosition);
  }

  public static OutlineCallsiteMappingInformation create(Int2IntSortedMap positions) {
    return new OutlineCallsiteMappingInformation(positions);
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  public static void deserialize(
      MapVersion version, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      JsonObject postionsMapObject = object.getAsJsonObject(POSITIONS_KEY);
      if (postionsMapObject == null) {
        throw new CompilationError(
            "Expected '" + POSITIONS_KEY + "' to be present: " + object.getAsString());
      }
      Int2IntSortedMap positionsMap = new Int2IntLinkedOpenHashMap();
      postionsMapObject
          .entrySet()
          .forEach(
              entry -> {
                try {
                  String key = entry.getKey();
                  int originalPosition = Integer.parseInt(key);
                  int newPosition = entry.getValue().getAsInt();
                  positionsMap.put(originalPosition, newPosition);
                } catch (Throwable ex) {
                  throw new CompilationError("Invalid position entry: " + entry.toString());
                }
              });
      onMappingInfo.accept(OutlineCallsiteMappingInformation.create(positionsMap));
    }
  }
}
