// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerCheckCastAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerExceptionGuardAnalysis;
import com.android.tools.r8.graph.analysis.EnqueuerInstanceOfAnalysis;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Function;

public class RuntimeTypeCheckInfo {

  private final Set<DexType> instanceOfTypes;
  private final Set<DexType> checkCastTypes;
  private final Set<DexType> exceptionGuardTypes;

  public RuntimeTypeCheckInfo(
      Set<DexType> instanceOfTypes, Set<DexType> checkCastTypes, Set<DexType> exceptionGuardTypes) {
    this.instanceOfTypes = instanceOfTypes;
    this.checkCastTypes = checkCastTypes;
    this.exceptionGuardTypes = exceptionGuardTypes;
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

  public RuntimeTypeCheckInfo rewriteWithLens(
      NonIdentityGraphLens graphLens, GraphLens appliedGraphLens) {
    Function<DexType, DexType> typeRewriter = type -> graphLens.lookupType(type, appliedGraphLens);
    return new RuntimeTypeCheckInfo(
        SetUtils.mapIdentityHashSet(instanceOfTypes, typeRewriter),
        SetUtils.mapIdentityHashSet(checkCastTypes, typeRewriter),
        SetUtils.mapIdentityHashSet(exceptionGuardTypes, typeRewriter));
  }

  public static class Builder
      implements EnqueuerInstanceOfAnalysis,
          EnqueuerCheckCastAnalysis,
          EnqueuerExceptionGuardAnalysis {

    private final GraphLens appliedGraphLens;
    private final DexItemFactory factory;

    private final Set<DexType> instanceOfTypes = Sets.newIdentityHashSet();
    private final Set<DexType> checkCastTypes = Sets.newIdentityHashSet();
    private final Set<DexType> exceptionGuardTypes = Sets.newIdentityHashSet();

    public Builder(AppView<?> appView) {
      this.appliedGraphLens = appView.graphLens();
      this.factory = appView.dexItemFactory();
    }

    public RuntimeTypeCheckInfo build(GraphLens graphLens) {
      RuntimeTypeCheckInfo runtimeTypeCheckInfo =
          new RuntimeTypeCheckInfo(instanceOfTypes, checkCastTypes, exceptionGuardTypes);
      return graphLens.isNonIdentityLens() && graphLens != appliedGraphLens
          ? runtimeTypeCheckInfo.rewriteWithLens(graphLens.asNonIdentityLens(), appliedGraphLens)
          : runtimeTypeCheckInfo;
    }

    @Override
    public void traceCheckCast(DexType type, ProgramMethod context) {
      add(type, checkCastTypes);
    }

    @Override
    public void traceSafeCheckCast(DexType type, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void traceInstanceOf(DexType type, ProgramMethod context) {
      add(type, instanceOfTypes);
    }

    @Override
    public void traceExceptionGuard(DexType guard, ProgramMethod context) {
      add(guard, exceptionGuardTypes);
    }

    private void add(DexType type, Set<DexType> set) {
      DexType baseType = type.toBaseType(factory);
      if (baseType.isClassType()) {
        set.add(baseType);
      }
    }

    public void attach(Enqueuer enqueuer) {
      enqueuer
          .registerInstanceOfAnalysis(this)
          .registerCheckCastAnalysis(this)
          .registerExceptionGuardAnalysis(this);
    }
  }
}
