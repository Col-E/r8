// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ArgumentUse;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UnusedArgumentsCollector {

  private final AppView<AppInfoWithLiveness> appView;
  private final Set<DexEncodedMethod> methods = Sets.newIdentityHashSet();

  private final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
  private final Map<DexMethod, IntList> removedArguments = new IdentityHashMap<>();

  static class UnusedArgumentsGraphLense extends NestedGraphLense {

    private final Map<DexMethod, IntList> removedArguments;

    UnusedArgumentsGraphLense(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        com.android.tools.r8.graph.GraphLense previousLense,
        DexItemFactory dexItemFactory,
        Map<DexMethod, IntList> removedArguments) {
      super(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLense,
          dexItemFactory);
      this.removedArguments = removedArguments;
    }

    @Override
    public GraphLenseLookupResult lookupMethod(
        DexMethod method, DexEncodedMethod context, Type type) {
      GraphLenseLookupResult result = super.lookupMethod(method, context, type);
      IntList removedArguments = this.removedArguments.get(result.getMethod());
      return removedArguments != null ? result.withRemovedArguments(removedArguments) : result;
    }
  }

  public UnusedArgumentsCollector(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public GraphLense run() {
    // TODO(65810338): Do this is parallel.
    appView.appInfo().classes().forEach(this::processClass);

    if (!methodMapping.isEmpty()) {
      return new UnusedArgumentsGraphLense(
          ImmutableMap.of(),
          methodMapping,
          ImmutableMap.of(),
          ImmutableBiMap.of(),
          methodMapping.inverse(),
          appView.graphLense(),
          appView.dexItemFactory(),
          removedArguments);
    }

    return appView.graphLense();
  }

  private class UsedSignatures {

    private final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
    private final Set<Wrapper<DexMethod>> usedSignatures;

    UsedSignatures(DexProgramClass clazz) {
      // Only static methods for now, so just consider direct methods.
      usedSignatures =
          Arrays.stream(clazz.directMethods())
              .map(targetMethod -> equivalence.wrap(targetMethod.method))
              .collect(Collectors.toSet());
    }

    private DexProto protoWithRemovedArguments(DexMethod method, IntList unused) {
      DexType[] parameters = new DexType[method.proto.parameters.size() - unused.size()];
      if (parameters.length > 0) {
        int newIndex = 0;
        for (int j = 0; j < method.proto.parameters.size(); j++) {
          if (!unused.contains(j)) {
            parameters[newIndex++] = method.proto.parameters.values[j];
          }
        }
        assert newIndex == parameters.length;
      }
      return appView.appInfo().dexItemFactory.createProto(method.proto.returnType, parameters);
    }

    private boolean isMethodSignatureAvailable(DexMethod method) {
      return !usedSignatures.contains(equivalence.wrap(method));
    }

    DexEncodedMethod removeArguments(DexEncodedMethod method, IntList unused) {
      boolean removed = usedSignatures.remove(equivalence.wrap(method.method));
      assert removed;
      DexProto newProto = protoWithRemovedArguments(method.method, unused);
      DexMethod newSignature;
      int count = 0;
      DexString newName = null;
      do {
        newName =
            newName == null
                ? method.method.name
                : appView
                    .dexItemFactory()
                    .createString(method.method.name.toSourceString() + count);
        newSignature =
            appView.dexItemFactory().createMethod(method.method.holder, newProto, newName);
        count++;
      } while (!isMethodSignatureAvailable(newSignature));
      usedSignatures.add(equivalence.wrap(newSignature));
      return method.toTypeSubstitutedMethod(newSignature);
    }
  }

  private void processClass(DexProgramClass clazz) {
    UsedSignatures signatures = new UsedSignatures(clazz);
    for (int i = 0; i < clazz.directMethods().length; i++) {
      DexEncodedMethod method = clazz.directMethods()[i];
      IntList unused = collectUnusedArguments(method);
      if (unused != null) {
        DexEncodedMethod newMethod = signatures.removeArguments(method, unused);
        clazz.directMethods()[i] = newMethod;
        methodMapping.put(method.method, newMethod.method);
        removedArguments.put(newMethod.method, unused);
      }
    }
  }

  private IntList collectUnusedArguments(DexEncodedMethod method) {
    // TODO(65810338): Do we need more exclusions here?
    if (appView.appInfo().isPinned(method.method)) {
      return null;
    }
    // Only process JAR code.
    if (method.getCode() == null || !method.getCode().isJarCode()) {
      return null;
    }
    assert method.getCode().getOwner() == method;
    int argumentCount =
        method.method.proto.parameters.size() + (method.accessFlags.isStatic() ? 0 : 1);
    // TODO(65810338): Implement for private and virtual as well.
    if (!method.accessFlags.isStatic()) {
      return null;
    }
    CollectUsedArguments collector = new CollectUsedArguments();
    method.getCode().registerArgumentReferences(collector);
    BitSet used = collector.getUsedArguments();
    IntList unused = null;
    if (used.cardinality() < argumentCount) {
      unused = new IntArrayList();
      for (int i = 0; i < argumentCount; i++) {
        if (!used.get(i)) {
          unused.add(i);
        }
      }
    }
    return unused;
  }

  private static class CollectUsedArguments extends ArgumentUse {

    private final BitSet used = new BitSet();

    BitSet getUsedArguments() {
      return used;
    }

    @Override
    public boolean register(int argument) {
      used.set(argument);
      return true;
    }
  }
}
