// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import static com.android.tools.r8.naming.mappinginformation.MappingInformationDiagnostics.noKeyForObjectWithId;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.BiConsumer;

public class MetaInfMappingInformation extends MappingInformation {

  public static final String ID = "com.android.tools.r8.metainf";
  public static final String MAP_VERSION_KEY = "map-version";

  private final MapVersion mapVersion;

  public MetaInfMappingInformation(MapVersion mapVersion) {
    super();
    this.mapVersion = mapVersion;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public boolean isMetaInfMappingInformation() {
    return true;
  }

  @Override
  public MetaInfMappingInformation asMetaInfMappingInformation() {
    return this;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return true;
  }

  public MapVersion getMapVersion() {
    return mapVersion;
  }

  @Override
  public String serialize() {
    JsonObject result = new JsonObject();
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    result.add(MAP_VERSION_KEY, new JsonPrimitive(mapVersion.getName()));
    return result.toString();
  }

  public static void deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      BiConsumer<ScopeReference, MappingInformation> onMappingInfo) {
    // Parsing the generator information must support parsing at all map versions as it itself is
    // what establishes the version.
    String mapVersion = object.get(MAP_VERSION_KEY).getAsString();
    if (mapVersion == null) {
      noKeyForObjectWithId(lineNumber, MAP_VERSION_KEY, MAPPING_ID_KEY, ID);
      return;
    }
    MapVersion mapVersion1 = MapVersion.fromName(mapVersion);
    if (mapVersion1 == null) {
      return;
    }
    onMappingInfo.accept(ScopeReference.globalScope(), new MetaInfMappingInformation(mapVersion1));
  }
}
