// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.TypeReference;
import java.util.Iterator;

public class MethodReferenceUtils {

  public static String toSourceStringWithoutHolderAndReturnType(MethodReference methodReference) {
    return toSourceString(methodReference, false, false);
  }

  public static String toSourceString(
      MethodReference methodReference, boolean includeHolder, boolean includeReturnType) {
    StringBuilder builder = new StringBuilder();
    if (includeReturnType) {
      builder.append(methodReference.getReturnType().getTypeName()).append(" ");
    }
    if (includeHolder) {
      builder.append(methodReference.getHolderClass().getTypeName()).append(".");
    }
    builder.append(methodReference.getMethodName()).append("(");
    Iterator<TypeReference> formalTypesIterator = methodReference.getFormalTypes().iterator();
    if (formalTypesIterator.hasNext()) {
      builder.append(formalTypesIterator.next().getTypeName());
      while (formalTypesIterator.hasNext()) {
        builder.append(", ").append(formalTypesIterator.next().getTypeName());
      }
    }
    return builder.append(")").toString();
  }
}
