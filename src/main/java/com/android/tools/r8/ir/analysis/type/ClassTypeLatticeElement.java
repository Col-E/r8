// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import java.util.Set;

public class ClassTypeLatticeElement extends ReferenceTypeLatticeElement {

  ClassTypeLatticeElement(DexType classType, boolean isNullable) {
    super(classType, isNullable);
    assert classType.isClassType();
  }

  ClassTypeLatticeElement(DexType classType, boolean isNullable, Set<DexType> interfaces) {
    super(classType, isNullable, interfaces);
    assert classType.isClassType();
  }

  public DexType getClassType() {
    return type;
  }

  Set<DexType> getInterfaces() {
    return interfaces;
  }

  @Override
  TypeLatticeElement asNullable() {
    return isNullable() ? this : new ClassTypeLatticeElement(type, true, interfaces);
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return isNullable() ? new ClassTypeLatticeElement(type, false, interfaces) : this;
  }

  @Override
  public boolean isClassType() {
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

}
