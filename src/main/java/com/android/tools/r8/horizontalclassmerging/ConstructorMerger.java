// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.dex.Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.structural.Ordered;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class ConstructorMerger {
  private final AppView<?> appView;
  private final MergeGroup group;
  private final Collection<DexEncodedMethod> constructors;
  private final DexItemFactory dexItemFactory;

  ConstructorMerger(
      AppView<?> appView, MergeGroup group, Collection<DexEncodedMethod> constructors) {
    this.appView = appView;
    this.group = group;
    this.constructors = constructors;

    // Constructors should not be empty and all constructors should have the same prototype.
    assert !constructors.isEmpty();
    assert constructors.stream().map(DexEncodedMethod::proto).distinct().count() == 1;

    this.dexItemFactory = appView.dexItemFactory();
  }

  /**
   * The method reference template describes which arguments the constructor must have, and is used
   * to generate the final reference by appending null arguments until it is fresh.
   */
  private DexMethod generateReferenceMethodTemplate() {
    DexMethod methodTemplate = constructors.iterator().next().getReference();
    if (!isTrivialMerge()) {
      methodTemplate = dexItemFactory.appendTypeToMethod(methodTemplate, dexItemFactory.intType);
    }
    return methodTemplate;
  }

  public int getArity() {
    return constructors.iterator().next().getReference().getArity();
  }

  public static class Builder {
    private int estimatedDexCodeSize;
    private final List<List<DexEncodedMethod>> constructorGroups = new ArrayList<>();
    private AppView<AppInfoWithLiveness> appView;

    public Builder(AppView<AppInfoWithLiveness> appView) {
      this.appView = appView;

      createNewGroup();
    }

    private void createNewGroup() {
      estimatedDexCodeSize = 0;
      constructorGroups.add(new ArrayList<>());
    }

    public Builder add(DexEncodedMethod constructor) {
      int estimatedMaxSizeInBytes = constructor.getCode().estimatedDexCodeSizeUpperBoundInBytes();
      // If the constructor gets too large, then the constructor should be merged into a new group.
      if (estimatedDexCodeSize + estimatedMaxSizeInBytes
              > appView.options().minimumVerificationSizeLimitInBytes() / 2
          && estimatedDexCodeSize > 0) {
        createNewGroup();
      }

      ListUtils.last(constructorGroups).add(constructor);
      estimatedDexCodeSize += estimatedMaxSizeInBytes;
      return this;
    }

    public List<ConstructorMerger> build(AppView<?> appView, MergeGroup group) {
      assert constructorGroups.stream().noneMatch(List::isEmpty);
      return ListUtils.map(
          constructorGroups, constructors -> new ConstructorMerger(appView, group, constructors));
    }
  }

  private boolean isTrivialMerge() {
    return constructors.size() == 1;
  }

  private DexMethod moveConstructor(
      ClassMethodsBuilder classMethodsBuilder, DexEncodedMethod constructor) {
    DexMethod method =
        dexItemFactory.createFreshMethodName(
            TEMPORARY_INSTANCE_INITIALIZER_PREFIX,
            constructor.getHolderType(),
            constructor.proto(),
            group.getTarget().getType(),
            classMethodsBuilder::isFresh);

    DexEncodedMethod encodedMethod = constructor.toTypeSubstitutedMethod(method);
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
    for (DexEncodedMethod constructor : constructors) {
      if (constructor.hasClassFileVersion()) {
        classFileVersion =
            Ordered.maxIgnoreNull(classFileVersion, constructor.getClassFileVersion());
      }
      DexMethod movedConstructor = moveConstructor(classMethodsBuilder, constructor);
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

    DexEncodedMethod representative = constructors.iterator().next();
    DexMethod originalConstructorReference =
        appView.graphLens().getOriginalMethodSignature(representative.getReference());

    // Create a special original method signature for the synthesized constructor that did not exist
    // prior to horizontal class merging. Otherwise we might accidentally think that the synthesized
    // constructor corresponds to the previous <init>() method on the target class, which could have
    // unintended side-effects such as leading to unused argument removal being applied to the
    // synthesized constructor all-though it by construction doesn't have any unused arguments.
    DexMethod bridgeConstructorReference =
        dexItemFactory.createFreshMethodName(
            "$r8$init$bridge",
            null,
            originalConstructorReference.getProto(),
            originalConstructorReference.getHolderType(),
            classMethodsBuilder::isFresh);

    ConstructorEntryPointSynthesizedCode synthesizedCode =
        new ConstructorEntryPointSynthesizedCode(
            typeConstructorClassMap,
            newConstructorReference,
            group.getClassIdField(),
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
    for (DexEncodedMethod oldConstructor : constructors) {
      List<ExtraParameter> extraParameters = new ArrayList<>();
      if (constructors.size() > 1) {
        int classIdentifier = classIdentifiers.getInt(oldConstructor.getHolderType());
        extraParameters.add(new ExtraConstantIntParameter(classIdentifier));
      }
      extraParameters.addAll(Collections.nCopies(extraNulls, new ExtraUnusedNullParameter()));
      lensBuilder.mapMergedConstructor(
          oldConstructor.getReference(), newConstructorReference, extraParameters);
    }

    // Add a mapping from a synthetic name to the synthetic constructor.
    lensBuilder.recordNewMethodSignature(bridgeConstructorReference, newConstructorReference);

    classMethodsBuilder.addDirectMethod(newConstructor);
  }
}
