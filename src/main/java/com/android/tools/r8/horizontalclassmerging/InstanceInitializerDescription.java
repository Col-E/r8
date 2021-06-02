// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A simple abstraction of an instance initializer's code, which allows a parent constructor call
 * followed by a sequence of instance-put instructions.
 */
public class InstanceInitializerDescription {

  private final int arity;
  private final Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments;
  private final DexMethod parentConstructor;
  private final List<InstanceFieldInitializationInfo> parentConstructorArguments;

  InstanceInitializerDescription(
      int arity,
      Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments,
      DexMethod parentConstructor,
      List<InstanceFieldInitializationInfo> parentConstructorArguments) {
    this.arity = arity;
    this.instanceFieldAssignments = instanceFieldAssignments;
    this.parentConstructor = parentConstructor;
    this.parentConstructorArguments = parentConstructorArguments;
  }

  public static Builder builder(ProgramMethod instanceInitializer) {
    return new Builder(instanceInitializer);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    InstanceInitializerDescription description = (InstanceInitializerDescription) obj;
    return arity == description.arity
        && instanceFieldAssignments.equals(description.instanceFieldAssignments)
        && parentConstructor == description.parentConstructor
        && parentConstructorArguments.equals(description.parentConstructorArguments);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        arity, instanceFieldAssignments, parentConstructor, parentConstructorArguments);
  }

  public static class Builder {

    private final int arity;

    private Map<DexField, InstanceFieldInitializationInfo> instanceFieldAssignments =
        new LinkedHashMap<>();
    private DexMethod parentConstructor;
    private List<InstanceFieldInitializationInfo> parentConstructorArguments;

    Builder(ProgramMethod method) {
      this.arity = method.getReference().getArity();
    }

    public void addInstancePut(DexField field, InstanceFieldInitializationInfo value) {
      instanceFieldAssignments.put(field, value);
    }

    public void addInvokeConstructor(
        DexMethod method, List<InstanceFieldInitializationInfo> arguments) {
      assert parentConstructor == null;
      assert parentConstructorArguments == null;
      parentConstructor = method;
      parentConstructorArguments = arguments;
    }

    public InstanceInitializerDescription build() {
      assert parentConstructor != null;
      return new InstanceInitializerDescription(
          arity, instanceFieldAssignments, parentConstructor, parentConstructorArguments);
    }
  }
}
