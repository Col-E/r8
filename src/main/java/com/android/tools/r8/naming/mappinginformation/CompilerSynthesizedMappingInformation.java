// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class CompilerSynthesizedMappingInformation extends ScopedMappingInformation {

  public static final String ID = "com.android.tools.r8.synthesized";

  public static class Builder extends ScopedMappingInformation.Builder<Builder> {

    @Override
    public String getId() {
      return ID;
    }

    @Override
    public Builder self() {
      return this;
    }

    public CompilerSynthesizedMappingInformation build() {
      return new CompilerSynthesizedMappingInformation(buildScope());
    }
  }

  private CompilerSynthesizedMappingInformation(ImmutableList<ScopeReference> scope) {
    super(scope);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean isCompilerSynthesizedMappingInformation() {
    return true;
  }

  @Override
  public CompilerSynthesizedMappingInformation asCompilerSynthesizedMappingInformation() {
    return this;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isCompilerSynthesizedMappingInformation();
  }

  @Override
  protected JsonObject serializeToJsonObject(JsonObject object) {
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return object;
  }

  public static CompilerSynthesizedMappingInformation deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      ScopeReference implicitSingletonScope) {
    if (version.isLessThan(MapVersion.MapVersionExperimental)) {
      return null;
    }
    return builder()
        .deserializeFromJsonObject(object, implicitSingletonScope, diagnosticsHandler, lineNumber)
        .build();
  }
}
