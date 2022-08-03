// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.List;
import java.util.Objects;

public class WrapperDescriptor implements SpecificationDescriptor {
  private final List<DexMethod> methods;
  private final List<DexType> subwrappers;
  private final boolean nonPublicAccess;

  public WrapperDescriptor(
      List<DexMethod> methods, List<DexType> directSubtypes, boolean nonPublicAccess) {
    this.methods = methods;
    this.subwrappers = directSubtypes;
    this.nonPublicAccess = nonPublicAccess;
  }

  public List<DexMethod> getMethods() {
    return methods;
  }

  public List<DexType> getSubwrappers() {
    return subwrappers;
  }

  public boolean hasNonPublicAccess() {
    return nonPublicAccess;
  }

  @Override
  public Object[] toJsonStruct(
      MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter exporter) {
    return exporter.exportWrapperDescriptor(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WrapperDescriptor)) {
      return false;
    }
    WrapperDescriptor that = (WrapperDescriptor) o;
    return nonPublicAccess == that.nonPublicAccess
        && Objects.equals(methods, that.methods)
        && Objects.equals(subwrappers, that.subwrappers);
  }

  @Override
  public int hashCode() {
    return Objects.hash(methods, subwrappers, nonPublicAccess);
  }
}
