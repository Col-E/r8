// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;

public abstract class ReferenceTypeElement extends TypeElement {

  private static class NullElement extends ReferenceTypeElement {

    NullElement(Nullability nullability) {
      super(nullability);
    }

    @Override
    public ReferenceTypeElement getOrCreateVariant(Nullability nullability) {
      return nullability.isNullable() ? NULL_INSTANCE : NULL_BOTTOM_INSTANCE;
    }

    private static NullElement create() {
      return new NullElement(Nullability.definitelyNull());
    }

    private static NullElement createBottom() {
      return new NullElement(Nullability.bottom());
    }

    @Override
    public boolean isNullType() {
      return true;
    }

    @Override
    public String toString() {
      return nullability.toString() + " " + DexItemFactory.nullValueType.toString();
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof NullElement)) {
        return false;
      }
      return true;
    }
  }

  private static final ReferenceTypeElement NULL_INSTANCE = NullElement.create();
  private static final ReferenceTypeElement NULL_BOTTOM_INSTANCE = NullElement.createBottom();

  final Nullability nullability;

  ReferenceTypeElement(Nullability nullability) {
    this.nullability = nullability;
  }

  @Override
  public Nullability nullability() {
    return nullability;
  }

  static ReferenceTypeElement getNullType() {
    return NULL_INSTANCE;
  }

  public abstract ReferenceTypeElement getOrCreateVariant(Nullability nullability);

  public TypeElement asMeetWithNotNull() {
    return getOrCreateVariant(nullability.meet(Nullability.definitelyNotNull()));
  }

  public TypeElement asDefinitelyNotNull() {
    return getOrCreateVariant(Nullability.definitelyNotNull());
  }

  public TypeElement asMaybeNull() {
    return getOrCreateVariant(Nullability.maybeNull());
  }

  @Override
  public boolean isReferenceType() {
    return true;
  }

  @Override
  public ReferenceTypeElement asReferenceType() {
    return this;
  }

  @Override
  public boolean equals(Object o) {
    throw new Unreachable("Should be implemented on each sub type");
  }

  @Override
  public int hashCode() {
    throw new Unreachable("Should be implemented on each sub type");
  }
}
