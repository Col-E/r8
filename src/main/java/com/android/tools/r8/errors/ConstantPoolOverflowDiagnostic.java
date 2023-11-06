// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;

/**
 * Diagnostic information about a class file which could not be generated as the size of the
 * required constant pool overflowed the limit.
 */
@KeepForApi
public class ConstantPoolOverflowDiagnostic extends ClassFileOverflowDiagnostic {

  private final int constantPoolSize;
  private final ClassReference clazz;

  public ConstantPoolOverflowDiagnostic(Origin origin, ClassReference clazz, int constantPoolSize) {
    super(origin);
    this.clazz = clazz;
    this.constantPoolSize = constantPoolSize;
  }

  /** Constant pool size of the clazz. */
  public int getConstantPoolSize() {
    return constantPoolSize;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Class "
        + clazz
        + " too large for class file."
        + " Constant pool size was "
        + getConstantPoolSize()
        + ".";
  }
}
