// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.android.tools.r8.dex.Constants.TEMPORARY_INSTANCE_INITIALIZER_PREFIX;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.code.ConstructorEntryPointSynthesizedCode;
import com.android.tools.r8.horizontalclassmerging.code.SyntheticInitializerConverter;
import com.android.tools.r8.ir.conversion.ExtraConstantIntParameter;
import com.android.tools.r8.ir.conversion.ExtraParameter;
import com.android.tools.r8.ir.conversion.ExtraUnusedNullParameter;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class InstanceInitializerMerger {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Reference2IntMap<DexType> classIdentifiers;
  private final DexItemFactory dexItemFactory;
  private final MergeGroup group;
  private final List<ProgramMethod> instanceInitializers;
  private final InstanceInitializerDescription instanceInitializerDescription;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final Mode mode;

  InstanceInitializerMerger(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Reference2IntMap<DexType> classIdentifiers,
      MergeGroup group,
      List<ProgramMethod> instanceInitializers,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Mode mode) {
    this(appView, classIdentifiers, group, instanceInitializers, lensBuilder, mode, null);
  }

  InstanceInitializerMerger(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Reference2IntMap<DexType> classIdentifiers,
      MergeGroup group,
      List<ProgramMethod> instanceInitializers,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Mode mode,
      InstanceInitializerDescription instanceInitializerDescription) {
    this.appView = appView;
    this.classIdentifiers = classIdentifiers;
    this.dexItemFactory = appView.dexItemFactory();
    this.group = group;
    this.instanceInitializers = instanceInitializers;
    this.instanceInitializerDescription = instanceInitializerDescription;
    this.lensBuilder = lensBuilder;
    this.mode = mode;

    // Constructors should not be empty and all constructors should have the same prototype unless
    // equivalent.
    assert !instanceInitializers.isEmpty();
    assert instanceInitializers.stream().map(ProgramMethod::getProto).distinct().count() == 1
        || instanceInitializerDescription != null;
  }

  public int getArity() {
    return instanceInitializers.iterator().next().getReference().getArity();
  }

  public List<ProgramMethod> getInstanceInitializers() {
    return instanceInitializers;
  }

  private CfVersion getNewClassFileVersion() {
    CfVersion classFileVersion = null;
    for (ProgramMethod instanceInitializer : instanceInitializers) {
      if (instanceInitializer.getDefinition().hasClassFileVersion()) {
        classFileVersion =
            Ordered.maxIgnoreNull(
                classFileVersion, instanceInitializer.getDefinition().getClassFileVersion());
      }
    }
    return classFileVersion;
  }

  private DexMethod getNewMethodReference(ProgramMethod representative, boolean needsClassId) {
    DexType[] oldParameters = representative.getParameters().values;
    DexType[] newParameters =
        new DexType[representative.getParameters().size() + BooleanUtils.intValue(needsClassId)];
    System.arraycopy(oldParameters, 0, newParameters, 0, oldParameters.length);
    for (int i = 0; i < oldParameters.length; i++) {
      final int parameterIndex = i;
      Set<DexType> parameterTypes =
          SetUtils.newIdentityHashSet(
              builder ->
                  instanceInitializers.forEach(
                      instanceInitializer ->
                          builder.accept(instanceInitializer.getParameter(parameterIndex))));
      if (parameterTypes.size() > 1) {
        newParameters[i] = DexTypeUtils.computeLeastUpperBound(appView, parameterTypes);
      }
    }
    if (needsClassId) {
      assert ArrayUtils.last(newParameters) == null;
      newParameters[newParameters.length - 1] = dexItemFactory.intType;
    }
    return dexItemFactory.createInstanceInitializer(group.getTarget().getType(), newParameters);
  }

  private DexMethod getOriginalMethodReference() {
    return appView.graphLens().getOriginalMethodSignature(getRepresentative().getReference());
  }

  private ProgramMethod getRepresentative() {
    return ListUtils.first(instanceInitializers);
  }

  /**
   * Returns a special original method signature for the synthesized constructor that did not exist
   * prior to horizontal class merging. Otherwise we might accidentally think that the synthesized
   * constructor corresponds to the previous <init>() method on the target class, which could have
   * unintended side-effects such as leading to unused argument removal being applied to the
   * synthesized constructor all-though it by construction doesn't have any unused arguments.
   */
  private DexMethod getSyntheticMethodReference(
      ClassMethodsBuilder classMethodsBuilder, DexMethod newMethodReference) {
    return dexItemFactory.createFreshMethodNameWithoutHolder(
        Constants.SYNTHETIC_INSTANCE_INITIALIZER_PREFIX,
        newMethodReference.getProto(),
        newMethodReference.getHolderType(),
        classMethodsBuilder::isFresh);
  }

  private Int2ReferenceSortedMap<DexMethod> createClassIdToInstanceInitializerMap() {
    assert !hasInstanceInitializerDescription();
    Int2ReferenceSortedMap<DexMethod> typeConstructorClassMap = new Int2ReferenceAVLTreeMap<>();
    for (ProgramMethod instanceInitializer : instanceInitializers) {
      typeConstructorClassMap.put(
          classIdentifiers.getInt(instanceInitializer.getHolderType()),
          lensBuilder.getRenamedMethodSignature(instanceInitializer.getReference()));
    }
    return typeConstructorClassMap;
  }

  public int size() {
    return instanceInitializers.size();
  }

  public static class Builder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final Reference2IntMap<DexType> classIdentifiers;
    private int estimatedDexCodeSize;
    private final List<List<ProgramMethod>> instanceInitializerGroups = new ArrayList<>();
    private final HorizontalClassMergerGraphLens.Builder lensBuilder;
    private final Mode mode;

    public Builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Reference2IntMap<DexType> classIdentifiers,
        HorizontalClassMergerGraphLens.Builder lensBuilder,
        Mode mode) {
      this.appView = appView;
      this.classIdentifiers = classIdentifiers;
      this.lensBuilder = lensBuilder;
      this.mode = mode;
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

    public Builder addEquivalent(ProgramMethod instanceInitializer) {
      ListUtils.last(instanceInitializerGroups).add(instanceInitializer);
      return this;
    }

    public List<InstanceInitializerMerger> build(MergeGroup group) {
      assert instanceInitializerGroups.stream().noneMatch(List::isEmpty);
      return ListUtils.map(
          instanceInitializerGroups,
          instanceInitializers ->
              new InstanceInitializerMerger(
                  appView, classIdentifiers, group, instanceInitializers, lensBuilder, mode));
    }

    public InstanceInitializerMerger buildSingle(
        MergeGroup group, InstanceInitializerDescription instanceInitializerDescription) {
      assert instanceInitializerGroups.stream().noneMatch(List::isEmpty);
      assert instanceInitializerGroups.size() == 1;
      List<ProgramMethod> instanceInitializers = ListUtils.first(instanceInitializerGroups);
      return new InstanceInitializerMerger(
          appView,
          classIdentifiers,
          group,
          instanceInitializers,
          lensBuilder,
          mode,
          instanceInitializerDescription);
    }
  }

  private boolean hasInstanceInitializerDescription() {
    return instanceInitializerDescription != null;
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
        instanceInitializer
            .getDefinition()
            .toTypeSubstitutedMethodAsInlining(method, dexItemFactory);
    encodedMethod.getMutableOptimizationInfo().markForceInline();
    encodedMethod.getAccessFlags().unsetConstructor();
    encodedMethod.getAccessFlags().unsetPublic();
    encodedMethod.getAccessFlags().unsetProtected();
    encodedMethod.getAccessFlags().setPrivate();
    classMethodsBuilder.addDirectMethod(encodedMethod);

    return method;
  }

  private MethodAccessFlags getNewAccessFlags() {
    return MethodAccessFlags.fromSharedAccessFlags(
        Constants.ACC_PUBLIC | Constants.ACC_SYNTHETIC, true);
  }

  private Code getNewCode(
      DexMethod newMethodReference,
      boolean needsClassId,
      int extraNulls) {
    if (hasInstanceInitializerDescription()) {
      return instanceInitializerDescription.createCfCode(
          getOriginalMethodReference(),
          group,
          needsClassId,
          extraNulls);
    }
    assert useSyntheticMethod();
    return new ConstructorEntryPointSynthesizedCode(
        createClassIdToInstanceInitializerMap(),
        newMethodReference,
        group.hasClassIdField() ? group.getClassIdField() : null);
  }

  private boolean isSingleton() {
    return instanceInitializers.size() == 1;
  }

  /** Synthesize a new method which selects the constructor based on a parameter type. */
  @SuppressWarnings("ReferenceEquality")
  void merge(
      ProfileCollectionAdditions profileCollectionAdditions,
      ClassMethodsBuilder classMethodsBuilder,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder) {
    ProgramMethod representative = ListUtils.first(instanceInitializers);

    // Create merged instance initializer reference.
    boolean needsClassId =
        instanceInitializers.size() > 1
            && (!hasInstanceInitializerDescription() || group.hasClassIdField());
    assert mode.isInitial() || !needsClassId;

    DexMethod newMethodReferenceTemplate = getNewMethodReference(representative, needsClassId);
    assert mode.isInitial() || classMethodsBuilder.isFresh(newMethodReferenceTemplate);

    Box<Set<DexType>> usedSyntheticArgumentClasses = new Box<>();
    DexMethod newMethodReference =
        dexItemFactory.createInstanceInitializerWithFreshProto(
            newMethodReferenceTemplate,
            mode.isInitial() ? syntheticArgumentClass.getArgumentClasses() : ImmutableList.of(),
            classMethodsBuilder::isFresh,
            usedSyntheticArgumentClasses::set);

    // Amend the art profile collection.
    if (usedSyntheticArgumentClasses.isSet()) {
      for (ProgramMethod instanceInitializer : instanceInitializers) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            instanceInitializer.getReference(),
            additionsBuilder ->
                usedSyntheticArgumentClasses.get().forEach(additionsBuilder::addRule));
      }
    }

    // Compute the extra unused null parameters.
    List<ExtraUnusedNullParameter> extraUnusedNullParameters =
        ExtraUnusedNullParameter.computeExtraUnusedNullParameters(
            newMethodReferenceTemplate, newMethodReference);

    // Verify that the merge is a simple renaming in the final round of merging.
    assert mode.isInitial() || newMethodReference == newMethodReferenceTemplate;

    // Move instance initializers to target class.
    if (hasInstanceInitializerDescription()) {
      lensBuilder.moveMethods(instanceInitializers, newMethodReference);
    } else if (!useSyntheticMethod()) {
      lensBuilder.moveMethod(representative.getReference(), newMethodReference, true);
    } else {
      for (ProgramMethod instanceInitializer : instanceInitializers) {
        DexMethod movedInstanceInitializer =
            moveInstanceInitializer(classMethodsBuilder, instanceInitializer);
        lensBuilder.mapMethod(movedInstanceInitializer, movedInstanceInitializer);
        lensBuilder.recordNewMethodSignature(
            instanceInitializer.getReference(), movedInstanceInitializer);

        // Amend the art profile collection.
        profileCollectionAdditions.applyIfContextIsInProfile(
            instanceInitializer.getReference(),
            additionsBuilder -> additionsBuilder.addRule(representative));
      }
    }

    // Add a mapping from a synthetic name to the synthetic constructor.
    DexMethod syntheticMethodReference =
        getSyntheticMethodReference(classMethodsBuilder, newMethodReference);

    if (useSyntheticMethod()) {
      lensBuilder.recordNewMethodSignature(syntheticMethodReference, newMethodReference, true);
    }

    // Map each of the instance initializers to the new instance initializer in the graph lens.
    for (ProgramMethod instanceInitializer : instanceInitializers) {
      List<ExtraParameter> extraParameters = new ArrayList<>();
      if (needsClassId) {
        int classIdentifier = classIdentifiers.getInt(instanceInitializer.getHolderType());
        extraParameters.add(new ExtraConstantIntParameter(classIdentifier));
      }
      extraParameters.addAll(extraUnusedNullParameters);
      lensBuilder.mapMergedConstructor(
          instanceInitializer.getReference(), newMethodReference, extraParameters);
    }

    DexEncodedMethod representativeMethod = representative.getDefinition();

    DexEncodedMethod newInstanceInitializer;
    if (!hasInstanceInitializerDescription() && !useSyntheticMethod()) {
      newInstanceInitializer =
          representativeMethod.toTypeSubstitutedMethodAsInlining(
              newMethodReference, dexItemFactory);
    } else {
      newInstanceInitializer =
          DexEncodedMethod.syntheticBuilder()
              .setMethod(newMethodReference)
              .setAccessFlags(getNewAccessFlags())
              .setCode(
                  getNewCode(newMethodReference, needsClassId, extraUnusedNullParameters.size()))
              .setClassFileVersion(getNewClassFileVersion())
              .setApiLevelForDefinition(representativeMethod.getApiLevelForDefinition())
              .setApiLevelForCode(representativeMethod.getApiLevelForCode())
              .build();
    }
    classMethodsBuilder.addDirectMethod(newInstanceInitializer);

    if (mode.isFinal()) {
      if (appView.options().isGeneratingDex() && !newInstanceInitializer.getCode().isDexCode()) {
        syntheticInitializerConverterBuilder.addInstanceInitializer(
            new ProgramMethod(group.getTarget(), newInstanceInitializer));
      } else {
        assert appView.options().isGeneratingDex()
            || newInstanceInitializer.getCode().isCfWritableCode()
            || newInstanceInitializer.getCode().isIncompleteHorizontalClassMergerCode();
      }
    }
  }

  void setObsolete() {
    if (hasInstanceInitializerDescription() || !useSyntheticMethod()) {
      instanceInitializers.forEach(
          instanceInitializer -> instanceInitializer.getDefinition().setObsolete());
    }
  }

  private boolean useSyntheticMethod() {
    return !isSingleton() || group.hasClassIdField();
  }
}
