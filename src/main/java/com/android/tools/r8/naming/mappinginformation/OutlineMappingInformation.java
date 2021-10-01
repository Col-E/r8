// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.naming.MapVersion;
import java.util.function.Consumer;

public class OutlineMappingInformation extends MappingInformation {

  public static final MapVersion SUPPORTED_VERSION = MapVersion.MAP_VERSION_EXPERIMENTAL;
  public static final String ID = "com.android.tools.r8.outline";

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public String serialize() {
    throw new CompilationError("Should not yet serialize this");
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
