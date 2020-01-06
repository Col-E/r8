// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;

public class RetraceUtils {

  public static String methodDescriptionFromMethodReference(
      MethodReference methodReference, boolean verbose) {
    if (!verbose || methodReference.isUnknown()) {
      return methodReference.getHolderClass().getTypeName() + "." + methodReference.getMethodName();
    }
    StringBuilder sb = new StringBuilder();
    sb.append(
        methodReference.getReturnType() == null
            ? "void"
            : methodReference.getReturnType().getTypeName());
    sb.append(" ");
    sb.append(methodReference.getHolderClass().getTypeName());
    sb.append(".");
    sb.append(methodReference.getMethodName());
    sb.append("(");
    boolean seenFirstIndex = false;
    for (TypeReference formalType : methodReference.getFormalTypes()) {
      if (seenFirstIndex) {
        sb.append(",");
      }
      seenFirstIndex = true;
      sb.append(formalType.getTypeName());
    }
    sb.append(")");
    return sb.toString();
  }
}
