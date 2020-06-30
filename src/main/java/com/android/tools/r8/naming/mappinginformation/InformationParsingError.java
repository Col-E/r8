// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;

@Keep
public class InformationParsingError implements Diagnostic {

  private final String message;
  private final Position position;

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }

  private InformationParsingError(String message, Position position) {
    this.message = message;
    this.position = position;
  }

  static InformationParsingError noHandlerFor(int lineNumber, String value) {
    return new InformationParsingError(
        String.format("Could not find a handler for %s", value),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError noKeyInJson(int lineNumber, String key) {
    return new InformationParsingError(
        String.format("Could not locate '%s' in the JSON object", key),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError notValidJson(int lineNumber) {
    return new InformationParsingError(
        "Not valid JSON", new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError notValidString(int lineNumber, String key) {
    return new InformationParsingError(
        String.format("The value of '%s' is not a valid string in the JSON object", key),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError tooManyInformationalParameters(int lineNumber) {
    return new InformationParsingError(
        "More informational parameters than actual parameters for method signature",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError noKeyForObjectWithId(
      int lineNumber, String key, String mappingKey, String mappingValue) {
    return new InformationParsingError(
        String.format("Could not find '%s' for object with %s '%s'", key, mappingKey, mappingValue),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError invalidValueForObjectWithId(
      int lineNumber, String mappingKey, String mappingValue) {
    return new InformationParsingError(
        String.format(
            "Could not decode the information for the object with %s '%s'",
            mappingKey, mappingValue),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError tooManyEntriesForParameterInformation(int lineNumber) {
    return new InformationParsingError(
        "Parameter information do not have 1 or 2 entries",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static InformationParsingError invalidParameterInformationObject(int lineNumber) {
    return new InformationParsingError(
        "Parameter information is not an index and a string representation of a type",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }
}
