// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.transformers;

import static org.objectweb.asm.Opcodes.ASM7;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import org.objectweb.asm.MethodVisitor;

/**
 * Class for transforming the content of a method.
 *
 * <p>This is just a simple wrapper on the ASM MethodVisitor interface with some added methods for
 * obtaining context information.
 */
public class MethodTransformer extends MethodVisitor {

  static class MethodContext {
    public final MethodReference method;
    public final int accessFlags;

    public MethodContext(MethodReference method, int accessFlags) {
      this.method = method;
      this.accessFlags = accessFlags;
    }
  }

  private MethodContext context;

  public MethodTransformer() {
    super(ASM7, null);
  }

  public ClassReference getHolder() {
    return getContext().method.getHolderClass();
  }

  public MethodReference getMethod() {
    return getContext().method;
  }

  // Package internals.

  MethodContext getContext() {
    return context;
  }

  void setSubVisitor(MethodVisitor visitor) {
    this.mv = visitor;
  }

  void setContext(MethodContext context) {
    this.context = context;
  }
}
