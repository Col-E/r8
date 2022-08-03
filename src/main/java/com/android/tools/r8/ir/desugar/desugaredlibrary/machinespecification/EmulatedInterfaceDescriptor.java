// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import java.util.Map;
import java.util.Objects;

public class EmulatedInterfaceDescriptor implements SpecificationDescriptor {
  private final DexType rewrittenType;
  private final Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedMethods;

  public EmulatedInterfaceDescriptor(
      DexType rewrittenType, Map<DexMethod, EmulatedDispatchMethodDescriptor> emulatedMethods) {
    this.rewrittenType = rewrittenType;
    this.emulatedMethods = emulatedMethods;
  }

  public DexType getRewrittenType() {
    return rewrittenType;
  }

  public Map<DexMethod, EmulatedDispatchMethodDescriptor> getEmulatedMethods() {
    return emulatedMethods;
  }

  @Override
  public Object[] toJsonStruct(
      MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter exporter) {
    return exporter.exportEmulatedInterfaceDescriptor(this);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof EmulatedInterfaceDescriptor)) {
      return false;
    }
    EmulatedInterfaceDescriptor that = (EmulatedInterfaceDescriptor) o;
    return rewrittenType == that.rewrittenType && emulatedMethods.equals(that.emulatedMethods);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rewrittenType, emulatedMethods);
  }
}
