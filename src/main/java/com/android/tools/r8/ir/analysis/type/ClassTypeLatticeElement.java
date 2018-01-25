// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;

public class ClassTypeLatticeElement extends TypeLatticeElement {
  private final DexType classType;

  ClassTypeLatticeElement(DexType classType, boolean isNullable) {
    super(isNullable);
    assert classType.isClassType();
    this.classType = classType;
  }

  public DexType getClassType() {
    return classType;
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new ClassTypeLatticeElement(classType, true);
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return isNullable() ? new ClassTypeLatticeElement(classType, false) : this;
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
  public TypeLatticeElement checkCast(AppInfo appInfo, DexType castType) {
    if (classType.isSubtypeOf(castType, appInfo)) {
      return this;
    }
    return fromDexType(castType, isNullable());
  }

  @Override
  public String toString() {
    return isNullableString() + classType.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    ClassTypeLatticeElement other = (ClassTypeLatticeElement) o;
    return classType == other.classType;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + classType.hashCode();
    return result;
  }
}
