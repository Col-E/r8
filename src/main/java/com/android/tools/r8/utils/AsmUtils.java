// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.objectweb.asm.Opcodes.ACC_DEPRECATED;

public class AsmUtils {
  public static boolean isDeprecated(int access) {
    // ASM stores the Deprecated attribute
    // (https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.15) in the
    // access flags.
    return (access & ACC_DEPRECATED) == ACC_DEPRECATED;
  }

  public static int withDeprecated(int access) {
    // ASM stores the Deprecated attribute
    // (https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.7.15) in the
    // access flags.
    return access | ACC_DEPRECATED;
  }
}
