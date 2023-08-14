// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Predicate;

public abstract class KeepClassReference {

  public static KeepClassReference fromBindingReference(BindingSymbol bindingReference) {
    return new BindingReference(bindingReference);
  }

  public static KeepClassReference fromClassNamePattern(
      KeepQualifiedClassNamePattern classNamePattern) {
    return new SomeItem(classNamePattern);
  }

  public boolean isBindingReference() {
    return asBindingReference() != null;
  }

  public boolean isClassNamePattern() {
    return asClassNamePattern() != null;
  }

  public BindingSymbol asBindingReference() {
    return null;
  }

  public KeepQualifiedClassNamePattern asClassNamePattern() {
    return null;
  }

  public abstract Collection<BindingSymbol> getBindingReferences();

  public boolean isAny(Predicate<BindingSymbol> onReference) {
    return isBindingReference()
        ? onReference.test(asBindingReference())
        : asClassNamePattern().isAny();
  }

  private static class BindingReference extends KeepClassReference {
    private final BindingSymbol bindingReference;

    private BindingReference(BindingSymbol bindingReference) {
      assert bindingReference != null;
      this.bindingReference = bindingReference;
    }

    @Override
    public BindingSymbol asBindingReference() {
      return bindingReference;
    }

    @Override
    public Collection<BindingSymbol> getBindingReferences() {
      return Collections.singletonList(bindingReference);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      BindingReference that = (BindingReference) o;
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

  private static class SomeItem extends KeepClassReference {
    private final KeepQualifiedClassNamePattern classNamePattern;

    private SomeItem(KeepQualifiedClassNamePattern classNamePattern) {
      assert classNamePattern != null;
      this.classNamePattern = classNamePattern;
    }

    @Override
    public KeepQualifiedClassNamePattern asClassNamePattern() {
      return classNamePattern;
    }

    @Override
    public Collection<BindingSymbol> getBindingReferences() {
      return Collections.emptyList();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      SomeItem someItem = (SomeItem) o;
      return classNamePattern.equals(someItem.classNamePattern);
    }

    @Override
    public int hashCode() {
      return classNamePattern.hashCode();
    }

    @Override
    public String toString() {
      return classNamePattern.toString();
    }
  }
}
