// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.code.ClassInitializerMerger;
import com.android.tools.r8.horizontalclassmerging.code.SyntheticInitializerConverter;
import com.android.tools.r8.ir.analysis.value.NumberFromIntervalValue;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The class merger is responsible for moving methods from the sources in {@link ClassMerger#group}
 * into the target of {@link ClassMerger#group}. While performing merging, this class tracks which
 * methods have been moved, as well as which fields have been remapped in the {@link
 * ClassMerger#lensBuilder}.
 */
public class ClassMerger {

  public static final String CLASS_ID_FIELD_PREFIX = "$r8$classId";

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  private final AppView<?> appView;
  private final Mode mode;
  private final MergeGroup group;
  private final DexItemFactory dexItemFactory;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;

  private final ClassMethodsBuilder classMethodsBuilder = new ClassMethodsBuilder();
  private final Reference2IntMap<DexType> classIdentifiers = new Reference2IntOpenHashMap<>();

  // Field mergers.
  private final ClassInstanceFieldsMerger classInstanceFieldsMerger;
  private final ClassStaticFieldsMerger classStaticFieldsMerger;

  // Method mergers.
  private final ClassInitializerMerger classInitializerMerger;
  private final InstanceInitializerMergerCollection instanceInitializerMergers;
  private final Collection<VirtualMethodMerger> virtualMethodMergers;

  private ClassMerger(
      AppView<?> appView,
      IRCodeProvider codeProvider,
      Mode mode,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      MergeGroup group,
      Collection<VirtualMethodMerger> virtualMethodMergers) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.group = group;
    this.lensBuilder = lensBuilder;
    this.mode = mode;

    // Field mergers.
    this.classStaticFieldsMerger = new ClassStaticFieldsMerger(appView, lensBuilder, group);
    this.classInstanceFieldsMerger = ClassInstanceFieldsMerger.create(appView, lensBuilder, group);

    // Method mergers.
    this.classInitializerMerger = ClassInitializerMerger.create(group);
    this.instanceInitializerMergers =
        InstanceInitializerMergerCollection.create(
            appView, classIdentifiers, codeProvider, group, lensBuilder, mode);
    this.virtualMethodMergers = virtualMethodMergers;

    buildClassIdentifierMap();
  }

  void buildClassIdentifierMap() {
    classIdentifiers.put(group.getTarget().getType(), 0);
    group.forEachSource(clazz -> classIdentifiers.put(clazz.getType(), classIdentifiers.size()));
  }

  void mergeDirectMethods(
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder) {
    mergeInstanceInitializers(
        profileCollectionAdditions, syntheticArgumentClass, syntheticInitializerConverterBuilder);
    mergeStaticClassInitializers(syntheticInitializerConverterBuilder);
    group.forEach(this::mergeDirectMethods);
    if (!classInitializerMerger.isEmpty() && classInitializerMerger.isTrivialMerge()) {
      classInitializerMerger.setObsolete();
    }
    instanceInitializerMergers.setObsolete();
  }

  void mergeStaticClassInitializers(
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder) {
    if (classInitializerMerger.isEmpty()) {
      return;
    }

    // Synthesize a new class initializer with a fresh synthetic original name.
    DexMethod newMethodReference =
        dexItemFactory.createClassInitializer(group.getTarget().getType());
    DexMethod syntheticMethodReference =
        newMethodReference.withName("$r8$clinit$synthetic", dexItemFactory);
    lensBuilder.recordNewMethodSignature(syntheticMethodReference, newMethodReference, true);

    ComputedApiLevel apiReferenceLevel = classInitializerMerger.getApiReferenceLevel(appView);
    DexEncodedMethod definition =
        DexEncodedMethod.syntheticBuilder()
            .setMethod(newMethodReference)
            .setAccessFlags(MethodAccessFlags.createForClassInitializer())
            .setCode(classInitializerMerger.getCode(syntheticMethodReference))
            .setClassFileVersion(classInitializerMerger.getCfVersion())
            .setApiLevelForDefinition(apiReferenceLevel)
            .setApiLevelForCode(apiReferenceLevel)
            .build();
    classMethodsBuilder.addDirectMethod(definition);

    // In case we didn't synthesize CF code, we register the class initializer for conversion to dex
    // after merging.
    if (!definition.getCode().isCfCode()) {
      assert appView.options().isGeneratingDex();
      assert mode.isFinal();
      syntheticInitializerConverterBuilder.addClassInitializer(
          new ProgramMethod(group.getTarget(), definition));
    }
  }

  @SuppressWarnings("ReferenceEquality")
  void mergeDirectMethods(DexProgramClass toMerge) {
    toMerge.forEachProgramDirectMethod(
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          if (definition.isClassInitializer()) {
            lensBuilder.moveMethod(
                method.getReference(),
                dexItemFactory.createClassInitializer(group.getTarget().getType()));
          } else if (!definition.isInstanceInitializer()) {
            DexMethod newMethod =
                method.getReference().withHolder(group.getTarget().getType(), dexItemFactory);
            if (!classMethodsBuilder.isFresh(newMethod)) {
              newMethod = renameDirectMethod(method);
            }
            classMethodsBuilder.addDirectMethod(
                newMethod != method.getReference()
                    ? definition.toTypeSubstitutedMethod(newMethod)
                    : method.getDefinition());
            if (definition.getReference() != newMethod) {
              lensBuilder.moveMethod(definition.getReference(), newMethod);
            }
          }
        });
    // Clear the members of the class to be merged since they have now been moved to the target.
    toMerge.getMethodCollection().clearDirectMethods();
  }

  /**
   * Find a new name for the method.
   *
   * @param method The class the method originally belonged to.
   */
  DexMethod renameDirectMethod(ProgramMethod method) {
    assert method.getDefinition().belongsToDirectPool();
    return dexItemFactory.createFreshMethodNameWithoutHolder(
        method.getName().toSourceString(),
        method.getProto(),
        group.getTarget().getType(),
        classMethodsBuilder::isFresh);
  }

  void mergeInstanceInitializers(
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder) {
    instanceInitializerMergers.forEach(
        merger ->
            merger.merge(
                profileCollectionAdditions,
                classMethodsBuilder,
                syntheticArgumentClass,
                syntheticInitializerConverterBuilder));
  }

  void mergeMethods(
      ProfileCollectionAdditions profileCollectionAdditions,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    mergeVirtualMethods(profileCollectionAdditions, virtuallyMergedMethodsKeepInfoConsumer);
    mergeDirectMethods(
        profileCollectionAdditions, syntheticArgumentClass, syntheticInitializerConverterBuilder);
    classMethodsBuilder.setClassMethods(group.getTarget());
  }

  void mergeVirtualMethods(
      ProfileCollectionAdditions profileCollectionAdditions,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    virtualMethodMergers.forEach(
        merger ->
            merger.merge(
                profileCollectionAdditions,
                classMethodsBuilder,
                lensBuilder,
                classIdentifiers,
                virtuallyMergedMethodsKeepInfoConsumer));
    group.forEachSource(clazz -> clazz.getMethodCollection().clearVirtualMethods());
  }

  void appendClassIdField() {
    assert appView.hasLiveness();
    assert mode.isInitial();

    DexEncodedField classIdField =
        DexEncodedField.syntheticBuilder()
            .setField(group.getClassIdField())
            .setAccessFlags(FieldAccessFlags.createPublicFinalSynthetic())
            .setApiLevel(appView.computedMinApiLevel())
            .disableAndroidApiLevelCheckIf(
                !appView.options().apiModelingOptions().isApiCallerIdentificationEnabled())
            .build();

    // For the $r8$classId synthesized fields, we try to over-approximate the set of values it may
    // have. For example, for a merge group of size 4, we may compute the set {0, 2, 3}, if the
    // instances with $r8$classId == 1 ends up dead as a result of optimizations). If no instances
    // end up being dead, we would compute the set {0, 1, 2, 3}. The latter information does not
    // provide any value, and therefore we should not save it in the optimization info. In order to
    // be able to recognize that {0, 1, 2, 3} is useless, we record that the value of the field is
    // known to be in [0; 3] here.
    NumberFromIntervalValue abstractValue = new NumberFromIntervalValue(0, group.size() - 1);
    feedback.recordFieldHasAbstractValue(classIdField, appView.withLiveness(), abstractValue);

    classInstanceFieldsMerger.setClassIdField(classIdField);
  }

  void fixAccessFlags() {
    if (Iterables.any(group.getSources(), not(DexProgramClass::isAbstract))) {
      group.getTarget().getAccessFlags().demoteFromAbstract();
    }
    if (Iterables.any(group.getSources(), not(DexProgramClass::isFinal))) {
      group.getTarget().getAccessFlags().demoteFromFinal();
    }
  }

  @SuppressWarnings("ReferenceEquality")
  void fixNestMemberAttributes() {
    if (group.getTarget().isInANest() && !group.getTarget().hasNestMemberAttributes()) {
      for (DexProgramClass clazz : group.getSources()) {
        if (clazz.hasNestMemberAttributes()) {
          // The nest host has been merged into a nest member.
          group.getTarget().clearNestHost();
          group.getTarget().setNestMemberAttributes(clazz.getNestMembersClassAttributes());
          group
              .getTarget()
              .removeNestMemberAttributes(
                  nestMemberAttribute ->
                      nestMemberAttribute.getNestMember() == group.getTarget().getType());
          break;
        }
      }
    }
  }

  private void mergeAnnotations() {
    assert group.getClasses().stream().filter(DexDefinition::hasAnnotations).count() <= 1;
    for (DexProgramClass clazz : group.getSources()) {
      if (clazz.hasAnnotations()) {
        group.getTarget().setAnnotations(clazz.annotations());
        break;
      }
    }
  }

  private void mergeInterfaces() {
    Set<DexType> interfaces = Sets.newLinkedHashSet();
    if (group.isInterfaceGroup()) {
      // Add all implemented interfaces from the merge group to the target class, ignoring
      // implemented interfaces that are part of the merge group.
      Set<DexType> groupTypes =
          SetUtils.newImmutableSet(
              builder -> group.forEach(clazz -> builder.accept(clazz.getType())));
      group.forEach(
          clazz -> {
            for (DexType itf : clazz.getInterfaces()) {
              if (!groupTypes.contains(itf)) {
                interfaces.add(itf);
              }
            }
          });
    } else {
      // Add all implemented interfaces from the merge group to the target class.
      group.forEach(clazz -> Iterables.addAll(interfaces, clazz.getInterfaces()));
    }
    group.getTarget().setInterfaces(DexTypeList.create(interfaces));
  }

  void mergeFields(PrunedItems.Builder prunedItemsBuilder) {
    if (group.hasClassIdField()) {
      appendClassIdField();
    }
    mergeInstanceFields(prunedItemsBuilder);
    mergeStaticFields();
  }

  void mergeInstanceFields(PrunedItems.Builder prunedItemsBuilder) {
    group.forEachSource(
        clazz -> {
          clazz.forEachInstanceField(
              field -> prunedItemsBuilder.addRemovedField(field.getReference()));
          clazz.clearInstanceFields();
        });
    group.getTarget().setInstanceFields(classInstanceFieldsMerger.merge());
  }

  void mergeStaticFields() {
    group.forEachSource(classStaticFieldsMerger::addFields);
    classStaticFieldsMerger.merge();
    group.forEachSource(DexClass::clearStaticFields);
  }

  public void mergeGroup(
      ProfileCollectionAdditions profileCollectionAdditions,
      PrunedItems.Builder prunedItemsBuilder,
      SyntheticArgumentClass syntheticArgumentClass,
      SyntheticInitializerConverter.Builder syntheticInitializerConverterBuilder,
      Consumer<VirtuallyMergedMethodsKeepInfo> virtuallyMergedMethodsKeepInfoConsumer) {
    fixAccessFlags();
    fixNestMemberAttributes();
    mergeAnnotations();
    mergeInterfaces();
    mergeFields(prunedItemsBuilder);
    mergeMethods(
        profileCollectionAdditions,
        syntheticArgumentClass,
        syntheticInitializerConverterBuilder,
        virtuallyMergedMethodsKeepInfoConsumer);
    group.getTarget().clearClassSignature();
    group.getTarget().forEachProgramMember(ProgramMember::clearGenericSignature);
    group.forEachSource(clazz -> prunedItemsBuilder.addRemovedClass(clazz.getType()));
  }

  public static class Builder {
    private final AppView<?> appView;
    private final IRCodeProvider codeProvider;
    private final Mode mode;
    private final MergeGroup group;

    public Builder(AppView<?> appView, IRCodeProvider codeProvider, MergeGroup group, Mode mode) {
      this.appView = appView;
      this.codeProvider = codeProvider;
      this.group = group;
      this.mode = mode;
    }

    private List<VirtualMethodMerger> createVirtualMethodMergers() {
      if (!appView.hasClassHierarchy()) {
        assert getVirtualMethodMergerBuilders().isEmpty();
        return Collections.emptyList();
      }
      Map<DexMethodSignature, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
          getVirtualMethodMergerBuilders();
      if (virtualMethodMergerBuilders.isEmpty()) {
        return Collections.emptyList();
      }
      List<VirtualMethodMerger> virtualMethodMergers =
          new ArrayList<>(virtualMethodMergerBuilders.size());
      List<VirtualMethodMerger> nonNopOrTrivialVirtualMethodMergers =
          new ArrayList<>(virtualMethodMergerBuilders.size());
      for (VirtualMethodMerger.Builder builder : virtualMethodMergerBuilders.values()) {
        VirtualMethodMerger virtualMethodMerger =
            builder.build(appView.withClassHierarchy(), group);
        if (virtualMethodMerger.isNopOrTrivial()) {
          virtualMethodMergers.add(virtualMethodMerger);
        } else {
          nonNopOrTrivialVirtualMethodMergers.add(virtualMethodMerger);
        }
      }
      virtualMethodMergers.addAll(nonNopOrTrivialVirtualMethodMergers);
      // The nopOrTrivial mergers do not deal with signature conflict resolution, we process them
      // first, then the non trivial mergers are processed and correctly deal with conflicting
      // signatures.
      assert verifyNopOrTrivialAreFirst(virtualMethodMergers);
      return virtualMethodMergers;
    }

    private boolean verifyNopOrTrivialAreFirst(List<VirtualMethodMerger> virtualMethodMergers) {
      boolean metNonNopOrTrivial = false;
      for (VirtualMethodMerger virtualMethodMerger : virtualMethodMergers) {
        if (metNonNopOrTrivial) {
          assert !virtualMethodMerger.isNopOrTrivial();
        } else {
          if (!virtualMethodMerger.isNopOrTrivial()) {
            metNonNopOrTrivial = true;
          }
        }
      }
      return true;
    }

    private Map<DexMethodSignature, VirtualMethodMerger.Builder> getVirtualMethodMergerBuilders() {
      Map<DexMethodSignature, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
          new LinkedHashMap<>();
      group.forEach(
          clazz ->
              clazz.forEachProgramVirtualMethod(
                  virtualMethod ->
                      virtualMethodMergerBuilders
                          .computeIfAbsent(
                              virtualMethod.getReference().getSignature(),
                              ignore -> new VirtualMethodMerger.Builder())
                          .add(virtualMethod)));
      return virtualMethodMergerBuilders;
    }

    private void createClassIdField() {
      DexField field =
          appView
              .dexItemFactory()
              .createFreshFieldNameWithoutHolder(
                  group.getTarget().getType(),
                  appView.dexItemFactory().intType,
                  CLASS_ID_FIELD_PREFIX,
                  f ->
                      Iterables.all(
                          group.getClasses(),
                          clazz -> clazz.getFieldCollection().lookupField(f) == null));
      group.setClassIdField(field);
    }

    public ClassMerger build(
        HorizontalClassMergerGraphLens.Builder lensBuilder) {
      List<VirtualMethodMerger> virtualMethodMergers = createVirtualMethodMergers();

      boolean requiresClassIdField =
          virtualMethodMergers.stream()
              .anyMatch(virtualMethodMerger -> !virtualMethodMerger.isNopOrTrivial());
      if (requiresClassIdField) {
        assert mode.isInitial();
        createClassIdField();
      }

      return new ClassMerger(appView, codeProvider, mode, lensBuilder, group, virtualMethodMergers);
    }
  }
}
