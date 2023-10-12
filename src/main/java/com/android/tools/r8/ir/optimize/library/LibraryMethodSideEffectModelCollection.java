// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.LibraryMethod;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.library.sideeffects.JavaLangObjectsSideEffectCollection;
import com.android.tools.r8.utils.BiPredicateUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

public class LibraryMethodSideEffectModelCollection {

  private final Map<DexMethod, BiPredicate<DexMethod, List<Value>>> finalMethodsWithoutSideEffects;
  private final Set<DexMethod> unconditionalFinalMethodsWithoutSideEffects;

  private final Set<DexMethod> nonFinalMethodsWithoutSideEffects;

  public LibraryMethodSideEffectModelCollection(AppView<?> appView) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    finalMethodsWithoutSideEffects = buildFinalMethodsWithoutSideEffects(appView, dexItemFactory);
    unconditionalFinalMethodsWithoutSideEffects =
        buildUnconditionalFinalMethodsWithoutSideEffects(dexItemFactory);
    nonFinalMethodsWithoutSideEffects = buildNonFinalMethodsWithoutSideEffects(dexItemFactory);
  }

  private static Map<DexMethod, BiPredicate<DexMethod, List<Value>>>
      buildFinalMethodsWithoutSideEffects(AppView<?> appView, DexItemFactory dexItemFactory) {
    ImmutableMap.Builder<DexMethod, BiPredicate<DexMethod, List<Value>>> builder =
        ImmutableMap.<DexMethod, BiPredicate<DexMethod, List<Value>>>builder()
            .put(
                dexItemFactory.byteMembers.byteValue,
                (method, arguments) -> arguments.get(0).isNeverNull())
            .put(
                dexItemFactory.objectsMethods.toStringWithObject,
                (method, arguments) ->
                    !JavaLangObjectsSideEffectCollection.toStringMayHaveSideEffects(
                        appView, arguments))
            .put(
                dexItemFactory.stringMembers.constructor,
                (method, arguments) -> arguments.get(1).isNeverNull())
            .put(
                dexItemFactory.stringMembers.valueOf,
                (method, arguments) ->
                    !JavaLangObjectsSideEffectCollection.toStringMayHaveSideEffects(
                        appView, arguments));
    putAll(
        builder,
        dexItemFactory.stringBufferMethods.constructorMethods,
        dexItemFactory.stringBufferMethods::constructorInvokeIsSideEffectFree);
    putAll(
        builder,
        dexItemFactory.stringBuilderMethods.constructorMethods,
        dexItemFactory.stringBuilderMethods::constructorInvokeIsSideEffectFree);
    return builder.build();
  }

  private static Set<DexMethod> buildUnconditionalFinalMethodsWithoutSideEffects(
      DexItemFactory dexItemFactory) {
    return ImmutableSet.<DexMethod>builder()
        .add(dexItemFactory.booleanMembers.booleanValue)
        .add(dexItemFactory.booleanMembers.toString)
        .add(dexItemFactory.booleanMembers.valueOf)
        .add(dexItemFactory.byteMembers.byteValue)
        .add(dexItemFactory.byteMembers.toString)
        .add(dexItemFactory.byteMembers.valueOf)
        .add(dexItemFactory.classMethods.desiredAssertionStatus)
        .add(dexItemFactory.charMembers.charValue)
        .add(dexItemFactory.charMembers.toString)
        .add(dexItemFactory.charMembers.valueOf)
        .add(dexItemFactory.doubleMembers.doubleValue)
        .add(dexItemFactory.doubleMembers.toString)
        .add(dexItemFactory.doubleMembers.valueOf)
        .add(dexItemFactory.enumMembers.constructor)
        .add(dexItemFactory.floatMembers.floatValue)
        .add(dexItemFactory.floatMembers.toString)
        .add(dexItemFactory.floatMembers.valueOf)
        .add(dexItemFactory.integerMembers.intValue)
        .add(dexItemFactory.integerMembers.toString)
        .add(dexItemFactory.integerMembers.valueOf)
        .add(dexItemFactory.longMembers.longValue)
        .add(dexItemFactory.longMembers.toString)
        .add(dexItemFactory.longMembers.valueOf)
        .add(dexItemFactory.npeMethods.init)
        .add(dexItemFactory.npeMethods.initWithMessage)
        .add(dexItemFactory.recordMembers.constructor)
        .add(dexItemFactory.objectMembers.constructor)
        .add(dexItemFactory.objectMembers.getClass)
        .add(dexItemFactory.shortMembers.shortValue)
        .add(dexItemFactory.shortMembers.toString)
        .add(dexItemFactory.shortMembers.valueOf)
        .add(dexItemFactory.stringBufferMethods.toString)
        .add(dexItemFactory.stringBuilderMethods.toString)
        .add(dexItemFactory.stringMembers.length)
        .add(dexItemFactory.stringMembers.hashCode)
        .add(dexItemFactory.stringMembers.isEmpty)
        .add(dexItemFactory.stringMembers.toString)
        .add(dexItemFactory.stringMembers.trim)
        .addAll(dexItemFactory.classMethods.getNames)
        // Required to unbox recent Kotlin enums (See b/268005228).
        .add(dexItemFactory.kotlinEnumEntriesListInit)
        .build();
  }

  private static Set<DexMethod> buildNonFinalMethodsWithoutSideEffects(
      DexItemFactory dexItemFactory) {
    return ImmutableSet.of(
        dexItemFactory.objectMembers.equals,
        dexItemFactory.objectMembers.hashCode,
        dexItemFactory.objectMembers.toString);
  }

  private static <K, V> void putAll(ImmutableMap.Builder<K, V> builder, Iterable<K> keys, V value) {
    for (K key : keys) {
      builder.put(key, value);
    }
  }

  public void forEachSideEffectFreeFinalMethod(Consumer<DexMethod> consumer) {
    unconditionalFinalMethodsWithoutSideEffects.forEach(consumer);
  }

  public boolean isCallToSideEffectFreeFinalMethod(InvokeMethod invoke) {
    return isSideEffectFreeFinalMethod(invoke.getInvokedMethod(), invoke.arguments());
  }

  public boolean isSideEffectFreeFinalMethod(DexMethod method, List<Value> arguments) {
    return unconditionalFinalMethodsWithoutSideEffects.contains(method)
        || finalMethodsWithoutSideEffects
            .getOrDefault(method, BiPredicateUtils.alwaysFalse())
            .test(method, arguments);
  }

  // This intentionally takes the invoke instruction since the determination of whether a library
  // method has side effects may depend on the arguments.
  public boolean isSideEffectFree(InvokeMethod invoke, LibraryMethod singleTarget) {
    return isSideEffectFreeFinalMethod(singleTarget.getReference(), invoke.arguments())
        || nonFinalMethodsWithoutSideEffects.contains(singleTarget.getReference());
  }
}
