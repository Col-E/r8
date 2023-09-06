// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;

public abstract class KeepItemReference {

  public static KeepItemReference fromBindingReference(BindingSymbol bindingReference) {
    return new BindingReference(bindingReference);
  }

  public static KeepItemReference fromItemPattern(KeepItemPattern itemPattern) {
    return new SomeItem(itemPattern);
  }

  public boolean isBindingReference() {
    return asBindingReference() != null;
  }

  public boolean isItemPattern() {
    return asItemPattern() != null;
  }

  public BindingSymbol asBindingReference() {
    return null;
  }

  public KeepItemPattern asItemPattern() {
    return null;
  }

  public abstract KeepItemPattern lookupItemPattern(KeepBindings bindings);

  private static class BindingReference extends KeepItemReference {
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
    public KeepItemPattern lookupItemPattern(KeepBindings bindings) {
      return bindings.get(bindingReference).getItem();
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
      BindingReference that = (BindingReference) o;
      return bindingReference.equals(that.bindingReference);
    }

    @Override
    public int hashCode() {
      return bindingReference.hashCode();
    }

    @Override
    public String toString() {
      return "reference='" + bindingReference + "'";
    }
  }

  private static class SomeItem extends KeepItemReference {
    private final KeepItemPattern itemPattern;

    private SomeItem(KeepItemPattern itemPattern) {
      assert itemPattern != null;
      this.itemPattern = itemPattern;
    }

    @Override
    public KeepItemPattern asItemPattern() {
      return itemPattern;
    }

    @Override
    public KeepItemPattern lookupItemPattern(KeepBindings bindings) {
      return asItemPattern();
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
      SomeItem someItem = (SomeItem) o;
      return itemPattern.equals(someItem.itemPattern);
    }

    @Override
    public int hashCode() {
      return itemPattern.hashCode();
    }

    @Override
    public String toString() {
      return itemPattern.toString();
    }
  }
}
