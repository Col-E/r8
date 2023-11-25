// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import com.android.tools.r8.graph.DexMethod;
import java.util.Objects;

public class CustomConversionDescriptor implements SpecificationDescriptor {
  private final DexMethod to;
  private final DexMethod from;

  @SuppressWarnings("ReferenceEquality")
  public CustomConversionDescriptor(DexMethod to, DexMethod from) {
    this.to = to;
    this.from = from;
    assert to.getReturnType() == from.getArgumentType(0, true);
    assert from.getReturnType() == to.getArgumentType(0, true);
  }

  public DexMethod getTo() {
    return to;
  }

  public DexMethod getFrom() {
    return from;
  }

  @Override
  public Object[] toJsonStruct(
      MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter exporter) {
    return exporter.exportCustomConversionDescriptor(this);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof CustomConversionDescriptor)) {
      return false;
    }
    CustomConversionDescriptor that = (CustomConversionDescriptor) o;
    return to == that.to && from == that.from;
  }

  @Override
  public int hashCode() {
    return Objects.hash(to, from);
  }
}
