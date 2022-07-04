// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.string.utils;

import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.function.Predicate;

public class StringBuilderCodeMatchers {

  public static Predicate<InstructionSubject> isInvokeStringBuilderAppendWithString() {
    return CodeMatchers.isInvokeWithTarget(
        "java.lang.StringBuilder", "java.lang.StringBuilder", "append", "java.lang.String");
  }

  public static long countStringBuilderInits(FoundMethodSubject method) {
    return countInstructionsOnStringBuilder(method, "<init>");
  }

  public static long countStringBuilderAppends(FoundMethodSubject method) {
    return countInstructionsOnStringBuilder(method, "append");
  }

  public static long countInstructionsOnStringBuilder(
      FoundMethodSubject method, String methodName) {
    return method
        .streamInstructions()
        .filter(
            instructionSubject ->
                CodeMatchers.isInvokeWithTarget(StringBuilder.class.getTypeName(), methodName)
                    .test(instructionSubject))
        .count();
  }
}
