// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.mappinginformation.MappingInformation.ReferentialMappingInformation;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.function.Consumer;

public class FileNameInformation extends ReferentialMappingInformation {

  private final String fileName;

  public static final String ID = "sourceFile";
  static final String FILE_NAME_KEY = "fileName";

  private FileNameInformation(String fileName) {
    this.fileName = fileName;
  }

  @Override
  public String getId() {
    return ID;
  }

  public String getFileName() {
    return fileName;
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
  public MappingInformation compose(MappingInformation existing) throws MappingComposeException {
    // Always take the first mapping.
    return existing;
  }

  @Override
  public boolean allowOther(MappingInformation information) {
    return !information.isFileNameInformation();
  }

  public static FileNameInformation build(String fileName) {
    return new FileNameInformation(fileName);
  }

  @Override
  public String serialize() {
    JsonObject object = new JsonObject();
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    object.add(FILE_NAME_KEY, new JsonPrimitive(fileName));
    return object.toString();
  }

  public static void deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      Consumer<MappingInformation> onMappingInfo) {
    try {
      JsonElement fileName =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, FILE_NAME_KEY, ID);
      if (fileName != null) {
        onMappingInfo.accept(new FileNameInformation(fileName.getAsString()));
      }
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.invalidValueForObjectWithId(lineNumber, FILE_NAME_KEY, ID));
    }
  }
}
