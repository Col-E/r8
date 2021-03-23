// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class CompilerSynthesizedMappingInformation extends MappingInformation {

  public static final String ID = "com.android.tools.r8.synthesized";

  public CompilerSynthesizedMappingInformation() {
    super(NO_LINE_NUMBER);
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
  public String serialize() {
    JsonObject result = new JsonObject();
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    return result.toString();
  }

  public static CompilerSynthesizedMappingInformation deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber) {
    if (version.isLessThan(MapVersion.MapVersionExperimental)) {
      return null;
    }
    return new CompilerSynthesizedMappingInformation();
  }
}
