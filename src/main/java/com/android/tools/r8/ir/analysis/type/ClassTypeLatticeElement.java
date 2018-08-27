// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.ImmutableSet;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassTypeLatticeElement extends TypeLatticeElement {
  private final DexType classType;
  private final Set<DexType> interfaces;

  ClassTypeLatticeElement(DexType classType, boolean isNullable) {
    this(classType, isNullable, ImmutableSet.of());
  }

  ClassTypeLatticeElement(DexType classType, boolean isNullable, Set<DexType> interfaces) {
    super(isNullable);
    assert classType.isClassType();
    this.classType = classType;
    this.interfaces = Collections.unmodifiableSet(interfaces);
  }

  public DexType getClassType() {
    return classType;
  }

  Set<DexType> getInterfaces() {
    return interfaces;
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new ClassTypeLatticeElement(classType, true, interfaces);
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return isNullable() ? new ClassTypeLatticeElement(classType, false, interfaces) : this;
  }

  @Override
  public boolean isClassTypeLatticeElement() {
    return true;
  }

  @Override
  public ClassTypeLatticeElement asClassTypeLatticeElement() {
    return this;
  }

  @Override
  public TypeLatticeElement arrayGet(AppInfo appInfo) {
    return objectType(appInfo, true);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(isNullableString()).append(classType.toString());
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
    ClassTypeLatticeElement other = (ClassTypeLatticeElement) o;
    if (!classType.equals(other.classType)) {
      return false;
    }
    if (interfaces.size() != other.interfaces.size()) {
      return false;
    }
    return interfaces.containsAll(other.interfaces);
  }

  @Override
  public int hashCode() {
    int prime = (!classType.isUnknown() && classType.isInterface()) ? 3 : 17;
    return super.hashCode() * classType.hashCode() * prime;
  }
}
