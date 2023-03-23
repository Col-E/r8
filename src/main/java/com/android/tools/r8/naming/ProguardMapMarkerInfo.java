// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.Version;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.VersionProperties;
import java.util.ArrayList;
import java.util.List;

public class ProguardMapMarkerInfo {

  private static final String MARKER_KEY_COMPILER = "compiler";
  private static final String MARKER_KEY_COMPILER_VERSION = "compiler_version";
  private static final String MARKER_KEY_COMPILER_HASH = "compiler_hash";
  private static final String MARKER_KEY_MIN_API = "min_api";
  private static final String MARKER_KEY_PG_MAP_ID = "pg_map_id";
  public static final String MARKER_KEY_PG_MAP_HASH = "pg_map_hash";
  public static final String SHA_256_KEY = "SHA-256";

  private final String compilerName;
  private final boolean isGeneratingDex;
  private final AndroidApiLevel apiLevel;
  private final MapVersion mapVersion;
  private final ProguardMapId proguardMapId;

  private ProguardMapMarkerInfo(
      String compilerName,
      boolean isGeneratingDex,
      AndroidApiLevel apiLevel,
      MapVersion mapVersion,
      ProguardMapId proguardMapId) {
    this.compilerName = compilerName;
    this.isGeneratingDex = isGeneratingDex;
    this.apiLevel = apiLevel;
    this.mapVersion = mapVersion;
    this.proguardMapId = proguardMapId;
  }

  public List<String> toPreamble() {
    List<String> preamble = new ArrayList<>();
    preamble.add("# " + MARKER_KEY_COMPILER + ": " + compilerName);
    preamble.add("# " + MARKER_KEY_COMPILER_VERSION + ": " + Version.LABEL);
    if (isGeneratingDex) {
      preamble.add("# " + MARKER_KEY_MIN_API + ": " + apiLevel.getLevel());
    }
    if (Version.isDevelopmentVersion()) {
      preamble.add("# " + MARKER_KEY_COMPILER_HASH + ": " + VersionProperties.INSTANCE.getSha());
    }
    // Turn off linting of the mapping file in some build systems.
    preamble.add("# common_typos_disable");
    // Emit the R8 specific map-file version.
    if (mapVersion.isGreaterThan(MapVersion.MAP_VERSION_NONE)) {
      preamble.add("# " + mapVersion.toMapVersionMappingInformation().serialize());
    }
    preamble.add("# " + MARKER_KEY_PG_MAP_ID + ": " + proguardMapId.getId());
    preamble.add(
        "# " + MARKER_KEY_PG_MAP_HASH + ": " + SHA_256_KEY + " " + proguardMapId.getHash());
    return preamble;
  }

  public String serializeToString() {
    return StringUtils.unixLines(toPreamble());
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private String compilerName;
    private boolean isGeneratingDex;
    private AndroidApiLevel apiLevel;
    private MapVersion mapVersion;
    private ProguardMapId proguardMapId;

    public Builder setCompilerName(String compilerName) {
      this.compilerName = compilerName;
      return this;
    }

    public Builder setApiLevel(AndroidApiLevel apiLevel) {
      this.apiLevel = apiLevel;
      return this;
    }

    public Builder setGeneratingDex(boolean generatingDex) {
      isGeneratingDex = generatingDex;
      return this;
    }

    public Builder setMapVersion(MapVersion mapVersion) {
      this.mapVersion = mapVersion;
      return this;
    }

    public Builder setProguardMapId(ProguardMapId proguardMapId) {
      this.proguardMapId = proguardMapId;
      return this;
    }

    public ProguardMapMarkerInfo build() {
      return new ProguardMapMarkerInfo(
          compilerName, isGeneratingDex, apiLevel, mapVersion, proguardMapId);
    }
  }
}
