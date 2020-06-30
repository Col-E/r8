// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.utils.Reporter;
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

  public static MappingInformation fromJsonObject(
      JsonObject object, Reporter reporter, int lineNumber) {
    if (object == null) {
      reporter.info(InformationParsingError.notValidJson(lineNumber));
      return null;
    }
    JsonElement id = object.get(MAPPING_ID_KEY);
    if (id == null) {
      reporter.info(InformationParsingError.noKeyInJson(lineNumber, MAPPING_ID_KEY));
      return null;
    }
    String idString = id.getAsString();
    if (idString == null) {
      reporter.info(InformationParsingError.notValidString(lineNumber, MAPPING_ID_KEY));
      return null;
    }
    if (idString.equals(MethodSignatureChangedInformation.ID)) {
      return MethodSignatureChangedInformation.build(object, reporter, lineNumber);
    }
    reporter.info(InformationParsingError.noHandlerFor(lineNumber, idString));
    return null;
  }

  static JsonElement getJsonElementFromObject(
      JsonObject object, Reporter reporter, int lineNumber, String key, String id) {
    JsonElement element = object.get(key);
    if (element == null) {
      reporter.info(
          InformationParsingError.noKeyForObjectWithId(lineNumber, key, MAPPING_ID_KEY, id));
    }
    return element;
  }
}
