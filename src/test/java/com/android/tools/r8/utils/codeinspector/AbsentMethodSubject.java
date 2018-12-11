// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.naming.MemberNaming.Signature;

public class AbsentMethodSubject extends MethodSubject {

  @Override
  public boolean isPresent() {
    return false;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if an absent method has been renamed");
  }

  @Override
  public boolean isPublic() {
    throw new Unreachable("Cannot determine if an absent method is public");
  }

  @Override
  public boolean isStatic() {
    throw new Unreachable("Cannot determine if an absent method is static");
  }

  @Override
  public boolean isFinal() {
    throw new Unreachable("Cannot determine if an absent method is final");
  }

  @Override
  public boolean isAbstract() {
    throw new Unreachable("Cannot determine if an absent method is abstract");
  }

  @Override
  public boolean isBridge() {
    throw new Unreachable("Cannot determine if an absent method is a bridge");
  }

  @Override
  public boolean isInstanceInitializer() {
    throw new Unreachable("Cannot determine if an absent method is an instance initializer");
  }

  @Override
  public boolean isClassInitializer() {
    throw new Unreachable("Cannot determine if an absent method is a class initializer");
  }

  @Override
  public DexEncodedMethod getMethod() {
    return null;
  }

  @Override
  public Signature getOriginalSignature() {
    return null;
  }

  @Override
  public Signature getFinalSignature() {
    return null;
  }

  @Override
  public String getOriginalSignatureAttribute() {
    return null;
  }

  @Override
  public String getFinalSignatureAttribute() {
    return null;
  }

  @Override
  public LineNumberTable getLineNumberTable() {
    return null;
  }

  @Override
  public boolean hasLocalVariableTable() {
    throw new Unreachable("Cannot determine if an absent method has a local variable table");
  }
}
