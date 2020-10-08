// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerCheckCastAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerExceptionGuardAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerInstanceOfAnalysis;
import com.google.common.collect.Sets;
import java.util.Set;

public class ClassMergingEnqueuerExtension
    implements EnqueuerInstanceOfAnalysis,
        EnqueuerCheckCastAnalysis,
        EnqueuerExceptionGuardAnalysis {

  private final Set<DexType> instanceOfTypes = Sets.newIdentityHashSet();
  private final Set<DexType> checkCastTypes = Sets.newIdentityHashSet();
  private final Set<DexType> exceptionGuardTypes = Sets.newIdentityHashSet();
  private final DexItemFactory factory;

  public ClassMergingEnqueuerExtension(DexItemFactory factory) {
    this.factory = factory;
  }

  @Override
  public void traceCheckCast(DexType type, ProgramMethod context) {
    checkCastTypes.add(type.toBaseType(factory));
  }

  @Override
  public void traceInstanceOf(DexType type, ProgramMethod context) {
    instanceOfTypes.add(type.toBaseType(factory));
  }

  @Override
  public void traceExceptionGuard(DexType guard, ProgramMethod context) {
    exceptionGuardTypes.add(guard);
  }

  public boolean isCheckCastType(DexProgramClass clazz) {
    return checkCastTypes.contains(clazz.type);
  }

  public boolean isInstanceOfType(DexProgramClass clazz) {
    return instanceOfTypes.contains(clazz.type);
  }

  public boolean isExceptionGuardType(DexProgramClass clazz) {
    return exceptionGuardTypes.contains(clazz.type);
  }

  public boolean isRuntimeCheckType(DexProgramClass clazz) {
    return isInstanceOfType(clazz) || isCheckCastType(clazz) || isExceptionGuardType(clazz);
  }

  public void attach(Enqueuer enqueuer) {
    enqueuer
        .registerInstanceOfAnalysis(this)
        .registerCheckCastAnalysis(this)
        .registerExceptionGuardAnalysis(this);
  }
}
