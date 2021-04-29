// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;

public class MissingDefinitionsDiagnosticTestUtils {

  public static String getMissingClassMessage(
      Class<?> missingClass, DefinitionContext... referencedFrom) {
    return getMissingClassMessage(Reference.classFromClass(missingClass), referencedFrom);
  }

  public static String getMissingClassMessage(
      ClassReference missingReference, DefinitionContext... referencedFrom) {
    StringBuilder builder =
        new StringBuilder("Missing class ").append(missingReference.getTypeName());
    appendReferencedFromSuffix(builder, referencedFrom);
    return builder.toString();
  }

  public static String getMissingFieldMessage(
      FieldReference missingReference, DefinitionContext... referencedFrom) {
    StringBuilder builder =
        new StringBuilder("Missing field ")
            .append(FieldReferenceUtils.toSourceString(missingReference));
    appendReferencedFromSuffix(builder, referencedFrom);
    return builder.toString();
  }

  public static String getMissingMethodMessage(
      MethodReference missingReference, DefinitionContext... referencedFrom) {
    StringBuilder builder =
        new StringBuilder("Missing method ")
            .append(MethodReferenceUtils.toSourceString(missingReference));
    appendReferencedFromSuffix(builder, referencedFrom);
    return builder.toString();
  }

  private static void appendReferencedFromSuffix(
      StringBuilder builder, DefinitionContext... referencedFrom) {
    builder
        .append(" (referenced from: ")
        .append(DefinitionContextUtils.toSourceString(referencedFrom[0]));
    int numberOfContexts = referencedFrom.length;
    if (numberOfContexts > 1) {
      builder.append(" and ").append(numberOfContexts - 1).append(" other context");
      if (numberOfContexts > 2) {
        builder.append("s");
      }
    }
    builder.append(")");
  }
}
