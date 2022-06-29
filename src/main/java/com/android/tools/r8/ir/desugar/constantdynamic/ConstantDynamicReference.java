// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.constantdynamic;

import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import java.util.List;
import java.util.Objects;

public class ConstantDynamicReference {
  private final DexString name;
  private final DexType type;
  private final DexMethodHandle bootstrapMethod;
  private final List<DexValue> bootstrapMethodArguments;

  public ConstantDynamicReference(
      DexString name,
      DexType type,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapMethodArguments) {
    assert bootstrapMethodArguments.isEmpty();
    this.name = name;
    this.type = type;
    this.bootstrapMethod = bootstrapMethod;
    this.bootstrapMethodArguments = bootstrapMethodArguments;
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
    if (this == obj) return true;
    if (!(obj instanceof ConstantDynamicReference)) {
      return false;
    }
    ConstantDynamicReference other = (ConstantDynamicReference) obj;
    return Objects.equals(name, other.name)
        && Objects.equals(type, other.type)
        && Objects.equals(bootstrapMethod, other.bootstrapMethod);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, type, bootstrapMethod);
  }
}
