// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.StringUtils;

public class KotlinMetadataDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;
  private final String message;

  public KotlinMetadataDiagnostic(Origin origin, Position position, String message) {
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

  static KotlinMetadataDiagnostic missingCompanionObject(
      DexClass clazz, String companionObjectName) {
    return new KotlinMetadataDiagnostic(
        clazz.getOrigin(),
        Position.UNKNOWN,
        "The companion object "
            + companionObjectName
            + " could not be found in class "
            + clazz.type.getName());
  }

  static KotlinMetadataDiagnostic unknownClassifier(String classifier) {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        Position.UNKNOWN,
        "The classifier " + classifier + " is unknown and cannot be parsed");
  }

  static KotlinMetadataDiagnostic invalidMethodDescriptor(String nameAndDescriptor) {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        Position.UNKNOWN,
        "Invalid descriptor (deserialized from Kotlin @Metadata): " + nameAndDescriptor);
  }

  static KotlinMetadataDiagnostic unexpectedErrorWhenRewriting(DexType type, Throwable t) {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        Position.UNKNOWN,
        "Unexpected error during rewriting of Kotlin metadata for class '"
            + type.toSourceString()
            + "':"
            + StringUtils.LINE_SEPARATOR
            + StringUtils.stacktraceAsString(t));
  }

  static KotlinMetadataDiagnostic lambdaBackingNotFound(
      DexType type, KotlinJvmMethodSignatureInfo signatureInfo) {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        Position.UNKNOWN,
        "The lambda function "
            + signatureInfo.toString()
            + " could no longer be found in "
            + type.toSourceString()
            + " . The method is most likely pruned and would require a specific keep rule to keep"
            + " alive. As a result, the metadata information regarding the lambda structure has"
            + " been discarded.");
  }
}
