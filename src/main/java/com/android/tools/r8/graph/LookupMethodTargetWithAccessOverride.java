// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public class LookupMethodTargetWithAccessOverride implements LookupMethodTarget {

  private final DexClassAndMethod target;
  private final DexClassAndMethod accessOverride;

  public LookupMethodTargetWithAccessOverride(
      DexClassAndMethod target, DexClassAndMethod accessOverride) {
    this.target = target;
    this.accessOverride = accessOverride;
  }

  @Override
  public LookupTarget toLookupTarget(DexClassAndMethod classAndMethod) {
    return new LookupMethodTargetWithAccessOverride(classAndMethod, accessOverride);
  }

  @Override
  public DexClassAndMethod getAccessOverride() {
    return accessOverride;
  }

  @Override
  public DexClass getHolder() {
    return target.getHolder();
  }

  @Override
  public DexMethod getReference() {
    return target.getReference();
  }

  @Override
  public DexEncodedMethod getDefinition() {
    return target.getDefinition();
  }

  @Override
  public DexClassAndMethod getTarget() {
    return target;
  }
}
