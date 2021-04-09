// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.MapVersion;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class FileNameInformation extends ScopedMappingInformation {

  private final String fileName;

  public static final String ID = "sourceFile";
  static final String FILE_NAME_KEY = "fileName";

  private FileNameInformation(String fileName, ImmutableList<ScopeReference> scopeReferences) {
    super(scopeReferences);
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
  public boolean allowOther(MappingInformation information) {
    return !information.isFileNameInformation();
  }

  public static FileNameInformation build(ScopeReference classScope, String fileName) {
    return new FileNameInformation(fileName, ImmutableList.of(classScope));
  }

  // Hard override of serialize as there is no current support for scope in source-file info.
  // This should be removed for experimental support of scope in the external format.
  @Override
  public String serialize() {
    return serializeToJsonObject(new JsonObject()).toString();
  }

  @Override
  protected JsonObject serializeToJsonObject(JsonObject object) {
    object.add(MAPPING_ID_KEY, new JsonPrimitive(ID));
    object.add(FILE_NAME_KEY, new JsonPrimitive(fileName));
    return object;
  }

  public static FileNameInformation deserialize(
      MapVersion version,
      JsonObject object,
      DiagnosticsHandler diagnosticsHandler,
      int lineNumber,
      ScopeReference implicitSingletonScope) {
    assert implicitSingletonScope instanceof ClassScopeReference;
    try {
      JsonElement fileName =
          getJsonElementFromObject(object, diagnosticsHandler, lineNumber, FILE_NAME_KEY, ID);
      if (fileName == null) {
        return null;
      }
      return new FileNameInformation(
          fileName.getAsString(), ImmutableList.of(implicitSingletonScope));
    } catch (UnsupportedOperationException | IllegalStateException ignored) {
      diagnosticsHandler.info(
          MappingInformationDiagnostics.invalidValueForObjectWithId(lineNumber, FILE_NAME_KEY, ID));
      return null;
    }
  }
}
