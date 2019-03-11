// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

public class ProguardMapError extends CompilationError {

  protected static final String DUPLICATE_TARGET_MESSAGE = "'%s' and '%s' map to same name: '%s'";
  protected static final String DUPLICATE_SOURCE_MESSAGE = "'%s' already has a mapping";

  private ProguardMapError(String message) {
    super(message);
  }

  private ProguardMapError(String message, Position position) {
    super(message, null, Origin.unknown(), position);
  }

  static ProguardMapError duplicateSourceClass(String typeName, Position position) {
    return new ProguardMapError(String.format(DUPLICATE_SOURCE_MESSAGE, typeName), position);
  }

  static ProguardMapError duplicateSourceMember(
      String signature, String typeName, Position position) {
    return new ProguardMapError(
        String.format(DUPLICATE_SOURCE_MESSAGE, signature, typeName), position);
  }

  static ProguardMapError duplicateTargetClass(
      String source, String other, String mappedName, Position position) {
    return new ProguardMapError(
        String.format(DUPLICATE_TARGET_MESSAGE, source, other, mappedName), position);
  }

  static ProguardMapError duplicateTargetSignature(
      Signature source, Signature other, String mappedName, Position position) {
    return new ProguardMapError(
        String.format(DUPLICATE_TARGET_MESSAGE, source.toString(), other.toString(), mappedName),
        position);
  }

  // TODO(mkroghj) Remove these and when the ProguardMapApplier is removed.
  static ProguardMapError keptTypeWasRenamed(DexType type, String keptName, String rename) {
    return new ProguardMapError(
        type + createMessageForConflict(keptName, rename));
  }

  static ProguardMapError keptMethodWasRenamed(DexMethod method, String keptName, String rename) {
    return new ProguardMapError(
        method.toSourceString() + createMessageForConflict(keptName, rename));
  }

  static ProguardMapError keptFieldWasRenamed(DexField field, String keptName, String rename) {
    return new ProguardMapError(
        field.toSourceString() + createMessageForConflict(keptName, rename));
  }

  private static String createMessageForConflict(String keptName, String rename) {
    return " is not being kept as " + keptName + ", but remapped to " + rename;
  }
}
