// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.util.Objects;

/** Internal encoding of a method that allows for having either basic info or full info. */
abstract class MethodDefinition implements Definition {

  static MethodDefinition create(ClassReference classReference, String methodName) {
    return new BaseMethodDefinition(classReference, methodName);
  }

  static MethodDefinition create(MethodReference methodReference) {
    return new FullMethodDefinition(methodReference);
  }

  boolean isFullMethodDefinition() {
    return false;
  }

  FullMethodDefinition asFullMethodDefinition() {
    return null;
  }

  abstract MethodDefinition substituteHolder(ClassReference newHolder);

  static class BaseMethodDefinition extends MethodDefinition {

    private final ClassReference classReference;
    private final String name;

    private BaseMethodDefinition(ClassReference classReference, String name) {
      this.classReference = classReference;
      this.name = name;
    }

    @Override
    public ClassReference getHolderClass() {
      return classReference;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    MethodDefinition substituteHolder(ClassReference newHolder) {
      return MethodDefinition.create(newHolder, name);
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BaseMethodDefinition that = (BaseMethodDefinition) o;
      return classReference.equals(that.classReference) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classReference, name);
    }
  }

  static class FullMethodDefinition extends MethodDefinition {

    private final MethodReference methodReference;

    private FullMethodDefinition(MethodReference methodReference) {
      this.methodReference = methodReference;
    }

    @Override
    public ClassReference getHolderClass() {
      return methodReference.getHolderClass();
    }

    @Override
    public String getName() {
      return methodReference.getMethodName();
    }

    @Override
    boolean isFullMethodDefinition() {
      return true;
    }

    @Override
    FullMethodDefinition asFullMethodDefinition() {
      return this;
    }

    @Override
    MethodDefinition substituteHolder(ClassReference newHolder) {
      return MethodDefinition.create(
          Reference.method(
              newHolder,
              methodReference.getMethodName(),
              methodReference.getFormalTypes(),
              methodReference.getReturnType()));
    }

    MethodReference getMethodReference() {
      return methodReference;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      FullMethodDefinition that = (FullMethodDefinition) o;
      return methodReference.equals(that.methodReference);
    }

    @Override
    public int hashCode() {
      return methodReference.hashCode();
    }
  }
}
