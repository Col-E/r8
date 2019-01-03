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
import com.android.tools.r8.graph.GraphLense.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.GraphLense.RewrittenPrototypeDescription.RemovedArgumentsInfo;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class UnusedArgumentsCollector {

  private final AppView<AppInfoWithLiveness> appView;

  private final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
  private final Map<DexMethod, RemovedArgumentsInfo> removedArguments = new IdentityHashMap<>();

  static class UnusedArgumentsGraphLense extends NestedGraphLense {

    private final Map<DexMethod, RemovedArgumentsInfo> removedArguments;

    UnusedArgumentsGraphLense(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLense previousLense,
        DexItemFactory dexItemFactory,
        Map<DexMethod, RemovedArgumentsInfo> removedArguments) {
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
    public RewrittenPrototypeDescription lookupPrototypeChanges(DexMethod method) {
      DexMethod originalMethod =
          originalMethodSignatures != null
              ? originalMethodSignatures.getOrDefault(method, method)
              : method;
      RewrittenPrototypeDescription result = previousLense.lookupPrototypeChanges(originalMethod);
      RemovedArgumentsInfo removedArguments = this.removedArguments.get(method);
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
    private final Set<Wrapper<DexMethod>> usedSignatures = new HashSet<>();

    private DexProto protoWithRemovedArguments(
        DexEncodedMethod encodedMethod, RemovedArgumentsInfo unused) {
      DexMethod method = encodedMethod.method;

      int firstArgumentIndex = encodedMethod.isStatic() ? 0 : 1;
      int numberOfParameters = method.proto.parameters.size() - unused.numberOfRemovedArguments();
      if (!encodedMethod.isStatic() && unused.isArgumentRemoved(0)) {
        numberOfParameters++;
      }

      DexType[] parameters = new DexType[numberOfParameters];
      if (numberOfParameters > 0) {
        int newIndex = 0;
        for (int oldIndex = 0; oldIndex < method.proto.parameters.size(); oldIndex++) {
          if (!unused.isArgumentRemoved(oldIndex + firstArgumentIndex)) {
            parameters[newIndex++] = method.proto.parameters.values[oldIndex];
          }
        }
        assert newIndex == parameters.length;
      }
      return appView.appInfo().dexItemFactory.createProto(method.proto.returnType, parameters);
    }

    private boolean isMethodSignatureAvailable(DexMethod method) {
      return !usedSignatures.contains(equivalence.wrap(method));
    }

    private void markSignatureAsUsed(DexMethod method) {
      usedSignatures.add(equivalence.wrap(method));
    }

    DexEncodedMethod removeArguments(DexEncodedMethod method, RemovedArgumentsInfo unused) {
      if (unused == null) {
        return null;
      }

      boolean removed = usedSignatures.remove(equivalence.wrap(method.method));
      assert removed;
      DexProto newProto = protoWithRemovedArguments(method, unused);
      DexMethod newSignature;
      int count = 0;
      DexString newName = null;
      do {
        if (newName == null) {
          newName = method.method.name;
        } else if (method.method.name != appView.dexItemFactory().initMethodName) {
          newName =
              appView.dexItemFactory().createString(method.method.name.toSourceString() + count);
        } else {
          // Constructors must be named `<init>`.
          return null;
        }
        newSignature =
            appView.dexItemFactory().createMethod(method.method.holder, newProto, newName);
        count++;
      } while (!isMethodSignatureAvailable(newSignature));
      markSignatureAsUsed(newSignature);
      return method.toTypeSubstitutedMethod(newSignature);
    }
  }

  private void processClass(DexProgramClass clazz) {
    UsedSignatures signatures = new UsedSignatures();
    for (DexEncodedMethod method : clazz.methods()) {
      signatures.markSignatureAsUsed(method.method);
    }
    for (int i = 0; i < clazz.directMethods().length; i++) {
      DexEncodedMethod method = clazz.directMethods()[i];
      RemovedArgumentsInfo unused = collectUnusedArguments(method);
      DexEncodedMethod newMethod = signatures.removeArguments(method, unused);
      if (newMethod != null) {
        clazz.directMethods()[i] = newMethod;
        methodMapping.put(method.method, newMethod.method);
        removedArguments.put(newMethod.method, unused);
      }
    }
  }

  private RemovedArgumentsInfo collectUnusedArguments(DexEncodedMethod method) {
    if (ArgumentRemovalUtils.isPinned(method, appView)) {
      return null;
    }
    // Only process JAR code.
    if (method.getCode() == null || !method.getCode().isJarCode()) {
      return null;
    }
    assert method.getCode().getOwner() == method;
    int argumentCount =
        method.method.proto.parameters.size() + (method.accessFlags.isStatic() ? 0 : 1);
    // TODO(65810338): Implement for virtual methods as well.
    if (method.accessFlags.isPrivate() || method.accessFlags.isStatic()) {
      CollectUsedArguments collector = new CollectUsedArguments();
      if (!method.accessFlags.isStatic()) {
        // TODO(65810338): The receiver cannot be removed without transforming the method to being
        // static.
        collector.register(0);
      }
      method.getCode().registerArgumentReferences(collector);
      BitSet used = collector.getUsedArguments();
      if (used.cardinality() < argumentCount) {
        List<RemovedArgumentInfo> unused = new ArrayList<>();
        for (int i = 0; i < argumentCount; i++) {
          if (!used.get(i)) {
            unused.add(RemovedArgumentInfo.builder().setArgumentIndex(i).build());
          }
        }
        return new RemovedArgumentsInfo(unused);
      }
    }
    return null;
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
