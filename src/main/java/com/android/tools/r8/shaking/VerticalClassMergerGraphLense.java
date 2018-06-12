// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

// This graph lense is instantiated during vertical class merging. The graph lense is context
// sensitive in the enclosing class of a given invoke *and* the type of the invoke (e.g., invoke-
// super vs invoke-virtual). This is illustrated by the following example.
//
// public class A {
//   public void m() { ... }
// }
// public class B extends A {
//   @Override
//   public void m() { invoke-super A.m(); ... }
//
//   public void m2() { invoke-virtual A.m(); ... }
// }
//
// Vertical class merging will merge class A into class B. Since class B already has a method with
// the signature "void B.m()", the method A.m will be given a fresh name and moved to class B.
// During this process, the method corresponding to A.m will be made private such that it can be
// called via an invoke-direct instruction.
//
// For the invocation "invoke-super A.m()" in B.m, this graph lense will return the newly created,
// private method corresponding to A.m (that is now in B.m with a fresh name), such that the
// invocation will hit the same implementation as the original super.m() call.
//
// For the invocation "invoke-virtual A.m()" in B.m2, this graph lense will return the method B.m.
public class VerticalClassMergerGraphLense extends GraphLense {
  private final GraphLense previousLense;

  public VerticalClassMergerGraphLense(GraphLense previousLense) {
    this.previousLense = previousLense;
  }

  @Override
  public DexType lookupType(DexType type) {
    return previousLense.lookupType(type);
  }

  @Override
  public DexMethod lookupMethod(DexMethod method, DexEncodedMethod context, Type type) {
    // TODO(christofferqa): If [type] is Type.SUPER and [method] has been merged into the class of
    // [context], then return the DIRECT method that has been created for [method] by SimpleClass-
    // Merger. Otherwise, return the VIRTUAL method corresponding to [method].
    return previousLense.lookupMethod(method, context, type);
  }

  @Override
  public Set<DexMethod> lookupMethodInAllContexts(DexMethod method) {
    DexMethod result = lookupMethod(method);
    if (result != null) {
      return ImmutableSet.of(result);
    }
    return ImmutableSet.of();
  }

  @Override
  public DexField lookupField(DexField field) {
    return previousLense.lookupField(field);
  }

  @Override
  public boolean isContextFreeForMethods() {
    return false;
  }

  @Override
  public boolean isContextFreeForMethod(DexMethod method) {
    // TODO(christofferqa): Should return false for methods where this graph lense is context
    // sensitive.
    return true;
  }
}
