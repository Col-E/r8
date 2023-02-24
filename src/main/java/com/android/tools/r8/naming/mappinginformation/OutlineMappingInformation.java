// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.Consumer;

public class OutlineMappingInformation extends ReferentialMappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_2_0;
  public static final String ID = "com.android.tools.r8.outline";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return object.toString();
  }

  @Override
  public OutlineMappingInformation asOutlineMappingInformation() {
    return this;
  }

  @Override
  public MappingInformation compose(MappingInformation existing) {
    // It does not matter which one we take so just take the first one.
    return existing;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return true;
  }

  @Override
  public boolean isOutlineMappingInformation() {
    return true;
  }

  public static boolean isSupported(MapVersion version) {
    return version.isGreaterThanOrEqualTo(SUPPORTED_VERSION);
  }

  public static void deserialize(MapVersion version, Consumer<MappingInformation> onMappingInfo) {
    if (isSupported(version)) {
      onMappingInfo.accept(new OutlineMappingInformation());
    }
  }

  public static OutlineMappingInformation.Builder builder() {
    return new OutlineMappingInformation.Builder();
  }

  public static class Builder {

    public OutlineMappingInformation build() {
      return new OutlineMappingInformation();
    }
  }
}
