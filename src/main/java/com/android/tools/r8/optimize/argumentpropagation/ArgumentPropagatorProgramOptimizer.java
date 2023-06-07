// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RemovedReceiverInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorGraphLens.Builder;
import com.android.tools.r8.optimize.argumentpropagation.utils.ParameterRemovalUtils;
import com.android.tools.r8.optimize.utils.ConcurrentNonProgramMethodsCollection;
import com.android.tools.r8.optimize.utils.NonProgramMethodsCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.AccessUtils;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.CallSiteOptimizationOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMaps;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public class ArgumentPropagatorProgramOptimizer {

  static class AllowedPrototypeChanges {

    private static final AllowedPrototypeChanges EMPTY =
        new AllowedPrototypeChanges(false, null, Int2ReferenceMaps.emptyMap(), IntSets.EMPTY_SET);

    boolean canBeConvertedToStaticMethod;
    DexType newReturnType;
    Int2ReferenceMap<DexType> newParameterTypes;
    IntSet removableParameterIndices;

    AllowedPrototypeChanges(
        boolean canBeConvertedToStaticMethod,
        DexType newReturnType,
        Int2ReferenceMap<DexType> newParameterTypes,
        IntSet removableParameterIndices) {
      this.canBeConvertedToStaticMethod = canBeConvertedToStaticMethod;
      this.newReturnType = newReturnType;
      this.newParameterTypes = newParameterTypes;
      this.removableParameterIndices = removableParameterIndices;
    }

    public static AllowedPrototypeChanges create(
        ProgramMethod method, RewrittenPrototypeDescription prototypeChanges) {
      if (prototypeChanges.isEmpty()) {
        return empty();
      }
      ArgumentInfoCollection argumentInfoCollection = prototypeChanges.getArgumentInfoCollection();
      boolean canBeConvertedToStaticMethod = argumentInfoCollection.isConvertedToStaticMethod();
      assert !canBeConvertedToStaticMethod
          || (method.getDefinition().isInstance()
              && argumentInfoCollection.getArgumentInfo(0).isRemovedReceiverInfo());
      DexType newReturnType =
          prototypeChanges.hasRewrittenReturnInfo()
              ? prototypeChanges.getRewrittenReturnInfo().getNewType()
              : null;
      Int2ReferenceMap<DexType> newParameterTypes = new Int2ReferenceOpenHashMap<>();
      IntSet removableParameterIndices = new IntOpenHashSet();
      argumentInfoCollection.forEach(
          (argumentIndex, argumentInfo) -> {
            if (argumentInfo.isRemovedReceiverInfo()) {
              // Skip as the receiver is not in the proto.
              assert canBeConvertedToStaticMethod;
            } else {
              int parameterIndex =
                  method.getDefinition().getParameterIndexFromArgumentIndex(argumentIndex);
              assert parameterIndex >= 0;
              if (argumentInfo.isRemovedArgumentInfo()) {
                removableParameterIndices.add(parameterIndex);
              } else {
                assert argumentInfo.isRewrittenTypeInfo();
                RewrittenTypeInfo rewrittenTypeInfo = argumentInfo.asRewrittenTypeInfo();
                newParameterTypes.put(parameterIndex, rewrittenTypeInfo.getNewType());
              }
            }
          });
      return new AllowedPrototypeChanges(
          canBeConvertedToStaticMethod,
          newReturnType,
          newParameterTypes,
          removableParameterIndices);
    }

    public static AllowedPrototypeChanges empty() {
      return EMPTY;
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          canBeConvertedToStaticMethod,
          newReturnType,
          newParameterTypes,
          removableParameterIndices);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AllowedPrototypeChanges other = (AllowedPrototypeChanges) obj;
      return canBeConvertedToStaticMethod == other.canBeConvertedToStaticMethod
          && newReturnType == other.newReturnType
          && newParameterTypes.equals(other.newParameterTypes)
          && removableParameterIndices.equals(other.removableParameterIndices);
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final Map<Set<DexProgramClass>, DexMethodSignatureSet> interfaceDispatchOutsideProgram;
  private final NonProgramMethodsCollection nonProgramMethodsCollection;

  public ArgumentPropagatorProgramOptimizer(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Map<Set<DexProgramClass>, DexMethodSignatureSet> interfaceDispatchOutsideProgram) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.interfaceDispatchOutsideProgram = interfaceDispatchOutsideProgram;
    this.nonProgramMethodsCollection =
        ConcurrentNonProgramMethodsCollection.createVirtualMethodsCollection(appView);
  }

  public ArgumentPropagatorGraphLens run(
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      Consumer<DexProgramClass> affectedClassConsumer,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Optimize components");
    ArgumentPropagatorSyntheticEventConsumer eventConsumer =
        ArgumentPropagatorSyntheticEventConsumer.create(appView);
    ProcessorContext processorContext = appView.createProcessorContext();
    Collection<Builder> partialGraphLensBuilders =
        ThreadUtils.processItemsWithResults(
            stronglyConnectedProgramComponents,
            classes ->
                new StronglyConnectedComponentOptimizer(eventConsumer, processorContext)
                    .optimize(
                        classes,
                        interfaceDispatchOutsideProgram.getOrDefault(
                            classes, DexMethodSignatureSet.empty()),
                        affectedClassConsumer),
            executorService);
    eventConsumer.finished(appView);
    timing.end();

    // Merge all the partial, disjoint graph lens builders into a single graph lens.
    timing.begin("Build graph lens");
    ArgumentPropagatorGraphLens.Builder graphLensBuilder =
        ArgumentPropagatorGraphLens.builder(appView);
    partialGraphLensBuilders.forEach(graphLensBuilder::mergeDisjoint);
    ArgumentPropagatorGraphLens graphLens = graphLensBuilder.build();
    timing.end();

    return graphLens;
  }

  public class StronglyConnectedComponentOptimizer {

    private final DexItemFactory dexItemFactory = appView.dexItemFactory();
    private final InternalOptions options = appView.options();
    private final CallSiteOptimizationOptions callSiteOptimizationOptions =
        appView.options().callSiteOptimizationOptions();

    private final Map<DexMethodSignature, AllowedPrototypeChanges>
        allowedPrototypeChangesForVirtualMethods = new HashMap<>();

    private final ProgramMethodMap<SingleValue> returnValuesForVirtualMethods =
        ProgramMethodMap.create();

    // Reserved names, i.e., mappings from pairs (old method signature, prototype changes) to the
    // new method signature for that method.
    private final Map<DexMethodSignature, Map<AllowedPrototypeChanges, DexMethodSignature>>
        newMethodSignatures = new HashMap<>();

    // The method name suffix to start from when searching for a fresh method signature. Used to
    // avoid searching from index 0 to a large number when searching for a fresh method signature.
    private final Map<DexMethodSignature, IntBox> newMethodSignatureSuffixes = new HashMap<>();

    // Occupied method signatures (inverse of reserved names). Used to effectively check if a given
    // method signature is already reserved.
    private final Map<DexMethodSignature, Pair<AllowedPrototypeChanges, DexMethodSignature>>
        occupiedMethodSignatures = new HashMap<>();

    private final ArgumentPropagatorSyntheticEventConsumer eventConsumer;
    private final ProcessorContext processorContext;

    public StronglyConnectedComponentOptimizer(
        ArgumentPropagatorSyntheticEventConsumer eventConsumer, ProcessorContext processorContext) {
      this.eventConsumer = eventConsumer;
      this.processorContext = processorContext;
    }

    // TODO(b/190154391): Strengthen the static type of parameters.
    // TODO(b/69963623): If we optimize a method to be unconditionally throwing (because it has a
    //  bottom parameter), then for each caller that becomes unconditionally throwing, we could
    //  also enqueue the caller's callers for reprocessing. This would propagate the throwing
    //  information to all call sites.
    // TODO(b/190154391): If we learn that a parameter of an instance initializer is constant, and
    //  this parameter is assigned to a field on the class, and this field does not have any other
    //  writes in the program, then we should replace all field reads by the constant value and
    // prune
    //  the field. Alternatively, we could consider building flow constraints for field assignments,
    //  similarly to the way we deal with call chains in argument propagation. If a field is only
    //  assigned the parameter of a given method, we would add the flow constraint "parameter p ->
    //  field f".
    private ArgumentPropagatorGraphLens.Builder optimize(
        Set<DexProgramClass> stronglyConnectedProgramClasses,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        Consumer<DexProgramClass> affectedClassConsumer) {
      // First reserve pinned method signatures.
      reservePinnedMethodSignatures(stronglyConnectedProgramClasses);

      // To ensure that we preserve the overriding relationships between methods, we only remove a
      // constant or unused parameter from a virtual method when it can be removed from all other
      // virtual methods in the component with the same method signature.
      computePrototypeChangesForVirtualMethods(
          stronglyConnectedProgramClasses, interfaceDispatchOutsideProgram);

      // Build a graph lens while visiting the classes in the component.
      // TODO(b/190154391): Consider visiting the interfaces first, and then processing the
      //  (non-interface) classes in top-down order to reduce the amount of reserved names.
      ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder =
          ArgumentPropagatorGraphLens.builder(appView);
      List<DexProgramClass> stronglyConnectedProgramClassesWithDeterministicOrder =
          new ArrayList<>(stronglyConnectedProgramClasses);
      stronglyConnectedProgramClassesWithDeterministicOrder.sort(
          Comparator.comparing(DexClass::getType));
      for (DexProgramClass clazz : stronglyConnectedProgramClassesWithDeterministicOrder) {
        if (visitClass(clazz, interfaceDispatchOutsideProgram, partialGraphLensBuilder)) {
          affectedClassConsumer.accept(clazz);
        }
      }
      return partialGraphLensBuilder;
    }

    private void reservePinnedMethodSignatures(
        Set<DexProgramClass> stronglyConnectedProgramClasses) {
      DexMethodSignatureSet pinnedMethodSignatures = DexMethodSignatureSet.create();
      Set<DexClass> seenNonProgramClasses = Sets.newIdentityHashSet();
      for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
        clazz.forEachProgramMethodMatching(
            method -> !method.isInstanceInitializer(),
            method -> {
              KeepMethodInfo keepInfo = appView.getKeepInfo(method);
              if (!keepInfo.isOptimizationAllowed(options)
                  || !keepInfo.isShrinkingAllowed(options)) {
                pinnedMethodSignatures.add(method.getMethodSignature());
              }
            });
        immediateSubtypingInfo.forEachImmediateSuperClassMatching(
            clazz,
            (supertype, superclass) ->
                superclass != null
                    && !superclass.isProgramClass()
                    && seenNonProgramClasses.add(superclass),
            (supertype, superclass) ->
                pinnedMethodSignatures.addAll(
                    nonProgramMethodsCollection.getOrComputeNonProgramMethods(
                        superclass.asClasspathOrLibraryClass())));
      }
      pinnedMethodSignatures.forEach(
          signature ->
              reserveMethodSignature(signature, signature, AllowedPrototypeChanges.empty()));
    }

    private void reserveMethodSignature(
        DexMethodSignature newMethodSignature,
        DexMethodSignature originalMethodSignature,
        AllowedPrototypeChanges allowedPrototypeChanges) {
      // Record that methods with the given signature and removed parameters should be mapped to the
      // new signature.
      newMethodSignatures
          .computeIfAbsent(originalMethodSignature, ignoreKey(HashMap::new))
          .put(allowedPrototypeChanges, newMethodSignature);

      // Record that the new method signature is used, by a method with the old signature that had
      // the
      // given removed parameters.
      occupiedMethodSignatures.put(
          newMethodSignature, new Pair<>(allowedPrototypeChanges, originalMethodSignature));
    }

    private void computePrototypeChangesForVirtualMethods(
        Set<DexProgramClass> stronglyConnectedProgramClasses,
        DexMethodSignatureSet interfaceDispatchOutsideProgram) {
      // Group the virtual methods in the component by their signatures.
      Map<DexMethodSignature, ProgramMethodSet> virtualMethodsBySignature =
          computeVirtualMethodsBySignature(stronglyConnectedProgramClasses);
      virtualMethodsBySignature.forEach(
          (signature, methods) -> {
            // Check that there are no keep rules that prohibit prototype changes from any of the
            // methods.
            if (Iterables.any(
                methods,
                method -> !isPrototypeChangesAllowed(method, interfaceDispatchOutsideProgram))) {
              return;
            }

            if (containsImmediateInterfaceOfInstantiatedLambda(methods)) {
              return;
            }

            // Find the parameters that are either (i) the same constant, (ii) all unused, or (iii)
            // all possible to strengthen to the same stronger type, in all methods.
            boolean canBeConvertedToStaticMethod = canRemoveReceiverFromVirtualMethods(methods);
            Int2ReferenceMap<DexType> newParameterTypes = new Int2ReferenceOpenHashMap<>();
            IntSet removableVirtualMethodParametersInAllMethods = new IntArraySet();
            for (int parameterIndex = 0;
                parameterIndex < signature.getParameters().size();
                parameterIndex++) {
              if (canRemoveParameterFromVirtualMethods(methods, parameterIndex)) {
                removableVirtualMethodParametersInAllMethods.add(parameterIndex);
              } else {
                DexType newParameterType =
                    getNewParameterTypeForVirtualMethods(methods, parameterIndex);
                if (newParameterType != null) {
                  newParameterTypes.put(parameterIndex, newParameterType);
                }
              }
            }

            // If any prototype changes can be made, record it.
            SingleValue returnValueForVirtualMethods =
                getReturnValueForVirtualMethods(methods, signature);
            DexType newReturnType =
                getNewReturnTypeForVirtualMethods(methods, returnValueForVirtualMethods);
            if (canBeConvertedToStaticMethod
                || newReturnType != null
                || !newParameterTypes.isEmpty()
                || !removableVirtualMethodParametersInAllMethods.isEmpty()) {
              allowedPrototypeChangesForVirtualMethods.put(
                  signature,
                  new AllowedPrototypeChanges(
                      canBeConvertedToStaticMethod,
                      newReturnType,
                      newParameterTypes,
                      removableVirtualMethodParametersInAllMethods));
            }

            // Also record the found return value for abstract virtual methods.
            if (newReturnType == dexItemFactory.voidType && returnValueForVirtualMethods != null) {
              for (ProgramMethod method : methods) {
                if (method.getAccessFlags().isAbstract()) {
                  returnValuesForVirtualMethods.put(method, returnValueForVirtualMethods);
                }
              }
            }
          });
    }

    private Map<DexMethodSignature, ProgramMethodSet> computeVirtualMethodsBySignature(
        Set<DexProgramClass> stronglyConnectedProgramClasses) {
      Map<DexMethodSignature, ProgramMethodSet> virtualMethodsBySignature = new HashMap<>();
      for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
        clazz.forEachProgramVirtualMethod(
            method ->
                virtualMethodsBySignature
                    .computeIfAbsent(
                        method.getMethodSignature(), ignoreKey(ProgramMethodSet::create))
                    .add(method));
      }
      return virtualMethodsBySignature;
    }

    private boolean isPrototypeChangesAllowed(
        ProgramMethod method, DexMethodSignatureSet interfaceDispatchOutsideProgram) {
      return appView.getKeepInfo(method).isParameterRemovalAllowed(options)
          && !method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
          && !appView.appInfo().isBootstrapMethod(method)
          && !interfaceDispatchOutsideProgram.contains(method);
    }

    private SingleValue getReturnValueForVirtualMethods(
        ProgramMethodSet methods, DexMethodSignature signature) {
      if (signature.getReturnType().isVoidType()) {
        return null;
      }

      SingleValue returnValue = null;
      for (ProgramMethod method : methods) {
        if (method.getDefinition().isAbstract()) {
          // OK, this can be rewritten to have void return type.
          continue;
        }
        if (!appView.appInfo().mayPropagateValueFor(appView, method)) {
          return null;
        }
        AbstractValue returnValueForMethod =
            method.getReturnType().isAlwaysNull(appView)
                ? appView.abstractValueFactory().createNullValue()
                : method.getOptimizationInfo().getAbstractReturnValue();
        if (!returnValueForMethod.isSingleValue()
            || !returnValueForMethod.asSingleValue().isMaterializableInAllContexts(appView)
            || (returnValue != null && !returnValueForMethod.equals(returnValue))) {
          return null;
        }
        returnValue = returnValueForMethod.asSingleValue();
      }
      return returnValue;
    }

    private boolean containsImmediateInterfaceOfInstantiatedLambda(ProgramMethodSet methods) {
      for (ProgramMethod method : methods) {
        DexProgramClass holder = method.getHolder();
        if (holder.isInterface()) {
          ObjectAllocationInfoCollection objectAllocationInfoCollection =
              appView.appInfo().getObjectAllocationInfoCollection();
          if (objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(holder)) {
            return true;
          }
        }
      }
      return false;
    }

    private boolean canRemoveReceiverFromVirtualMethods(ProgramMethodSet methods) {
      if (methods.size() > 1) {
        // Method staticizing would break dynamic dispatch.
        return false;
      }
      ProgramMethod method = methods.getFirst();
      return method.getOptimizationInfo().hasUnusedArguments()
          && method.getOptimizationInfo().getUnusedArguments().get(0)
          && ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)
          && ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, 0);
    }

    private boolean canRemoveParameterFromVirtualMethods(
        ProgramMethodSet methods, int parameterIndex) {
      int argumentIndex = parameterIndex + 1;
      for (ProgramMethod method : methods) {
        if (method.getDefinition().isAbstract()) {
          // OK, this parameter can be removed.
          continue;
        }
        if (method.getOptimizationInfo().hasUnusedArguments()
            && method.getOptimizationInfo().getUnusedArguments().get(argumentIndex)
            && ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)
            && ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, argumentIndex)) {
          // OK, this parameter is unused.
          continue;
        }
        CallSiteOptimizationInfo optimizationInfo = method.getOptimizationInfo().getArgumentInfos();
        if (optimizationInfo.isConcreteCallSiteOptimizationInfo()) {
          ConcreteCallSiteOptimizationInfo concreteOptimizationInfo =
              optimizationInfo.asConcreteCallSiteOptimizationInfo();
          AbstractValue abstractValue =
              concreteOptimizationInfo.getAbstractArgumentValue(argumentIndex);
          if (abstractValue.isSingleValue()
              && abstractValue.asSingleValue().isMaterializableInContext(appView, method)) {
            // OK, this parameter has a constant value and can be removed.
            continue;
          }
        }
        return false;
      }
      return true;
    }

    private DexType getNewReturnTypeForVirtualMethods(
        ProgramMethodSet methods, SingleValue returnValue) {
      if (returnValue != null || isReturnValueUnusedForVirtualMethods(methods)) {
        return dexItemFactory.voidType;
      }

      DexType newReturnType = null;
      for (ProgramMethod method : methods) {
        if (method.getDefinition().isAbstract()) {
          // OK, this method can have any return type.
          continue;
        }
        DexType newReturnTypeForMethod = getNewReturnType(method, OptionalBool.UNKNOWN, null);
        if (newReturnTypeForMethod == null
            || (newReturnType != null && newReturnType != newReturnTypeForMethod)) {
          return null;
        }
        newReturnType = newReturnTypeForMethod;
      }
      assert newReturnType == null || newReturnType != methods.getFirst().getReturnType();
      return newReturnType;
    }

    private boolean isReturnValueUnusedForVirtualMethods(ProgramMethodSet methods) {
      ProgramMethod representative = methods.getFirst();
      return !representative.getReturnType().isVoidType()
          && Iterables.all(
              methods,
              method ->
                  appView.getKeepInfo(method).isUnusedReturnValueOptimizationAllowed(options)
                      && method.getOptimizationInfo().isReturnValueUsed().isFalse());
    }

    private DexType getNewParameterTypeForVirtualMethods(
        ProgramMethodSet methods, int parameterIndex) {
      DexType newParameterType = null;
      for (ProgramMethod method : methods) {
        if (method.getAccessFlags().isAbstract()) {
          // OK, this parameter can have any type.
          continue;
        }
        DexType newParameterTypeForMethod = getNewParameterType(method, parameterIndex);
        if (newParameterTypeForMethod == null
            || (newParameterType != null && newParameterType != newParameterTypeForMethod)) {
          return null;
        }
        newParameterType = newParameterTypeForMethod;
      }
      assert newParameterType == null
          || newParameterType != methods.getFirst().getParameter(parameterIndex);
      return newParameterType;
    }

    // Returns true if the class was changed as a result of argument propagation.
    private boolean visitClass(
        DexProgramClass clazz,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder) {
      BooleanBox affected = new BooleanBox();
      Set<DexField> newFieldSignatures = Sets.newIdentityHashSet();
      Map<DexField, DexType> newFieldTypes = new IdentityHashMap<>();
      clazz.forEachProgramFieldMatching(
          field -> field.getType().isClassType(),
          field -> {
            DexType newFieldType = getNewFieldType(field);
            if (newFieldType != field.getType()) {
              newFieldTypes.put(field.getReference(), newFieldType);
            } else {
              // Reserve field signature.
              newFieldSignatures.add(field.getReference());
            }
          });
      clazz.forEachProgramFieldMatching(
          field -> field.getType().isClassType(),
          field -> {
            DexField newFieldSignature =
                getNewFieldSignature(field, newFieldSignatures, newFieldTypes);
            if (newFieldSignature != field.getReference()) {
              partialGraphLensBuilder.recordMove(field.getReference(), newFieldSignature);
              affected.set();
            }
          });
      DexMethodSignatureSet instanceInitializerSignatures = DexMethodSignatureSet.create();
      ProgramMethodMap<RewrittenPrototypeDescription> instanceInitializerPrototypeChanges =
          ProgramMethodMap.create();
      clazz.forEachProgramInstanceInitializer(
          method -> {
            RewrittenPrototypeDescription prototypeChanges =
                computePrototypeChangesForDirectMethod(
                    method, interfaceDispatchOutsideProgram, null);
            if (prototypeChanges.isEmpty()) {
              instanceInitializerSignatures.add(method);
            }
            instanceInitializerPrototypeChanges.put(method, prototypeChanges);
          });
      clazz.forEachProgramMethod(
          method -> {
            RewrittenPrototypeDescription prototypeChanges =
                instanceInitializerPrototypeChanges.getOrDefault(
                    method,
                    () ->
                        method.getDefinition().belongsToDirectPool()
                            ? computePrototypeChangesForDirectMethod(
                                method,
                                interfaceDispatchOutsideProgram,
                                instanceInitializerSignatures)
                            : computePrototypeChangesForVirtualMethod(method));
            if (method.getDefinition().isInstanceInitializer()) {
              if (prototypeChanges.isEmpty()) {
                assert instanceInitializerSignatures.contains(method);
                return;
              }
              prototypeChanges =
                  selectInitArgumentTypeForInstanceInitializer(
                      method, prototypeChanges, instanceInitializerSignatures);
            }
            DexMethod newMethodSignature = getNewMethodSignature(method, prototypeChanges);
            if (newMethodSignature != method.getReference()) {
              partialGraphLensBuilder.recordMove(
                  method.getReference(), newMethodSignature, prototypeChanges);
              affected.set();
            } else if (!prototypeChanges.isEmpty()) {
              partialGraphLensBuilder.recordStaticized(method.getReference(), prototypeChanges);
              affected.set();
            }
          });
      return affected.get();
    }

    private DexType getNewFieldType(ProgramField field) {
      DynamicType dynamicType = field.getOptimizationInfo().getDynamicType();
      DexType staticType = field.getType();
      if (dynamicType.isUnknown()) {
        return staticType;
      }

      KeepFieldInfo keepInfo = appView.getKeepInfo(field);

      // We don't have dynamic type information for fields that are kept.
      assert !keepInfo.isPinned(options);

      if (!keepInfo.isFieldTypeStrengtheningAllowed(options)) {
        return staticType;
      }

      if (dynamicType.isNullType()) {
        // Don't optimize always null fields; these will be optimized anyway.
        return staticType;
      }

      if (dynamicType.isNotNullType()) {
        // We don't have a more specific type.
        return staticType;
      }

      DynamicTypeWithUpperBound dynamicTypeWithUpperBound =
          dynamicType.asDynamicTypeWithUpperBound();
      TypeElement dynamicUpperBoundType = dynamicTypeWithUpperBound.getDynamicUpperBoundType();
      assert dynamicUpperBoundType.isReferenceType();

      ClassTypeElement staticFieldType = staticType.toTypeElement(appView).asClassType();
      if (dynamicUpperBoundType.equalUpToNullability(staticFieldType)) {
        // We don't have more precise type information.
        return staticType;
      }

      if (!dynamicUpperBoundType.strictlyLessThan(staticFieldType, appView)) {
        assert options.testing.allowTypeErrors;
        return staticType;
      }

      DexType newStaticFieldType;
      if (dynamicUpperBoundType.isClassType()) {
        ClassTypeElement dynamicUpperBoundClassType = dynamicUpperBoundType.asClassType();
        if (dynamicUpperBoundClassType.getClassType() == dexItemFactory.objectType) {
          if (dynamicUpperBoundClassType.getInterfaces().hasSingleKnownInterface()) {
            newStaticFieldType =
                dynamicUpperBoundClassType.getInterfaces().getSingleKnownInterface();
          } else {
            return staticType;
          }
        } else {
          newStaticFieldType = dynamicUpperBoundClassType.getClassType();
        }
      } else {
        newStaticFieldType = dynamicUpperBoundType.asArrayType().toDexType(dexItemFactory);
      }

      if (newStaticFieldType == staticType) {
        return staticType;
      }

      if (!AccessUtils.isAccessibleInSameContextsAs(newStaticFieldType, staticType, appView)) {
        return staticType;
      }

      if (!AndroidApiLevelUtils.isApiSafeForTypeStrengthening(
          newStaticFieldType, staticType, appView)) {
        return staticType;
      }

      return newStaticFieldType;
    }

    private DexField getNewFieldSignature(
        ProgramField field,
        Set<DexField> newFieldSignatures,
        Map<DexField, DexType> newFieldTypes) {
      DexType newFieldType = newFieldTypes.getOrDefault(field.getReference(), field.getType());
      if (newFieldType == field.getType()) {
        assert newFieldSignatures.contains(field.getReference());
        return field.getReference();
      }
      // Find a new name for this field if the signature is already occupied.
      return dexItemFactory.createFreshFieldNameWithoutHolder(
          field.getHolderType(), newFieldType, field.getName().toString(), newFieldSignatures::add);
    }

    private DexMethod getNewMethodSignature(
        ProgramMethod method, RewrittenPrototypeDescription prototypeChanges) {
      DexMethodSignature methodSignatureWithoutPrototypeChanges = method.getMethodSignature();
      AllowedPrototypeChanges allowedPrototypeChanges =
          AllowedPrototypeChanges.create(method, prototypeChanges);

      // Check if there is a reserved signature for this already.
      DexMethodSignature reservedSignature =
          newMethodSignatures
              .getOrDefault(methodSignatureWithoutPrototypeChanges, Collections.emptyMap())
              .get(allowedPrototypeChanges);
      if (reservedSignature != null) {
        assert reservedSignature
            .getProto()
            .equals(prototypeChanges.rewriteMethod(method, dexItemFactory).getProto());
        return reservedSignature.withHolder(method.getHolderType(), dexItemFactory);
      }

      DexMethod methodReferenceWithParametersRemoved =
          prototypeChanges.rewriteMethod(method, dexItemFactory);
      DexMethodSignature methodSignatureWithParametersRemoved =
          methodReferenceWithParametersRemoved.getSignature();

      // Find a method signature. First check if the current signature is available.
      if (!occupiedMethodSignatures.containsKey(methodSignatureWithParametersRemoved)) {
        if (!method.getDefinition().isInstanceInitializer()) {
          reserveMethodSignature(
              methodSignatureWithParametersRemoved,
              methodSignatureWithoutPrototypeChanges,
              allowedPrototypeChanges);
        }
        return methodReferenceWithParametersRemoved;
      }

      Pair<AllowedPrototypeChanges, DexMethodSignature> occupant =
          occupiedMethodSignatures.get(methodSignatureWithParametersRemoved);
      // In this case we should have found a reserved method signature above.
      assert !(occupant.getFirst().equals(allowedPrototypeChanges)
          && occupant.getSecond().equals(methodSignatureWithoutPrototypeChanges));

      // We need to find a new name for this method, since the signature is already occupied.
      // TODO(b/190154391): Instead of generating a new name, we could also try permuting the order
      // of parameters.
      IntBox suffix =
          newMethodSignatureSuffixes.computeIfAbsent(
              methodSignatureWithParametersRemoved, ignoreKey(IntBox::new));
      DexMethod newMethod =
          dexItemFactory.createFreshMethodNameWithoutHolder(
              method.getName().toString(),
              methodReferenceWithParametersRemoved.getProto(),
              method.getHolderType(),
              candidate -> {
                suffix.increment();
                return isMethodSignatureFresh(
                    candidate.getSignature(),
                    methodSignatureWithoutPrototypeChanges,
                    allowedPrototypeChanges);
              },
              suffix.get());

      // Reserve the newly generated method signature.
      if (!method.getDefinition().isInstanceInitializer()) {
        reserveMethodSignature(
            newMethod.getSignature(),
            methodSignatureWithoutPrototypeChanges,
            allowedPrototypeChanges);
      }

      return newMethod;
    }

    private boolean isMethodSignatureFresh(
        DexMethodSignature signature,
        DexMethodSignature previous,
        AllowedPrototypeChanges allowedPrototypeChanges) {
      Pair<AllowedPrototypeChanges, DexMethodSignature> candidateOccupant =
          occupiedMethodSignatures.get(signature);
      if (candidateOccupant == null) {
        return true;
      }
      return candidateOccupant.getFirst().equals(allowedPrototypeChanges)
          && candidateOccupant.getSecond().equals(previous);
    }

    private RewrittenPrototypeDescription computePrototypeChangesForDirectMethod(
        ProgramMethod method,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        DexMethodSignatureSet instanceInitializerSignatures) {
      assert method.getDefinition().belongsToDirectPool();
      RewrittenPrototypeDescription prototypeChanges =
          isPrototypeChangesAllowed(method, interfaceDispatchOutsideProgram)
              ? computePrototypeChangesForMethod(method)
              : RewrittenPrototypeDescription.none();
      if (method.getDefinition().isInstanceInitializer() && instanceInitializerSignatures != null) {
        prototypeChanges =
            selectInitArgumentTypeForInstanceInitializer(
                method, prototypeChanges, instanceInitializerSignatures);
      }
      return prototypeChanges;
    }

    private RewrittenPrototypeDescription selectInitArgumentTypeForInstanceInitializer(
        ProgramMethod method,
        RewrittenPrototypeDescription prototypeChanges,
        DexMethodSignatureSet instanceInitializerSignatures) {
      DexMethod rewrittenMethod = prototypeChanges.rewriteMethod(method, dexItemFactory);
      if (instanceInitializerSignatures.add(rewrittenMethod)) {
        return prototypeChanges;
      }
      if (!callSiteOptimizationOptions.isForceSyntheticsForInstanceInitializersEnabled()) {
        for (DexType extraArgumentType :
            ImmutableList.of(dexItemFactory.intType, dexItemFactory.objectType)) {
          RewrittenPrototypeDescription candidatePrototypeChanges =
              prototypeChanges.withExtraParameters(new ExtraUnusedNullParameter(extraArgumentType));
          rewrittenMethod = candidatePrototypeChanges.rewriteMethod(method, dexItemFactory);
          if (instanceInitializerSignatures.add(rewrittenMethod)) {
            return candidatePrototypeChanges;
          }
        }
      }
      DexProgramClass extraArgumentClass =
          appView
              .getSyntheticItems()
              .createClass(
                  kinds -> kinds.NON_FIXED_INIT_TYPE_ARGUMENT,
                  processorContext.createMethodProcessingContext(method).createUniqueContext(),
                  appView);
      eventConsumer.acceptInitializerArgumentClass(extraArgumentClass, method);
      RewrittenPrototypeDescription finalPrototypeChanges =
          prototypeChanges.withExtraParameters(
              new ExtraUnusedNullParameter(extraArgumentClass.getType()));
      boolean added =
          instanceInitializerSignatures.add(
              finalPrototypeChanges.rewriteMethod(method, dexItemFactory));
      assert added;
      return finalPrototypeChanges;
    }

    private RewrittenPrototypeDescription computePrototypeChangesForVirtualMethod(
        ProgramMethod method) {
      AllowedPrototypeChanges allowedPrototypeChanges =
          allowedPrototypeChangesForVirtualMethods.get(method.getMethodSignature());
      if (allowedPrototypeChanges == null) {
        return RewrittenPrototypeDescription.none();
      }

      IntSet removableParameterIndices = allowedPrototypeChanges.removableParameterIndices;
      Int2ReferenceMap<DexType> newParameterTypes = allowedPrototypeChanges.newParameterTypes;
      if (method.getAccessFlags().isAbstract()) {
        return computePrototypeChangesForAbstractVirtualMethod(
            method,
            allowedPrototypeChanges.newReturnType,
            newParameterTypes,
            removableParameterIndices);
      }

      boolean canBeConvertedToStaticMethod = allowedPrototypeChanges.canBeConvertedToStaticMethod;
      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChangesForMethod(
              method,
              canBeConvertedToStaticMethod,
              allowedPrototypeChanges.newReturnType,
              newParameterTypes::get,
              removableParameterIndices::contains);
      assert prototypeChanges.getArgumentInfoCollection().numberOfRemovedArguments()
          == BooleanUtils.intValue(canBeConvertedToStaticMethod) + removableParameterIndices.size();
      return prototypeChanges;
    }

    private RewrittenPrototypeDescription computePrototypeChangesForAbstractVirtualMethod(
        ProgramMethod method,
        DexType newReturnType,
        Int2ReferenceMap<DexType> newParameterTypes,
        IntSet removableParameterIndices) {
      // Treat the parameters as unused.
      ArgumentInfoCollection.Builder argumentInfoCollectionBuilder =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(method.getDefinition().getNumberOfArguments());
      method
          .getParameters()
          .forEach(
              (parameterIndex, parameter) -> {
                int argumentIndex =
                    method.getDefinition().getArgumentIndexFromParameterIndex(parameterIndex);
                if (removableParameterIndices.contains(parameterIndex)) {
                  argumentInfoCollectionBuilder.addArgumentInfo(
                      argumentIndex, RemovedArgumentInfo.builder().setType(parameter).build());
                } else if (newParameterTypes.containsKey(parameterIndex)) {
                  DexType newParameterType = newParameterTypes.get(parameterIndex);
                  argumentInfoCollectionBuilder.addArgumentInfo(
                      argumentIndex,
                      RewrittenTypeInfo.builder()
                          .setCastType(newParameterType)
                          .setOldType(parameter)
                          .setNewType(newParameterType)
                          .build());
                }
              });
      return RewrittenPrototypeDescription.create(
          Collections.emptyList(),
          computeReturnChangesForMethod(method, newReturnType),
          argumentInfoCollectionBuilder.build());
    }

    private RewrittenPrototypeDescription computePrototypeChangesForMethod(ProgramMethod method) {
      IntFunction<DexType> parameterIndexToParameterType =
          appView.getKeepInfo(method).isParameterTypeStrengtheningAllowed(options)
              ? parameterIndex -> getNewParameterType(method, parameterIndex)
              : parameterIndex -> null;
      return computePrototypeChangesForMethod(
          method,
          true,
          getNewReturnType(method),
          parameterIndexToParameterType,
          parameterIndex -> true);
    }

    private DexType getNewReturnType(ProgramMethod method) {
      return getNewReturnType(
          method, method.getOptimizationInfo().isReturnValueUsed(), getReturnValue(method));
    }

    private DexType getNewReturnType(
        ProgramMethod method, OptionalBool isReturnValueUsed, SingleValue returnValue) {
      DexType staticType = method.getReturnType();
      if (staticType.isVoidType()) {
        return null;
      }
      if (returnValue != null) {
        return dexItemFactory.voidType;
      }
      KeepMethodInfo keepInfo = appView.getKeepInfo(method);
      if (keepInfo.isUnusedReturnValueOptimizationAllowed(options) && isReturnValueUsed.isFalse()) {
        return dexItemFactory.voidType;
      }
      if (!keepInfo.isReturnTypeStrengtheningAllowed(options)) {
        return null;
      }
      TypeElement newReturnTypeElement =
          method
              .getOptimizationInfo()
              .getDynamicType()
              .getDynamicUpperBoundType(staticType.toTypeElement(appView));
      assert newReturnTypeElement.isTop()
          || newReturnTypeElement.lessThanOrEqual(staticType.toTypeElement(appView), appView);
      if (!newReturnTypeElement.isClassType()) {
        assert newReturnTypeElement.isArrayType()
            || newReturnTypeElement.isNullType()
            || newReturnTypeElement.isTop();
        return null;
      }
      DexType newReturnType = newReturnTypeElement.asClassType().toDexType(dexItemFactory);
      if (newReturnType == staticType) {
        return null;
      }
      if (!appView.appInfo().isSubtype(newReturnType, staticType)) {
        return null;
      }
      if (!AccessUtils.isAccessibleInSameContextsAs(newReturnType, staticType, appView)) {
        return null;
      }
      if (!AndroidApiLevelUtils.isApiSafeForTypeStrengthening(newReturnType, staticType, appView)) {
        return null;
      }
      return newReturnType;
    }

    private SingleValue getReturnValue(ProgramMethod method) {
      AbstractValue returnValue;
      if (method.getReturnType().isAlwaysNull(appView)) {
        returnValue = appView.abstractValueFactory().createNullValue();
      } else if (method.getDefinition().belongsToVirtualPool()
          && returnValuesForVirtualMethods.containsKey(method)) {
        assert method.getAccessFlags().isAbstract();
        returnValue = returnValuesForVirtualMethods.get(method);
      } else {
        returnValue = method.getOptimizationInfo().getAbstractReturnValue();
      }
      return returnValue.isSingleValue()
              && returnValue.asSingleValue().isMaterializableInAllContexts(appView)
          ? returnValue.asSingleValue()
          : null;
    }

    private DexType getNewParameterType(ProgramMethod method, int parameterIndex) {
      if (!appView.getKeepInfo(method).isParameterTypeStrengtheningAllowed(options)) {
        return null;
      }
      DexType staticType = method.getParameter(parameterIndex);
      if (!staticType.isClassType()) {
        return null;
      }
      int argumentIndex = method.getDefinition().getArgumentIndexFromParameterIndex(parameterIndex);
      DynamicType dynamicType =
          method.getOptimizationInfo().getArgumentInfos().getDynamicType(argumentIndex);
      if (dynamicType == null || dynamicType.isUnknown()) {
        return null;
      }
      TypeElement staticTypeElement = staticType.toTypeElement(appView);
      TypeElement dynamicUpperBoundType = dynamicType.getDynamicUpperBoundType(staticTypeElement);
      assert dynamicUpperBoundType.lessThanOrEqual(staticTypeElement, appView);
      assert dynamicUpperBoundType.isReferenceType();
      if (dynamicUpperBoundType.isNullType()) {
        return null;
      }
      if (dynamicUpperBoundType.isArrayType()) {
        return null;
      }
      assert dynamicUpperBoundType.isClassType();
      DexType newParameterType = dynamicUpperBoundType.asClassType().toDexType(dexItemFactory);
      if (newParameterType == staticType) {
        return null;
      }
      if (!appView.appInfo().isSubtype(newParameterType, staticType)) {
        return null;
      }
      if (!AccessUtils.isAccessibleInSameContextsAs(newParameterType, staticType, appView)) {
        return null;
      }
      if (!AndroidApiLevelUtils.isApiSafeForTypeStrengthening(
          newParameterType, staticType, appView)) {
        return null;
      }
      return newParameterType;
    }

    private RewrittenPrototypeDescription computePrototypeChangesForMethod(
        ProgramMethod method,
        boolean canBeConvertedToStaticMethod,
        DexType newReturnType,
        IntFunction<DexType> newParameterTypes,
        IntPredicate removableParameterIndices) {
      return RewrittenPrototypeDescription.create(
          Collections.emptyList(),
          computeReturnChangesForMethod(method, newReturnType),
          computeParameterChangesForMethod(
              method, canBeConvertedToStaticMethod, newParameterTypes, removableParameterIndices));
    }

    private ArgumentInfoCollection computeParameterChangesForMethod(
        ProgramMethod method,
        boolean canBeConvertedToStaticMethod,
        IntFunction<DexType> newParameterTypes,
        IntPredicate removableParameterIndices) {
      ArgumentInfoCollection.Builder parameterChangesBuilder =
          ArgumentInfoCollection.builder()
              .setArgumentInfosSize(method.getDefinition().getNumberOfArguments());
      if (method.getDefinition().isInstance()
          && canBeConvertedToStaticMethod
          && method.getOptimizationInfo().hasUnusedArguments()
          && method.getOptimizationInfo().getUnusedArguments().get(0)
          && ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)
          && ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, 0)) {
        parameterChangesBuilder
            .addArgumentInfo(
                0, RemovedReceiverInfo.Builder.create().setType(method.getHolderType()).build())
            .setIsConvertedToStaticMethod();
      }

      CallSiteOptimizationInfo optimizationInfo = method.getOptimizationInfo().getArgumentInfos();
      for (int parameterIndex = 0;
          parameterIndex < method.getParameters().size();
          parameterIndex++) {
        int argumentIndex =
            parameterIndex + method.getDefinition().getFirstNonReceiverArgumentIndex();
        if (removableParameterIndices.test(parameterIndex)) {
          if (method.getOptimizationInfo().hasUnusedArguments()
              && method.getOptimizationInfo().getUnusedArguments().get(argumentIndex)
              && ParameterRemovalUtils.canRemoveUnusedParametersFrom(appView, method)
              && ParameterRemovalUtils.canRemoveUnusedParameter(appView, method, argumentIndex)) {
            parameterChangesBuilder.addArgumentInfo(
                argumentIndex,
                RemovedArgumentInfo.builder().setType(method.getParameter(parameterIndex)).build());
            continue;
          }

          AbstractValue abstractValue = optimizationInfo.getAbstractArgumentValue(argumentIndex);
          if (abstractValue.isSingleValue()
              && abstractValue.asSingleValue().isMaterializableInContext(appView, method)) {
            parameterChangesBuilder.addArgumentInfo(
                argumentIndex,
                RemovedArgumentInfo.builder()
                    .setSingleValue(abstractValue.asSingleValue())
                    .setType(method.getParameter(parameterIndex))
                    .build());
            continue;
          }
        }

        DexType dynamicType = newParameterTypes.apply(parameterIndex);
        if (dynamicType != null) {
          DexType staticType = method.getParameter(parameterIndex);
          assert dynamicType != staticType;
          parameterChangesBuilder.addArgumentInfo(
              argumentIndex,
              RewrittenTypeInfo.builder()
                  .setCastType(dynamicType)
                  .setOldType(staticType)
                  .setNewType(dynamicType)
                  .build());
        }
      }
      return parameterChangesBuilder.build();
    }

    private RewrittenTypeInfo computeReturnChangesForMethod(
        ProgramMethod method, DexType newReturnType) {
      if (newReturnType == null) {
        assert !returnValuesForVirtualMethods.containsKey(method);
        return null;
      }
      assert newReturnType != method.getReturnType();
      return RewrittenTypeInfo.builder()
          .applyIf(
              newReturnType == dexItemFactory.voidType,
              builder -> builder.setSingleValue(getReturnValue(method)))
          .setCastType(newReturnType)
          .setOldType(method.getReturnType())
          .setNewType(newReturnType)
          .build();
    }
  }
}
