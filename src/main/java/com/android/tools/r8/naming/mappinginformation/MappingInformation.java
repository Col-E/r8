// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public abstract class MappingInformation {

  static final int NO_LINE_NUMBER = -1;

  public static final String MAPPING_ID_KEY = "id";

  private final int lineNumber;

  MappingInformation(int lineNumber) {
    this.lineNumber = lineNumber;
  }

  public int getLineNumber() {
    return lineNumber;
  }

  public abstract String serialize();

  public boolean isSignatureMappingInformation() {
    return false;
  }

  public SignatureMappingInformation asSignatureMappingInformation() {
    return null;
  }

  public boolean isFileNameInformation() {
    return false;
  }

  public FileNameInformation asFileNameInformation() {
    return null;
  }

  public boolean isMethodSignatureChangedInformation() {
    return false;
  }

  public MethodSignatureChangedInformation asMethodSignatureChangedInformation() {
    return null;
  }

  public abstract boolean allowOther(MappingInformation information);

  public static MappingInformation fromJsonObject(
      JsonObject object, DiagnosticsHandler diagnosticsHandler, int lineNumber) {
    if (object == null) {
      diagnosticsHandler.info(MappingInformationDiagnostics.notValidJson(lineNumber));
      return null;
    }
    JsonElement id = object.get(MAPPING_ID_KEY);
    if (id == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.noKeyInJson(lineNumber, MAPPING_ID_KEY));
      return null;
    }
    String idString = id.getAsString();
    if (idString == null) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.notValidString(lineNumber, MAPPING_ID_KEY));
      return null;
    }
    switch (idString) {
      case MethodSignatureChangedInformation.ID:
        return MethodSignatureChangedInformation.build(object, diagnosticsHandler, lineNumber);
      case FileNameInformation.ID:
        return FileNameInformation.build(object, diagnosticsHandler, lineNumber);
      default:
        diagnosticsHandler.info(MappingInformationDiagnostics.noHandlerFor(lineNumber, idString));
        return null;
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
