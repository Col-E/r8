// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class FileNameInformation extends MappingInformation {

  private final String fileName;

  public static final String ID = "sourceFile";
  static final String FILE_NAME_KEY = "fileName";

  private FileNameInformation(String fileName) {
    super(NO_LINE_NUMBER);
    this.fileName = fileName;
  }

  public String getFileName() {
    return fileName;
  }

  @Override
  public String serialize() {
    JsonObject result = new JsonObject();
    result.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    result.add(FILE_NAME_KEY, new JsonPrimitive(fileName));
    return result.toString();
  }

  @Override
  public boolean isFileNameInformation() {
    return true;
  }

  @Override
  public FileNameInformation asFileNameInformation() {
    return this;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isFileNameInformation();
  }

  public static FileNameInformation build(String fileName) {
    return new FileNameInformation(fileName);
  }

  public static FileNameInformation build(
      JsonObject object, DiagnosticsHandler diagnosticsHandler, int lineNumber) {
    try {
      JsonElement fileName =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, FILE_NAME_KEY, ID);
      if (fileName == null) {
        return null;
      }
      return new FileNameInformation(fileName.getAsString());
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.invalidValueForObjectWithId(lineNumber, FILE_NAME_KEY, ID));
      return null;
    }
  }
}
