// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ClassMethodsBuilder {
  private Set<DexMethod> reservedMethods = Sets.newIdentityHashSet();
  private List<DexEncodedMethod> virtualMethods = new ArrayList<>();
  private List<DexEncodedMethod> directMethods = new ArrayList<>();

  public void addVirtualMethod(DexEncodedMethod virtualMethod) {
    virtualMethods.add(virtualMethod);
    boolean added = reservedMethods.add(virtualMethod.getReference());
    assert added;
  }

  public void addDirectMethod(DexEncodedMethod directMethod) {
    directMethods.add(directMethod);
    boolean added = reservedMethods.add(directMethod.getReference());
    assert added;
  }

  public boolean isFresh(DexMethod method) {
    return !reservedMethods.contains(method);
  }

  @SuppressWarnings("ReferenceEquality")
  public void setClassMethods(DexProgramClass clazz) {
    assert virtualMethods.stream().allMatch(method -> method.getHolderType() == clazz.type);
    assert virtualMethods.stream().allMatch(DexEncodedMethod::belongsToVirtualPool);
    assert directMethods.stream().allMatch(method -> method.getHolderType() == clazz.type);
    assert directMethods.stream().allMatch(DexEncodedMethod::belongsToDirectPool);
    clazz.setVirtualMethods(virtualMethods);
    clazz.setDirectMethods(directMethods);
  }
}
