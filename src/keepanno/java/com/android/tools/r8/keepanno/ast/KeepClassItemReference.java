// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Collection;
import java.util.Collections;

public abstract class KeepClassItemReference extends KeepItemReference {

  public static KeepClassItemReference fromBindingReference(
      KeepClassBindingReference bindingReference) {
    return new ClassBinding(bindingReference);
  }

  public static KeepClassItemReference fromClassItemPattern(KeepClassItemPattern classItemPattern) {
    return new ClassItem(classItemPattern);
  }

  public static KeepClassItemReference fromClassNamePattern(
      KeepQualifiedClassNamePattern classNamePattern) {
    return new ClassItem(
        KeepClassItemPattern.builder().setClassNamePattern(classNamePattern).build());
  }

  @Override
  public final KeepClassItemReference asClassItemReference() {
    return this;
  }

  public abstract Collection<KeepBindingReference> getBindingReferences();

  private static class ClassBinding extends KeepClassItemReference {
    private final KeepClassBindingReference bindingReference;

    private ClassBinding(KeepClassBindingReference bindingReference) {
      assert bindingReference != null;
      this.bindingReference = bindingReference;
    }

    @Override
    public KeepClassBindingReference asBindingReference() {
      return bindingReference;
    }

    @Override
    public Collection<KeepBindingReference> getBindingReferences() {
      return Collections.singletonList(bindingReference);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClassBinding)) {
        return false;
      }
      ClassBinding that = (ClassBinding) o;
      return bindingReference.equals(that.bindingReference);
    }

    @Override
    public int hashCode() {
      return bindingReference.hashCode();
    }

    @Override
    public String toString() {
      return bindingReference.toString();
    }
  }

  private static class ClassItem extends KeepClassItemReference {
    private final KeepClassItemPattern classItemPattern;

    private ClassItem(KeepClassItemPattern classItemPattern) {
      assert classItemPattern != null;
      this.classItemPattern = classItemPattern;
    }

    @Override
    public KeepItemPattern asItemPattern() {
      return classItemPattern;
    }

    @Override
    public KeepClassItemPattern asClassItemPattern() {
      return classItemPattern;
    }

    @Override
    public Collection<KeepBindingReference> getBindingReferences() {
      return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ClassItem)) {
        return false;
      }
      ClassItem someItem = (ClassItem) o;
      return classItemPattern.equals(someItem.classItemPattern);
    }

    @Override
    public int hashCode() {
      return classItemPattern.hashCode();
    }

    @Override
    public String toString() {
      return classItemPattern.toString();
    }
  }
}
