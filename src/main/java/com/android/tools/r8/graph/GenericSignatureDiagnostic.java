// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.lang.reflect.GenericSignatureFormatError;

public class GenericSignatureDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;
  private final String message;

  GenericSignatureDiagnostic(Origin origin, Position position, String message) {
    this.origin = origin;
    this.position = position;
    this.message = message;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return message;
  }

  static GenericSignatureDiagnostic invalidClassSignature(
      String signature, String name, Origin origin, GenericSignatureFormatError error) {
    return invalidSignature(signature, "class", name, origin, error);
  }

  static GenericSignatureDiagnostic invalidMethodSignature(
      String signature, String name, Origin origin, GenericSignatureFormatError error) {
    return invalidSignature(signature, "method", name, origin, error);
  }

  static GenericSignatureDiagnostic invalidFieldSignature(
      String signature, String name, Origin origin, GenericSignatureFormatError error) {
    return invalidSignature(signature, "field", name, origin, error);
  }

  private static GenericSignatureDiagnostic invalidSignature(
      String signature,
      String kind,
      String name,
      Origin origin,
      GenericSignatureFormatError error) {
    String message =
        "Invalid signature '"
            + signature
            + "' for "
            + kind
            + " "
            + name
            + "."
            + System.lineSeparator()
            + "Signature is ignored and will not be present in the output."
            + System.lineSeparator()
            + "Parser error: "
            + error.getMessage();
    return new GenericSignatureDiagnostic(origin, Position.UNKNOWN, message);
  }
}
