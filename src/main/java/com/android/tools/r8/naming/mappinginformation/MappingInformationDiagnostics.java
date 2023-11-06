// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.mappinginformation;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;

@KeepForApi
public class MappingInformationDiagnostics implements Diagnostic {

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

  private MappingInformationDiagnostics(String message, Position position) {
    this.message = message;
    this.position = position;
  }

  static MappingInformationDiagnostics noHandlerFor(int lineNumber, String value) {
    return new MappingInformationDiagnostics(
        String.format("Could not find a handler for %s", value),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics noKeyInJson(int lineNumber, String key) {
    return new MappingInformationDiagnostics(
        String.format("Could not locate '%s' in the JSON object", key),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics notValidJson(int lineNumber) {
    return new MappingInformationDiagnostics(
        "Not valid JSON", new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics notValidString(int lineNumber, String key) {
    return new MappingInformationDiagnostics(
        String.format("The value of '%s' is not a valid string in the JSON object", key),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics tooManyInformationalParameters(int lineNumber) {
    return new MappingInformationDiagnostics(
        "More informational parameters than actual parameters for method signature",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics noKeyForObjectWithId(
      int lineNumber, String key, String mappingKey, String mappingValue) {
    return new MappingInformationDiagnostics(
        String.format("Could not find '%s' for object with %s '%s'", key, mappingKey, mappingValue),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics invalidValueForObjectWithId(
      int lineNumber, String mappingKey, String mappingValue) {
    return new MappingInformationDiagnostics(
        String.format(
            "Could not decode the information for the object with %s '%s'",
            mappingKey, mappingValue),
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics tooManyEntriesForParameterInformation(int lineNumber) {
    return new MappingInformationDiagnostics(
        "Parameter information do not have 1 or 2 entries",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  static MappingInformationDiagnostics invalidParameterInformationObject(int lineNumber) {
    return new MappingInformationDiagnostics(
        "Parameter information is not an index and a string representation of a type",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  public static MappingInformationDiagnostics notAllowedCombination(
      MappingInformation one, MappingInformation other, int lineNumber) {
    return new MappingInformationDiagnostics(
        "The mapping '" + one + "' is not allowed in combination with '" + other + "'",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  public static MappingInformationDiagnostics invalidResidualSignature(
      String info, int lineNumber) {
    return new MappingInformationDiagnostics(
        "The residual signature mapping '" + info + "' is invalid'",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }

  public static MappingInformationDiagnostics invalidResidualSignatureType(
      String info, int lineNumber) {
    return new MappingInformationDiagnostics(
        "The residual signature mapping '"
            + info
            + "' is not of the same type as the "
            + "member it describes.'",
        new TextPosition(1, lineNumber, TextPosition.UNKNOWN_COLUMN));
  }
}
