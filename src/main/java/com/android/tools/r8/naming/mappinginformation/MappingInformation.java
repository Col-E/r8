// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualFieldSignatureMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation.ResidualMethodSignatureMappingInformation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.function.Consumer;

public abstract class MappingInformation {

  public static final String MAPPING_ID_KEY = "id";

  public abstract String getId();

  public abstract String serialize();

  public boolean isMapVersionMappingInformation() {
    return false;
  }

  public boolean isUnknownJsonMappingInformation() {
    return false;
  }

  public boolean isFileNameInformation() {
    return false;
  }

  public boolean isRewriteFrameMappingInformation() {
    return false;
  }

  public boolean isOutlineCallsiteInformation() {
    return false;
  }

  public boolean isCompilerSynthesizedMappingInformation() {
    return false;
  }

  public boolean isOutlineMappingInformation() {
    return false;
  }

  public boolean isResidualSignatureMappingInformation() {
    return false;
  }

  public boolean isResidualMethodSignatureMappingInformation() {
    return false;
  }

  public boolean isResidualFieldSignatureMappingInformation() {
    return false;
  }

  public MapVersionMappingInformation asMapVersionMappingInformation() {
    return null;
  }

  public FileNameInformation asFileNameInformation() {
    return null;
  }

  public CompilerSynthesizedMappingInformation asCompilerSynthesizedMappingInformation() {
    return null;
  }

  public UnknownJsonMappingInformation asUnknownJsonMappingInformation() {
    return null;
  }

  public RewriteFrameMappingInformation asRewriteFrameMappingInformation() {
    return null;
  }

  public OutlineMappingInformation asOutlineMappingInformation() {
    return null;
  }

  public OutlineCallsiteMappingInformation asOutlineCallsiteInformation() {
    return null;
  }

  public ResidualMethodSignatureMappingInformation asResidualMethodSignatureMappingInformation() {
    return null;
  }

  public ResidualFieldSignatureMappingInformation asResidualFieldSignatureMappingInformation() {
    return null;
  }

  public boolean shouldCompose(MappingInformation existing) {
    return !allowOther(existing);
  }

  public abstract MappingInformation compose(MappingInformation existing)
      throws MappingComposeException;

  public boolean isGlobalMappingInformation() {
    return false;
  }

  public abstract boolean allowOther(MappingInformation information);

  public static void fromJsonObject(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    if (object == null) {
      diagnosticsHandler.info(MappingInformationDiagnostics.notValidJson(lineNumber));
      return;
    }
    JsonElement id = object.get(MAPPING_ID_KEY);
    if (id == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.noKeyInJson(lineNumber, MAPPING_ID_KEY));
      return;
    }
    String idString = id.getAsString();
    if (idString == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.notValidString(lineNumber, MAPPING_ID_KEY));
      return;
    }
    deserialize(
        idString,
        version,
        object,
        diagnosticsHandler,
        lineNumber,
        onMappingInfo);
  }

  private static void deserialize(
      String id,
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    switch (id) {
      case MapVersionMappingInformation.ID:
        MapVersionMappingInformation.deserialize(object, lineNumber, onMappingInfo);
        return;
      case FileNameInformation.ID:
        FileNameInformation.deserialize(
            version, object, diagnosticsHandler, lineNumber, onMappingInfo);
        return;
      case CompilerSynthesizedMappingInformation.ID:
        CompilerSynthesizedMappingInformation.deserialize(version, onMappingInfo);
        return;
      case RewriteFrameMappingInformation.ID:
        RewriteFrameMappingInformation.deserialize(version, object, onMappingInfo);
        return;
      case OutlineMappingInformation.ID:
        OutlineMappingInformation.deserialize(version, onMappingInfo);
        return;
      case OutlineCallsiteMappingInformation.ID:
        OutlineCallsiteMappingInformation.deserialize(version, object, onMappingInfo);
        return;
      case ResidualSignatureMappingInformation.ID:
        ResidualSignatureMappingInformation.deserialize(version, object, onMappingInfo);
        return;
      default:
        diagnosticsHandler.info(MappingInformationDiagnostics.noHandlerFor(lineNumber, id));
        UnknownJsonMappingInformation.deserialize(id, object, onMappingInfo);
    }
  }

  static JsonElement getJsonElementFromObject(
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      String key,
      String id) {
    JsonElement element = object.get(key);
    if (element == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.noKeyForObjectWithId(lineNumber, key, MAPPING_ID_KEY, id));
    }
    return element;
  }
}
