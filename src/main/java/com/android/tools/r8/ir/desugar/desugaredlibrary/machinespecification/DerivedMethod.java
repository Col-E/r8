// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import java.util.Objects;

/**
 * A derived method is: - if the holderKind is null, a normal dexMethod; - if the holderKind is
 * non-null, a method derived from the dexMethod In that case the method holder is used as the
 * context to generate the holder type. The method may however differ (for example the method name
 * may be different).
 */
public class DerivedMethod implements SpecificationDescriptor {

  private final DexMethod method;
  private final SyntheticKind holderKind;

  public DerivedMethod(DexMethod method) {
    this(method, null);
  }

  public DerivedMethod(DexMethod method, SyntheticKind holderKind) {
    this.holderKind = holderKind;
    this.method = method;
  }

  public SyntheticKind getHolderKind() {
    return holderKind;
  }

  public DexType getHolderContext() {
    return method.getHolderType();
  }

  public DexMethod getMethod() {
    return method;
  }

  public DexString getName() {
    return method.getName();
  }

  public DexProto getProto() {
    return method.getProto();
  }

  @Override
  public Object[] toJsonStruct(
      MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter exporter) {
    return exporter.exportDerivedMethod(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof DerivedMethod)) {
      return false;
    }
    DerivedMethod that = (DerivedMethod) o;
    return method == that.method && Objects.equals(holderKind, that.holderKind);
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, holderKind);
  }
}
