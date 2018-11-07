// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import java.util.Collections;
import java.util.Set;

public class ReferenceTypeLatticeElement extends TypeLatticeElement {
  private static final ReferenceTypeLatticeElement NULL_INSTANCE =
      new ReferenceTypeLatticeElement(DexItemFactory.nullValueType, true);

  final DexType type;

  // Link between maybe-null and definitely-not-null reference type lattices.
  ReferenceTypeLatticeElement dual;

  public ReferenceTypeLatticeElement getOrCreateDualLattice() {
    throw new Unreachable("Should be defined/used by class/array types.");
  }

  static void linkDualLattice(ReferenceTypeLatticeElement t1, ReferenceTypeLatticeElement t2) {
    assert t1.dual == null && t2.dual == null;
    t1.dual = t2;
    t2.dual = t1;
  }

  ReferenceTypeLatticeElement(DexType type, boolean isNullable) {
    super(isNullable);
    this.type = type;
  }

  static ReferenceTypeLatticeElement getNullTypeLatticeElement() {
    return NULL_INSTANCE;
  }

  public Set<DexType> getInterfaces() {
    return Collections.emptySet();
  }

  @Override
  public boolean isNull() {
    return type == DexItemFactory.nullValueType;
  }

  @Override
  public TypeLatticeElement asNullable() {
    assert isNull();
    return this;
  }

  @Override
  public boolean isReference() {
    return true;
  }

  @Override
  public String toString() {
    return isNullableString() + type.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ReferenceTypeLatticeElement)) {
      return false;
    }
    ReferenceTypeLatticeElement other = (ReferenceTypeLatticeElement) o;
    if (this.isNullable() != other.isNullable()) {
      return false;
    }
    if (!type.equals(other.type)) {
      return false;
    }
    Set<DexType> thisInterfaces = getInterfaces();
    Set<DexType> otherInterfaces = other.getInterfaces();
    if (thisInterfaces.size() != otherInterfaces.size()) {
      return false;
    }
    return thisInterfaces.containsAll(otherInterfaces);
  }

  @Override
  public int hashCode() {
    assert isNull();
    return System.identityHashCode(this);
  }
}
