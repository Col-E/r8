// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.transformers;

import static org.objectweb.asm.Opcodes.ASM7;

import org.objectweb.asm.ClassVisitor;

/**
 * Class for transforming the content of a class.
 *
 * <p>This is just a simple wrapper on the ASM ClassVisitor interface.
 */
public class ClassTransformer extends ClassVisitor {
  public ClassTransformer() {
    super(ASM7, null);
  }

  // Package internals.

  void setSubVisitor(ClassVisitor visitor) {
    this.cv = visitor;
  }
}
