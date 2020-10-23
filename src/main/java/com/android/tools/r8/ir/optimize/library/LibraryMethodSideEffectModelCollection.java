// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class LibraryMethodSideEffectModelCollection {

  private final Map<DexMethod, Predicate<InvokeMethod>> finalMethodsWithoutSideEffects;
  private final Set<DexMethod> nonFinalMethodsWithoutSideEffects;

  public LibraryMethodSideEffectModelCollection(DexItemFactory dexItemFactory) {
    finalMethodsWithoutSideEffects = buildFinalMethodsWithoutSideEffects(dexItemFactory);
    nonFinalMethodsWithoutSideEffects = buildNonFinalMethodsWithoutSideEffects(dexItemFactory);
  }

  private static Map<DexMethod, Predicate<InvokeMethod>> buildFinalMethodsWithoutSideEffects(
      DexItemFactory dexItemFactory) {
    ImmutableMap.Builder<DexMethod, Predicate<InvokeMethod>> builder =
        ImmutableMap.<DexMethod, Predicate<InvokeMethod>>builder()
            .put(dexItemFactory.enumMembers.constructor, alwaysTrue())
            .put(dexItemFactory.npeMethods.init, alwaysTrue())
            .put(dexItemFactory.npeMethods.initWithMessage, alwaysTrue())
            .put(dexItemFactory.objectMembers.constructor, alwaysTrue())
            .put(dexItemFactory.objectMembers.getClass, alwaysTrue())
            .put(dexItemFactory.stringMembers.hashCode, alwaysTrue());
    putAll(builder, dexItemFactory.classMethods.getNames, alwaysTrue());
    putAll(
        builder,
        dexItemFactory.stringBufferMethods.constructorMethods,
        dexItemFactory.stringBufferMethods::constructorInvokeIsSideEffectFree);
    putAll(
        builder,
        dexItemFactory.stringBuilderMethods.constructorMethods,
        dexItemFactory.stringBuilderMethods::constructorInvokeIsSideEffectFree);
    putAll(builder, dexItemFactory.boxedValueOfMethods(), alwaysTrue());
    return builder.build();
  }

  private static Set<DexMethod> buildNonFinalMethodsWithoutSideEffects(
      DexItemFactory dexItemFactory) {
    return ImmutableSet.of(
        dexItemFactory.objectMembers.equals,
        dexItemFactory.objectMembers.hashCode,
        dexItemFactory.objectMembers.toString);
  }

  private static void putAll(
      ImmutableMap.Builder<DexMethod, Predicate<InvokeMethod>> builder,
      Iterable<DexMethod> methods,
      Predicate<InvokeMethod> predicate) {
    for (DexMethod method : methods) {
      builder.put(method, predicate);
    }
  }

  public boolean isCallToSideEffectFreeFinalMethod(InvokeMethod invoke) {
    return finalMethodsWithoutSideEffects
        .getOrDefault(invoke.getInvokedMethod(), alwaysFalse())
        .test(invoke);
  }

  // This intentionally takes the invoke instruction since the determination of whether a library
  // method has side effects may depend on the arguments.
  public boolean isSideEffectFree(InvokeMethod invoke, LibraryMethod singleTarget) {
    return isCallToSideEffectFreeFinalMethod(invoke)
        || nonFinalMethodsWithoutSideEffects.contains(singleTarget.getReference());
  }
}
