// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class ReferenceTypeLatticeElement extends TypeLatticeElement {
  private static final ReferenceTypeLatticeElement NULL_INSTANCE =
      new ReferenceTypeLatticeElement(DexItemFactory.nullValueType, true);
  private static final ReferenceTypeLatticeElement REFERENCE_INSTANCE =
      new ReferenceTypeLatticeElement(DexItemFactory.unknownType, true);

  final DexType type;
  Set<DexType> interfaces;

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
    this(type, isNullable, ImmutableSet.of());
  }

  ReferenceTypeLatticeElement(DexType type, boolean isNullable, Set<DexType> interfaces) {
    super(isNullable);
    this.type = type;
    this.interfaces = interfaces == null ? null : Collections.unmodifiableSet(interfaces);
  }

  static ReferenceTypeLatticeElement getNullTypeLatticeElement() {
    return NULL_INSTANCE;
  }

  static ReferenceTypeLatticeElement getReferenceTypeLatticeElement() {
    return REFERENCE_INSTANCE;
  }

  @Override
  public boolean isNull() {
    return type == DexItemFactory.nullValueType;
  }

  @Override
  public boolean isReferenceInstance() {
    return type == DexItemFactory.unknownType;
  }

  @Override
  public TypeLatticeElement asNullable() {
    assert isNull() || isReferenceInstance();
    return this;
  }

  @Override
  public boolean isReference() {
    return true;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return isNull() ? this : BOTTOM;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(isNullableString()).append(type.toString());
    if (interfaces != null) {
      builder.append(" [");
      builder.append(
          interfaces.stream().map(DexType::toString).collect(Collectors.joining(", ")));
      builder.append("]");
    }
    return builder.toString();
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
    if (interfaces == null || other.interfaces == null) {
      return interfaces == other.interfaces;
    }
    if (interfaces.size() != other.interfaces.size()) {
      return false;
    }
    return interfaces.containsAll(other.interfaces);
  }

  @Override
  public int hashCode() {
    assert isNull() || isReferenceInstance();
    return this.hashCode();
  }
}
