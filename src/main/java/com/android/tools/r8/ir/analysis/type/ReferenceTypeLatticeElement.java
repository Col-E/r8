// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

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
  final Set<DexType> interfaces;

  ReferenceTypeLatticeElement(DexType type, boolean isNullable) {
    this(type, isNullable, ImmutableSet.of());
  }

  ReferenceTypeLatticeElement(DexType type, boolean isNullable, Set<DexType> interfaces) {
    super(isNullable);
    this.type = type;
    this.interfaces = Collections.unmodifiableSet(interfaces);
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
  TypeLatticeElement asNullable() {
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
    if (!interfaces.isEmpty()) {
      builder.append(" [");
      builder.append(
          interfaces.stream().map(DexType::toString).collect(Collectors.joining(", ")));
      builder.append("]");
    }
    return builder.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ReferenceTypeLatticeElement other = (ReferenceTypeLatticeElement) o;
    if (!type.equals(other.type)) {
      return false;
    }
    if (interfaces.size() != other.interfaces.size()) {
      return false;
    }
    return interfaces.containsAll(other.interfaces);
  }

  @Override
  public int hashCode() {
    int prime = type.isUnknown() ? 3 : (type.isArrayType() ? 7 : 5);
    return super.hashCode() * type.hashCode() * prime;
  }
}
