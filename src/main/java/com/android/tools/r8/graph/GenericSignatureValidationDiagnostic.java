// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper.SignatureEvaluationResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

public class GenericSignatureValidationDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;
  private final String message;

  GenericSignatureValidationDiagnostic(Origin origin, Position position, String message) {
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

  static GenericSignatureValidationDiagnostic invalidClassSignature(
      String signature, String name, Origin origin, SignatureEvaluationResult error) {
    return invalidSignature(signature, "class", name, origin, error);
  }

  static GenericSignatureValidationDiagnostic invalidMethodSignature(
      String signature, String name, Origin origin, SignatureEvaluationResult error) {
    return invalidSignature(signature, "method", name, origin, error);
  }

  static GenericSignatureValidationDiagnostic invalidFieldSignature(
      String signature, String name, Origin origin, SignatureEvaluationResult error) {
    return invalidSignature(signature, "field", name, origin, error);
  }

  private static GenericSignatureValidationDiagnostic invalidSignature(
      String signature, String kind, String name, Origin origin, SignatureEvaluationResult error) {
    String message =
        "Invalid signature '"
            + signature
            + "' for "
            + kind
            + " "
            + name
            + "."
            + System.lineSeparator()
            + "Validation error: "
            + error.getDescription()
            + "."
            + System.lineSeparator()
            + "Signature is ignored and will not be present in the output.";
    return new GenericSignatureValidationDiagnostic(origin, Position.UNKNOWN, message);
  }
}
