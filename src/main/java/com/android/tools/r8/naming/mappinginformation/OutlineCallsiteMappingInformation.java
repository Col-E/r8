// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.PositionalMappingInformation;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import java.util.function.Consumer;

public class OutlineCallsiteMappingInformation extends PositionalMappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_0;
  public static final MapVersion SUPPORTED_WITH_OUTLINE_VERSION = MapVersion.MAP_VERSION_2_1;
  public static final String ID = "com.android.tools.r8.outlineCallsite";

  private static final String POSITIONS_KEY = "positions";
  private static final String OUTLINE_KEY = "outline";

  private Int2IntSortedMap positions;
  private MethodReference outline;

  private OutlineCallsiteMappingInformation(Int2IntSortedMap positions, MethodReference outline) {
    this.positions = positions;
    this.outline = outline;
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
    if (outline != null) {
      result.add(OUTLINE_KEY, new JsonPrimitive(outline.toString()));
    }
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

  @Override
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    throw new MappingComposeException("Unable to compose " + ID);
  }

  public int rewritePosition(int originalPosition) {
    return positions.getOrDefault(originalPosition, originalPosition);
  }

  public MethodReference getOutline() {
    return outline;
  }

  public static OutlineCallsiteMappingInformation create(
      Int2IntSortedMap positions, MethodReference outline) {
    return new OutlineCallsiteMappingInformation(positions, outline);
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  public static void deserialize(
      MapVersion version, JsonObject object, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      JsonObject positionsMapObject = object.getAsJsonObject(POSITIONS_KEY);
      if (positionsMapObject == null) {
        throw new CompilationError("Expected '" + POSITIONS_KEY + "' to be present: " + object);
      }
      Int2IntSortedMap positionsMap = new Int2IntLinkedOpenHashMap();
      positionsMapObject
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
      MethodReference outline = null;
      JsonElement outlineElement = object.get(OUTLINE_KEY);
      if (outlineElement != null) {
        outline = MethodReferenceUtils.methodFromSmali(outlineElement.getAsString());
      } else if (version.isGreaterThanOrEqualTo(SUPPORTED_WITH_OUTLINE_VERSION)) {
        throw new CompilationError("Expected '" + OUTLINE_KEY + "' to be present: " + object);
      }
      onMappingInfo.accept(OutlineCallsiteMappingInformation.create(positionsMap, outline));
    }
  }

  public void setOutlineInternal(MethodReference outline) {
    this.outline = outline;
  }

  public Int2IntSortedMap getPositions() {
    return positions;
  }

  public void setPositionsInternal(Int2IntSortedMap positions) {
    this.positions = positions;
  }
}
