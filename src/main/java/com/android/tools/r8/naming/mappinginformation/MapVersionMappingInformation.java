// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.noKeyForObjectWithId;

import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.Consumer;

public class MapVersionMappingInformation extends MappingInformation {

  public static final String ID = "com.android.tools.r8.mapping";
  public static final String MAP_VERSION_KEY = "version";

  private final MapVersion mapVersion;
  private final String value;

  public MapVersionMappingInformation(MapVersion mapVersion, String value) {
    this.mapVersion = mapVersion;
    this.value = value;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isMapVersionMappingInformation() {
    return true;
  }

  @Override
  public MapVersionMappingInformation asMapVersionMappingInformation() {
    return this;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    assert existing.isMapVersionMappingInformation();
    MapVersionMappingInformation existingMapVersionInfo = existing.asMapVersionMappingInformation();
    return mapVersion.isLessThanOrEqualTo(existingMapVersionInfo.getMapVersion()) ? existing : this;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return true;
  }

  public MapVersion getMapVersion() {
    return mapVersion;
  }

  public String getValue() {
    return value;
  }

  @Override
  public boolean isGlobalMappingInformation() {
    return true;
  }

  @Override
  public String serialize() {
    JsonObject result = new JsonObject();
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    result.add(MAP_VERSION_KEY, new JsonPrimitive(mapVersion.getName()));
    return result.toString();
  }

  public static void deserialize(
      JsonObject object, int lineNumber, Consumer<MappingInformation> onMappingInfo) {
    // Parsing the generator information must support parsing at all map versions as it itself is
    // what establishes the version.
    String mapVersionString = object.get(MAP_VERSION_KEY).getAsString();
    if (mapVersionString == null) {
      noKeyForObjectWithId(lineNumber, MAP_VERSION_KEY, MAPPING_ID_KEY, ID);
      return;
    }
    MapVersion mapVersion = MapVersion.fromName(mapVersionString);
    if (mapVersion == null) {
      mapVersion = MapVersion.MAP_VERSION_UNKNOWN;
    }
    onMappingInfo.accept(new MapVersionMappingInformation(mapVersion, mapVersionString));
  }
}
