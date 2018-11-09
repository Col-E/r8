// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexType;
import java.util.Set;
import java.util.stream.Collectors;

public class ClassTypeLatticeElement extends ReferenceTypeLatticeElement {

  private Set<DexType> lazyInterfaces;
  private AppInfo appInfoForLazyInterfacesComputation;

  public ClassTypeLatticeElement(DexType classType, boolean isNullable, Set<DexType> interfaces) {
    this(classType, isNullable, interfaces, null);
  }

  public ClassTypeLatticeElement(DexType classType, boolean isNullable, AppInfo appInfo) {
    this(classType, isNullable, null, appInfo);
  }

  private ClassTypeLatticeElement(
      DexType classType, boolean isNullable, Set<DexType> interfaces, AppInfo appInfo) {
    super(classType, isNullable);
    assert classType.isClassType();
    appInfoForLazyInterfacesComputation = appInfo;
    lazyInterfaces = interfaces;
  }

  public DexType getClassType() {
    return type;
  }

  @Override
  public Set<DexType> getInterfaces() {
    if (lazyInterfaces != null) {
      return lazyInterfaces;
    }
    synchronized (this) {
      if (lazyInterfaces == null) {
        Set<DexType> itfs = type.implementedInterfaces(appInfoForLazyInterfacesComputation);
        lazyInterfaces =
            TypeLatticeElement.computeLeastUpperBoundOfInterfaces(
                appInfoForLazyInterfacesComputation, itfs, itfs);
        appInfoForLazyInterfacesComputation = null;
      }
    }
    return lazyInterfaces;
  }

  @Override
  public ReferenceTypeLatticeElement getOrCreateDualLattice() {
    if (dual != null) {
      return dual;
    }
    synchronized (this) {
      if (dual == null) {
        ClassTypeLatticeElement dual =
            new ClassTypeLatticeElement(
                type, !isNullable(), lazyInterfaces, appInfoForLazyInterfacesComputation);
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
  public boolean isBasedOnMissingClass(AppInfo appInfo) {
    return getClassType().isMissingOrHasMissingSuperType(appInfo)
        || getInterfaces().stream().anyMatch(type -> type.isMissingOrHasMissingSuperType(appInfo));
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
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append(super.toString());
    builder.append(" [");
    builder.append(
        getInterfaces().stream().map(DexType::toString).collect(Collectors.joining(", ")));
    builder.append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    // The interfaces of a type do not contribute to its hashCode as they are lazily computed.
    return (isNullable() ? 1 : -1) * type.hashCode();
  }
}
