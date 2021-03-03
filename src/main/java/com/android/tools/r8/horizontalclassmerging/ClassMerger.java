// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.NumberFromIntervalValue;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The class merger is responsible for moving methods from the sources in {@link ClassMerger#group}
 * into the target of {@link ClassMerger#group}. While performing merging, this class tracks which
 * methods have been moved, as well as which fields have been remapped in the {@link
 * ClassMerger#lensBuilder}.
 */
public class ClassMerger {

  public static final String CLASS_ID_FIELD_NAME = "$r8$classId";

  private static final OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();

  private final AppView<AppInfoWithLiveness> appView;
  private final MergeGroup group;
  private final DexItemFactory dexItemFactory;
  private final ClassInitializerSynthesizedCode classInitializerSynthesizedCode;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final HorizontallyMergedClasses.Builder mergedClassesBuilder;

  private final ClassMethodsBuilder classMethodsBuilder = new ClassMethodsBuilder();
  private final Reference2IntMap<DexType> classIdentifiers = new Reference2IntOpenHashMap<>();
  private final ClassStaticFieldsMerger classStaticFieldsMerger;
  private final ClassInstanceFieldsMerger classInstanceFieldsMerger;
  private final Collection<VirtualMethodMerger> virtualMethodMergers;
  private final Collection<ConstructorMerger> constructorMergers;

  private ClassMerger(
      AppView<AppInfoWithLiveness> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      HorizontallyMergedClasses.Builder mergedClassesBuilder,
      MergeGroup group,
      Collection<VirtualMethodMerger> virtualMethodMergers,
      Collection<ConstructorMerger> constructorMergers,
      ClassInitializerSynthesizedCode classInitializerSynthesizedCode) {
    this.appView = appView;
    this.lensBuilder = lensBuilder;
    this.mergedClassesBuilder = mergedClassesBuilder;
    this.group = group;
    this.virtualMethodMergers = virtualMethodMergers;
    this.constructorMergers = constructorMergers;

    this.dexItemFactory = appView.dexItemFactory();
    this.classInitializerSynthesizedCode = classInitializerSynthesizedCode;
    this.classStaticFieldsMerger = new ClassStaticFieldsMerger(appView, lensBuilder, group);
    this.classInstanceFieldsMerger = new ClassInstanceFieldsMerger(appView, lensBuilder, group);

    buildClassIdentifierMap();
  }

  MergeGroup getGroup() {
    return group;
  }

  void buildClassIdentifierMap() {
    classIdentifiers.put(group.getTarget().getType(), 0);
    group.forEachSource(clazz -> classIdentifiers.put(clazz.getType(), classIdentifiers.size()));
  }

  void mergeDirectMethods(SyntheticArgumentClass syntheticArgumentClass) {
    mergeStaticClassInitializers();
    mergeDirectMethods(group.getTarget());
    group.forEachSource(this::mergeDirectMethods);
    mergeConstructors(syntheticArgumentClass);
  }

  void mergeStaticClassInitializers() {
    if (classInitializerSynthesizedCode.isEmpty()) {
      return;
    }

    DexMethod newClinit = dexItemFactory.createClassInitializer(group.getTarget().getType());

    CfCode code = classInitializerSynthesizedCode.synthesizeCode(group.getTarget().getType());
    if (!group.getTarget().hasClassInitializer()) {
      classMethodsBuilder.addDirectMethod(
          new DexEncodedMethod(
              newClinit,
              MethodAccessFlags.fromSharedAccessFlags(
                  Constants.ACC_SYNTHETIC | Constants.ACC_STATIC, true),
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              code,
              true,
              classInitializerSynthesizedCode.getCfVersion()));
    } else {
      DexEncodedMethod clinit = group.getTarget().getClassInitializer();
      clinit.setCode(code, appView);
      CfVersion cfVersion = classInitializerSynthesizedCode.getCfVersion();
      if (cfVersion != null) {
        clinit.upgradeClassFileVersion(cfVersion);
      } else {
        assert appView.options().isGeneratingDex();
      }
      classMethodsBuilder.addDirectMethod(clinit);
    }
  }

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
            classMethodsBuilder.addDirectMethod(definition.toTypeSubstitutedMethod(newMethod));
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
    return dexItemFactory.createFreshMethodName(
        method.getDefinition().method.name.toSourceString(),
        method.getHolderType(),
        method.getDefinition().getProto(),
        group.getTarget().getType(),
        classMethodsBuilder::isFresh);
  }

  void mergeConstructors(SyntheticArgumentClass syntheticArgumentClass) {
    constructorMergers.forEach(
        merger ->
            merger.merge(
                classMethodsBuilder,
                lensBuilder,
                classIdentifiers,
                syntheticArgumentClass));
  }

  void mergeVirtualMethods() {
    virtualMethodMergers.forEach(
        merger -> merger.merge(classMethodsBuilder, lensBuilder, classIdentifiers));
    group.forEachSource(clazz -> clazz.getMethodCollection().clearVirtualMethods());
  }

  void appendClassIdField() {
    boolean deprecated = false;
    boolean d8R8Synthesized = true;
    DexEncodedField classIdField =
        new DexEncodedField(
            group.getClassIdField(),
            FieldAccessFlags.createPublicFinalSynthetic(),
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            null,
            deprecated,
            d8R8Synthesized);

    // For the $r8$classId synthesized fields, we try to over-approximate the set of values it may
    // have. For example, for a merge group of size 4, we may compute the set {0, 2, 3}, if the
    // instances with $r8$classId == 1 ends up dead as a result of optimizations). If no instances
    // end up being dead, we would compute the set {0, 1, 2, 3}. The latter information does not
    // provide any value, and therefore we should not save it in the optimization info. In order to
    // be able to recognize that {0, 1, 2, 3} is useless, we record that the value of the field is
    // known to be in [0; 3] here.
    NumberFromIntervalValue abstractValue = new NumberFromIntervalValue(0, group.size() - 1);
    feedback.recordFieldHasAbstractValue(classIdField, appView, abstractValue);

    classInstanceFieldsMerger.setClassIdField(classIdField);
  }

  void mergeStaticFields() {
    group.forEachSource(classStaticFieldsMerger::addFields);
    classStaticFieldsMerger.merge(group.getTarget());
    group.forEachSource(clazz -> clazz.setStaticFields(null));
  }

  void fixAccessFlags() {
    if (Iterables.any(group.getSources(), not(DexProgramClass::isAbstract))) {
      group.getTarget().getAccessFlags().demoteFromAbstract();
    }
    if (Iterables.any(group.getSources(), not(DexProgramClass::isFinal))) {
      group.getTarget().getAccessFlags().demoteFromFinal();
    }
  }

  private void mergeInterfaces() {
    DexTypeList previousInterfaces = group.getTarget().getInterfaces();
    Set<DexType> interfaces = Sets.newLinkedHashSet(previousInterfaces);
    group.forEachSource(clazz -> Iterables.addAll(interfaces, clazz.getInterfaces()));
    if (interfaces.size() > previousInterfaces.size()) {
      group.getTarget().setInterfaces(new DexTypeList(interfaces));
    }
  }

  void mergeInstanceFields() {
    group.forEachSource(
        clazz -> {
          classInstanceFieldsMerger.addFields(clazz);
          clazz.clearInstanceFields();
        });
    group.getTarget().setInstanceFields(classInstanceFieldsMerger.merge());
  }

  public void mergeGroup(SyntheticArgumentClass syntheticArgumentClass) {
    fixAccessFlags();
    appendClassIdField();

    mergeInterfaces();

    mergeVirtualMethods();
    mergeDirectMethods(syntheticArgumentClass);
    classMethodsBuilder.setClassMethods(group.getTarget());

    mergeStaticFields();
    mergeInstanceFields();

    mergedClassesBuilder.addMergeGroup(group);
  }

  public static class Builder {
    private final AppView<AppInfoWithLiveness> appView;
    private final MergeGroup group;
    private final ClassInitializerSynthesizedCode.Builder classInitializerSynthesizedCodeBuilder =
        new ClassInitializerSynthesizedCode.Builder();
    private final Map<DexProto, ConstructorMerger.Builder> constructorMergerBuilders =
        new LinkedHashMap<>();
    private final List<ConstructorMerger.Builder> unmergedConstructorBuilders = new ArrayList<>();
    private final Map<Wrapper<DexMethod>, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
        new LinkedHashMap<>();

    public Builder(AppView<AppInfoWithLiveness> appView, MergeGroup group) {
      this.appView = appView;
      this.group = group;
    }

    private Builder setup() {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexProgramClass target =
          IterableUtils.findOrDefault(group, DexClass::isPublic, group.iterator().next());
      // TODO(b/165498187): ensure the name for the field is fresh
      group.setClassIdField(
          dexItemFactory.createField(
              target.getType(), dexItemFactory.intType, CLASS_ID_FIELD_NAME));
      group.setTarget(target);
      setupForMethodMerging(target);
      group.forEachSource(this::setupForMethodMerging);
      return this;
    }

    private void setupForMethodMerging(DexProgramClass toMerge) {
      if (toMerge.hasClassInitializer()) {
        classInitializerSynthesizedCodeBuilder.add(toMerge.getClassInitializer());
      }
      toMerge.forEachProgramDirectMethodMatching(
          DexEncodedMethod::isInstanceInitializer, this::addConstructor);
      toMerge.forEachProgramVirtualMethod(this::addVirtualMethod);
    }

    private void addConstructor(ProgramMethod method) {
      assert method.getDefinition().isInstanceInitializer();
      if (appView.options().horizontalClassMergerOptions().isConstructorMergingEnabled()) {
        constructorMergerBuilders
            .computeIfAbsent(
                method.getDefinition().getProto(), ignore -> new ConstructorMerger.Builder(appView))
            .add(method.getDefinition());
      } else {
        unmergedConstructorBuilders.add(
            new ConstructorMerger.Builder(appView).add(method.getDefinition()));
      }
    }

    private void addVirtualMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      virtualMethodMergerBuilders
          .computeIfAbsent(
              MethodSignatureEquivalence.get().wrap(method.getReference()),
              ignore -> new VirtualMethodMerger.Builder())
          .add(method);
    }

    private Collection<ConstructorMerger.Builder> getConstructorMergerBuilders() {
      return appView.options().horizontalClassMergerOptions().isConstructorMergingEnabled()
          ? constructorMergerBuilders.values()
          : unmergedConstructorBuilders;
    }

    public ClassMerger build(
        HorizontallyMergedClasses.Builder mergedClassesBuilder,
        HorizontalClassMergerGraphLens.Builder lensBuilder) {
      setup();
      List<VirtualMethodMerger> virtualMethodMergers =
          new ArrayList<>(virtualMethodMergerBuilders.size());
      for (VirtualMethodMerger.Builder builder : virtualMethodMergerBuilders.values()) {
        virtualMethodMergers.add(builder.build(appView, group));
      }
      // Try and merge the functions with the most arguments first, to avoid using synthetic
      // arguments if possible.
      virtualMethodMergers.sort(Comparator.comparing(VirtualMethodMerger::getArity).reversed());

      List<ConstructorMerger> constructorMergers =
          new ArrayList<>(constructorMergerBuilders.size());
      for (ConstructorMerger.Builder builder : getConstructorMergerBuilders()) {
        constructorMergers.addAll(builder.build(appView, group));
      }

      // Try and merge the functions with the most arguments first, to avoid using synthetic
      // arguments if possible.
      virtualMethodMergers.sort(Comparator.comparing(VirtualMethodMerger::getArity).reversed());
      constructorMergers.sort(Comparator.comparing(ConstructorMerger::getArity).reversed());

      return new ClassMerger(
          appView,
          lensBuilder,
          mergedClassesBuilder,
          group,
          virtualMethodMergers,
          constructorMergers,
          classInitializerSynthesizedCodeBuilder.build());
    }
  }
}
