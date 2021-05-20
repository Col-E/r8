// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.dex.Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.code.ConstructorEntryPointSynthesizedCode;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.structural.Ordered;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InstanceInitializerMerger {

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final MergeGroup group;
  private final List<ProgramMethod> instanceInitializers;

  InstanceInitializerMerger(
      AppView<?> appView, MergeGroup group, List<ProgramMethod> instanceInitializers) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.group = group;
    this.instanceInitializers = instanceInitializers;

    // Constructors should not be empty and all constructors should have the same prototype.
    assert !instanceInitializers.isEmpty();
    assert instanceInitializers.stream().map(ProgramMethod::getProto).distinct().count() == 1;
  }

  /**
   * The method reference template describes which arguments the constructor must have, and is used
   * to generate the final reference by appending null arguments until it is fresh.
   */
  private DexMethod generateReferenceMethodTemplate() {
    DexMethod methodTemplate = instanceInitializers.iterator().next().getReference();
    if (!isTrivialMerge()) {
      methodTemplate = dexItemFactory.appendTypeToMethod(methodTemplate, dexItemFactory.intType);
    }
    return methodTemplate;
  }

  public int getArity() {
    return instanceInitializers.iterator().next().getReference().getArity();
  }

  public static class Builder {
    private int estimatedDexCodeSize;
    private final List<List<ProgramMethod>> instanceInitializerGroups = new ArrayList<>();
    private final AppView<? extends AppInfoWithClassHierarchy> appView;

    public Builder(AppView<? extends AppInfoWithClassHierarchy> appView) {
      this.appView = appView;
      createNewGroup();
    }

    private void createNewGroup() {
      estimatedDexCodeSize = 0;
      instanceInitializerGroups.add(new ArrayList<>());
    }

    public Builder add(ProgramMethod instanceInitializer) {
      int estimatedMaxSizeInBytes =
          instanceInitializer.getDefinition().getCode().estimatedDexCodeSizeUpperBoundInBytes();
      // If the constructor gets too large, then the constructor should be merged into a new group.
      if (estimatedDexCodeSize + estimatedMaxSizeInBytes
              > appView.options().minimumVerificationSizeLimitInBytes() / 2
          && estimatedDexCodeSize > 0) {
        createNewGroup();
      }

      ListUtils.last(instanceInitializerGroups).add(instanceInitializer);
      estimatedDexCodeSize += estimatedMaxSizeInBytes;
      return this;
    }

    public List<InstanceInitializerMerger> build(MergeGroup group) {
      assert instanceInitializerGroups.stream().noneMatch(List::isEmpty);
      return ListUtils.map(
          instanceInitializerGroups,
          instanceInitializers ->
              new InstanceInitializerMerger(appView, group, instanceInitializers));
    }
  }

  private boolean isTrivialMerge() {
    return instanceInitializers.size() == 1;
  }

  private DexMethod moveInstanceInitializer(
      ClassMethodsBuilder classMethodsBuilder, ProgramMethod instanceInitializer) {
    DexMethod method =
        dexItemFactory.createFreshMethodNameWithHolder(
            TEMPORARY_INSTANCE_INITIALIZER_PREFIX,
            instanceInitializer.getHolderType(),
            instanceInitializer.getProto(),
            group.getTarget().getType(),
            classMethodsBuilder::isFresh);

    DexEncodedMethod encodedMethod =
        instanceInitializer.getDefinition().toTypeSubstitutedMethod(method);
    encodedMethod.getMutableOptimizationInfo().markForceInline();
    encodedMethod.getAccessFlags().unsetConstructor();
    encodedMethod.getAccessFlags().unsetPublic();
    encodedMethod.getAccessFlags().unsetProtected();
    encodedMethod.getAccessFlags().setPrivate();
    classMethodsBuilder.addDirectMethod(encodedMethod);

    return method;
  }

  private MethodAccessFlags getAccessFlags() {
    // TODO(b/164998929): ensure this behaviour is correct, should probably calculate upper bound
    return MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true);
  }

  /** Synthesize a new method which selects the constructor based on a parameter type. */
  void merge(
      ClassMethodsBuilder classMethodsBuilder,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Reference2IntMap<DexType> classIdentifiers,
      SyntheticArgumentClass syntheticArgumentClass) {
    // Tree map as must be sorted.
    Int2ReferenceSortedMap<DexMethod> typeConstructorClassMap = new Int2ReferenceAVLTreeMap<>();

    CfVersion classFileVersion = null;
    for (ProgramMethod constructor : instanceInitializers) {
      if (constructor.getDefinition().hasClassFileVersion()) {
        classFileVersion =
            Ordered.maxIgnoreNull(
                classFileVersion, constructor.getDefinition().getClassFileVersion());
      }
      DexMethod movedConstructor = moveInstanceInitializer(classMethodsBuilder, constructor);
      lensBuilder.mapMethod(movedConstructor, movedConstructor);
      lensBuilder.recordNewMethodSignature(constructor.getReference(), movedConstructor);
      typeConstructorClassMap.put(
          classIdentifiers.getInt(constructor.getHolderType()), movedConstructor);
    }

    DexMethod methodReferenceTemplate = generateReferenceMethodTemplate();
    DexMethod newConstructorReference =
        dexItemFactory.createInstanceInitializerWithFreshProto(
            methodReferenceTemplate.withHolder(group.getTarget().getType(), dexItemFactory),
            syntheticArgumentClass.getArgumentClasses(),
            classMethodsBuilder::isFresh);
    int extraNulls = newConstructorReference.getArity() - methodReferenceTemplate.getArity();

    ProgramMethod representative = ListUtils.first(instanceInitializers);
    DexMethod originalConstructorReference =
        appView.graphLens().getOriginalMethodSignature(representative.getReference());

    // Create a special original method signature for the synthesized constructor that did not exist
    // prior to horizontal class merging. Otherwise we might accidentally think that the synthesized
    // constructor corresponds to the previous <init>() method on the target class, which could have
    // unintended side-effects such as leading to unused argument removal being applied to the
    // synthesized constructor all-though it by construction doesn't have any unused arguments.
    DexMethod bridgeConstructorReference =
        dexItemFactory.createFreshMethodNameWithoutHolder(
            "$r8$init$bridge",
            originalConstructorReference.getProto(),
            originalConstructorReference.getHolderType(),
            classMethodsBuilder::isFresh);

    ConstructorEntryPointSynthesizedCode synthesizedCode =
        new ConstructorEntryPointSynthesizedCode(
            typeConstructorClassMap,
            newConstructorReference,
            group.hasClassIdField() ? group.getClassIdField() : null,
            bridgeConstructorReference);
    DexEncodedMethod newConstructor =
        new DexEncodedMethod(
            newConstructorReference,
            getAccessFlags(),
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            synthesizedCode,
            true,
            classFileVersion);

    // Map each old constructor to the newly synthesized constructor in the graph lens.
    for (ProgramMethod oldInstanceInitializer : instanceInitializers) {
      List<ExtraParameter> extraParameters = new ArrayList<>();
      if (instanceInitializers.size() > 1) {
        int classIdentifier = classIdentifiers.getInt(oldInstanceInitializer.getHolderType());
        extraParameters.add(new ExtraConstantIntParameter(classIdentifier));
      }
      extraParameters.addAll(Collections.nCopies(extraNulls, new ExtraUnusedNullParameter()));
      lensBuilder.mapMergedConstructor(
          oldInstanceInitializer.getReference(), newConstructorReference, extraParameters);
    }

    // Add a mapping from a synthetic name to the synthetic constructor.
    lensBuilder.recordNewMethodSignature(bridgeConstructorReference, newConstructorReference);

    classMethodsBuilder.addDirectMethod(newConstructor);
  }
}
