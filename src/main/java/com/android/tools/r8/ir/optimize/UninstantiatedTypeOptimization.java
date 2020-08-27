// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization.Strategy.ALLOW_ARGUMENT_REMOVAL;
import static com.android.tools.r8.ir.optimize.UninstantiatedTypeOptimization.Strategy.DISALLOW_ARGUMENT_REMOVAL;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.TopDownClassHierarchyTraversal;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.MemberPoolCollection.MemberPool;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public class UninstantiatedTypeOptimization {

  enum Strategy {
    ALLOW_ARGUMENT_REMOVAL,
    DISALLOW_ARGUMENT_REMOVAL
  }

  public static class UninstantiatedTypeOptimizationGraphLens extends NestedGraphLens {

    private final AppView<?> appView;
    private final Map<DexMethod, ArgumentInfoCollection> removedArgumentsInfoPerMethod;

    UninstantiatedTypeOptimizationGraphLens(
        BiMap<DexMethod, DexMethod> methodMap,
        Map<DexMethod, ArgumentInfoCollection> removedArgumentsInfoPerMethod,
        AppView<?> appView) {
      super(
          ImmutableMap.of(),
          methodMap,
          ImmutableMap.of(),
          null,
          methodMap.inverse(),
          appView.graphLens(),
          appView.dexItemFactory());
      this.appView = appView;
      this.removedArgumentsInfoPerMethod = removedArgumentsInfoPerMethod;
    }

    @Override
    protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
        RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
      DexMethod previous = internalGetPreviousMethodSignature(method);
      if (previous == method) {
        assert !removedArgumentsInfoPerMethod.containsKey(method);
        return prototypeChanges;
      }
      if (method.getReturnType().isVoidType() && !previous.getReturnType().isVoidType()) {
        prototypeChanges = prototypeChanges.withConstantReturn(previous.getReturnType(), appView);
      }
      return prototypeChanges.withRemovedArguments(
          removedArgumentsInfoPerMethod.getOrDefault(method, ArgumentInfoCollection.empty()));
    }
  }

  private static final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();

  private final AppView<AppInfoWithLiveness> appView;

  public UninstantiatedTypeOptimization(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public UninstantiatedTypeOptimization strenghtenOptimizationInfo() {
    OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    AbstractValue nullValue = appView.abstractValueFactory().createSingleNumberValue(0);
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachField(
          field -> {
            if (field.type().isAlwaysNull(appView)) {
              FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field.field);
              if (fieldAccessInfo != null) {
                // Clear all writes since each write must write `null` to the field.
                fieldAccessInfo.asMutable().clearWrites();
              }
              feedback.recordFieldHasAbstractValue(field, appView, nullValue);
            }
          });
      clazz.forEachMethod(
          method -> {
            if (method.returnType().isAlwaysNull(appView)) {
              feedback.methodReturnsAbstractValue(method, appView, nullValue);
            }
          });
    }
    return this;
  }

  public UninstantiatedTypeOptimizationGraphLens run(
      MethodPoolCollection methodPoolCollection, ExecutorService executorService, Timing timing) {
    try {
      methodPoolCollection.buildAll(executorService, timing);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    Map<Wrapper<DexMethod>, Set<DexType>> changedVirtualMethods = new HashMap<>();
    BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
    Map<DexMethod, ArgumentInfoCollection> removedArgumentsInfoPerMethod = new IdentityHashMap<>();

    TopDownClassHierarchyTraversal.forProgramClasses(appView)
        .visit(
            appView.appInfo().classes(),
            clazz ->
                processClass(
                    clazz,
                    changedVirtualMethods,
                    methodMapping,
                    methodPoolCollection,
                    removedArgumentsInfoPerMethod));

    if (!methodMapping.isEmpty()) {
      return new UninstantiatedTypeOptimizationGraphLens(
          methodMapping, removedArgumentsInfoPerMethod, appView);
    }
    return null;
  }

  private void processClass(
      DexProgramClass clazz,
      Map<Wrapper<DexMethod>, Set<DexType>> changedVirtualMethods,
      BiMap<DexMethod, DexMethod> methodMapping,
      MethodPoolCollection methodPoolCollection,
      Map<DexMethod, ArgumentInfoCollection> removedArgumentsInfoPerMethod) {
    MemberPool<DexMethod> methodPool = methodPoolCollection.get(clazz);

    if (clazz.isInterface()) {
      // Do not allow changing the prototype of methods that override an interface method.
      // This achieved by faking that there is already a method with the given signature.
      for (DexEncodedMethod virtualMethod : clazz.virtualMethods()) {
        RewrittenPrototypeDescription prototypeChanges =
            RewrittenPrototypeDescription.createForUninstantiatedTypes(
                virtualMethod.method,
                appView,
                getRemovedArgumentsInfo(virtualMethod, ALLOW_ARGUMENT_REMOVAL));
        if (!prototypeChanges.isEmpty()) {
          DexMethod newMethod = getNewMethodSignature(virtualMethod, prototypeChanges);
          Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);
          if (!methodPool.hasSeenDirectly(wrapper)) {
            methodPool.seen(wrapper);
          }
        }
      }
      return;
    }

    Map<DexEncodedMethod, RewrittenPrototypeDescription> prototypeChangesPerMethod =
        new IdentityHashMap<>();
    for (DexEncodedMethod directMethod : clazz.directMethods()) {
      RewrittenPrototypeDescription prototypeChanges =
          getPrototypeChanges(directMethod, ALLOW_ARGUMENT_REMOVAL);
      if (!prototypeChanges.isEmpty()) {
        prototypeChangesPerMethod.put(directMethod, prototypeChanges);
      }
    }

    // Reserve all signatures which are known to not be touched below.
    Set<Wrapper<DexMethod>> usedSignatures = new HashSet<>();
    for (DexEncodedMethod method : clazz.methods()) {
      if (!prototypeChangesPerMethod.containsKey(method)) {
        usedSignatures.add(equivalence.wrap(method.method));
      }
    }

    // Change the return type of direct methods that return an uninstantiated type to void.
    clazz
        .getMethodCollection()
        .replaceDirectMethods(
            encodedMethod -> {
              DexMethod method = encodedMethod.method;
              RewrittenPrototypeDescription prototypeChanges =
                  prototypeChangesPerMethod.getOrDefault(
                      encodedMethod, RewrittenPrototypeDescription.none());
              ArgumentInfoCollection removedArgumentsInfo =
                  prototypeChanges.getArgumentInfoCollection();
              DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
              if (newMethod != method) {
                Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

                // TODO(b/110806787): Can be extended to handle collisions by renaming the given
                // method.
                if (usedSignatures.add(wrapper)) {
                  methodMapping.put(method, newMethod);
                  if (removedArgumentsInfo.hasRemovedArguments()) {
                    removedArgumentsInfoPerMethod.put(newMethod, removedArgumentsInfo);
                  }
                  return encodedMethod.toTypeSubstitutedMethod(
                      newMethod,
                      removedArgumentsInfo.createParameterAnnotationsRemover(encodedMethod));
                }
              }
              return encodedMethod;
            });

    // Change the return type of virtual methods that return an uninstantiated type to void.
    // This is done in two steps. First we change the return type of all methods that override
    // a method whose return type has already been changed to void previously. Note that
    // all supertypes of the current class are always visited prior to the current class.
    // This is important to ensure that a method that used to override a method in its super
    // class will continue to do so after this optimization.
    clazz
        .getMethodCollection()
        .replaceVirtualMethods(
            encodedMethod -> {
              DexMethod method = encodedMethod.method;
              RewrittenPrototypeDescription prototypeChanges =
                  getPrototypeChanges(encodedMethod, DISALLOW_ARGUMENT_REMOVAL);
              ArgumentInfoCollection removedArgumentsInfo =
                  prototypeChanges.getArgumentInfoCollection();
              DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
              if (newMethod != method) {
                Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

                boolean isOverrideOfPreviouslyChangedMethodInSuperClass =
                    changedVirtualMethods
                        .getOrDefault(equivalence.wrap(method), ImmutableSet.of())
                        .stream()
                        .anyMatch(other -> appView.appInfo().isSubtype(clazz.type, other));
                if (isOverrideOfPreviouslyChangedMethodInSuperClass) {
                  assert methodPool.hasSeen(wrapper);

                  boolean signatureIsAvailable = usedSignatures.add(wrapper);
                  assert signatureIsAvailable;

                  methodMapping.put(method, newMethod);
                  return encodedMethod.toTypeSubstitutedMethod(
                      newMethod,
                      removedArgumentsInfo.createParameterAnnotationsRemover(encodedMethod));
                }
              }
              return encodedMethod;
            });
    clazz
        .getMethodCollection()
        .replaceVirtualMethods(
            encodedMethod -> {
              DexMethod method = encodedMethod.method;
              RewrittenPrototypeDescription prototypeChanges =
                  getPrototypeChanges(encodedMethod, DISALLOW_ARGUMENT_REMOVAL);
              ArgumentInfoCollection removedArgumentsInfo =
                  prototypeChanges.getArgumentInfoCollection();
              DexMethod newMethod = getNewMethodSignature(encodedMethod, prototypeChanges);
              if (newMethod != method) {
                Wrapper<DexMethod> wrapper = equivalence.wrap(newMethod);

                // TODO(b/110806787): Can be extended to handle collisions by renaming the given
                //  method. Note that this also requires renaming all of the methods that override
                // this
                //  method, though.
                if (!methodPool.hasSeen(wrapper) && usedSignatures.add(wrapper)) {
                  methodPool.seen(wrapper);

                  methodMapping.put(method, newMethod);

                  boolean added =
                      changedVirtualMethods
                          .computeIfAbsent(
                              equivalence.wrap(method), key -> Sets.newIdentityHashSet())
                          .add(clazz.type);
                  assert added;

                  return encodedMethod.toTypeSubstitutedMethod(
                      newMethod,
                      removedArgumentsInfo.createParameterAnnotationsRemover(encodedMethod));
                }
              }
              return encodedMethod;
            });
  }

  private RewrittenPrototypeDescription getPrototypeChanges(
      DexEncodedMethod encodedMethod, Strategy strategy) {
    if (ArgumentRemovalUtils.isPinned(encodedMethod, appView)
        || appView.appInfo().keepConstantArguments.contains(encodedMethod.method)) {
      return RewrittenPrototypeDescription.none();
    }
    return RewrittenPrototypeDescription.createForUninstantiatedTypes(
        encodedMethod.method, appView, getRemovedArgumentsInfo(encodedMethod, strategy));
  }

  private ArgumentInfoCollection getRemovedArgumentsInfo(
      DexEncodedMethod encodedMethod, Strategy strategy) {
    if (strategy == DISALLOW_ARGUMENT_REMOVAL) {
      return ArgumentInfoCollection.empty();
    }

    ArgumentInfoCollection.Builder argInfosBuilder = ArgumentInfoCollection.builder();
    DexProto proto = encodedMethod.method.proto;
    int offset = encodedMethod.isStatic() ? 0 : 1;
    for (int i = 0; i < proto.parameters.size(); ++i) {
      DexType type = proto.parameters.values[i];
      if (type.isAlwaysNull(appView)) {
        RemovedArgumentInfo removedArg =
            RemovedArgumentInfo.builder().setIsAlwaysNull().setType(type).build();
        argInfosBuilder.addArgumentInfo(i + offset, removedArg);
      }
    }
    return argInfosBuilder.build();
  }

  private DexMethod getNewMethodSignature(
      DexEncodedMethod encodedMethod, RewrittenPrototypeDescription prototypeChanges) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexMethod method = encodedMethod.method;
    DexProto newProto = prototypeChanges.rewriteProto(encodedMethod, dexItemFactory);

    return dexItemFactory.createMethod(method.holder, newProto, method.name);
  }
}
