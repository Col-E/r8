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

  public ClassTypeLatticeElement(DexType classType, boolean isNullable, Set<DexType> interfaces) {
    super(classType, isNullable, interfaces);
    assert classType.isClassType();
  }

  public DexType getClassType() {
    return type;
  }

  Set<DexType> getInterfaces(AppInfo appInfo) {
    if (interfaces != null) {
      return interfaces;
    }
    synchronized (this) {
      if (interfaces == null) {
        interfaces = type.implementedInterfaces(appInfo);
        interfaces =
            TypeLatticeElement.computeLeastUpperBoundOfInterfaces(appInfo, interfaces, interfaces);
      }
    }
    return interfaces;
  }

  @Override
  public ReferenceTypeLatticeElement getOrCreateDualLattice() {
    if (dual != null) {
      return dual;
    }
    synchronized (this) {
      if (dual == null) {
        ClassTypeLatticeElement dual = new ClassTypeLatticeElement(type, !isNullable(), interfaces);
        linkDualLattice(this, dual);
      }
    }
    return this.dual;
  }

  @Override
  public TypeLatticeElement asNullable() {
    return isNullable() ? this : getOrCreateDualLattice();
  }

  @Override
  public TypeLatticeElement asNonNullable() {
    return !isNullable() ? this : getOrCreateDualLattice();
  }

  @Override
  public boolean isClassType() {
    return true;
  }

  @Override
  public ClassTypeLatticeElement asClassTypeLatticeElement() {
    return this;
  }

}
