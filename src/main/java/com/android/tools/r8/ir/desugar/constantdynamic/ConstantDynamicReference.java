// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.List;
import java.util.Objects;

public class ConstantDynamicReference implements StructuralItem<ConstantDynamicReference> {
  private final int symbolicReferenceId;
  private final DexString name;
  private final DexType type;
  private final DexMethodHandle bootstrapMethod;
  private final List<DexValue> bootstrapMethodArguments;

  private static void specify(StructuralSpecification<ConstantDynamicReference, ?> spec) {
    spec.withInt(c -> c.symbolicReferenceId);
  }

  public ConstantDynamicReference(
      int symbolicReferenceId,
      DexString name,
      DexType type,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapMethodArguments) {
    assert bootstrapMethodArguments.isEmpty();
    this.symbolicReferenceId = symbolicReferenceId;
    this.name = name;
    this.type = type;
    this.bootstrapMethod = bootstrapMethod;
    this.bootstrapMethodArguments = bootstrapMethodArguments;
  }

  @Override
  public ConstantDynamicReference self() {
    return this;
  }

  @Override
  public StructuralMapping<ConstantDynamicReference> getStructuralMapping() {
    return ConstantDynamicReference::specify;
  }

  public DexString getName() {
    return name;
  }

  public DexType getType() {
    return type;
  }

  public DexMethodHandle getBootstrapMethod() {
    return bootstrapMethod;
  }

  public List<DexValue> getBootstrapMethodArguments() {
    return bootstrapMethodArguments;
  }

  @Override
  public boolean equals(Object obj) {
    return Equatable.equalsImpl(this, obj);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, bootstrapMethod, bootstrapMethodArguments);
  }
}
