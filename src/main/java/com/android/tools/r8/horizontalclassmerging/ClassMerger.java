// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import static com.google.common.base.Predicates.not;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
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
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
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

  private final MergeGroup group;
  private final DexItemFactory dexItemFactory;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final HorizontallyMergedClasses.Builder mergedClassesBuilder;
  private final FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder;

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
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      MergeGroup group,
      Collection<VirtualMethodMerger> virtualMethodMergers,
      Collection<ConstructorMerger> constructorMergers) {
    this.lensBuilder = lensBuilder;
    this.mergedClassesBuilder = mergedClassesBuilder;
    this.fieldAccessChangesBuilder = fieldAccessChangesBuilder;
    this.group = group;
    this.virtualMethodMergers = virtualMethodMergers;
    this.constructorMergers = constructorMergers;

    this.dexItemFactory = appView.dexItemFactory();
    this.classStaticFieldsMerger = new ClassStaticFieldsMerger(appView, lensBuilder, group);
    this.classInstanceFieldsMerger = new ClassInstanceFieldsMerger(lensBuilder, group);

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
    mergeDirectMethods(group.getTarget());
    group.forEachSource(this::mergeDirectMethods);
    mergeConstructors(syntheticArgumentClass);
  }

  void mergeDirectMethods(DexProgramClass toMerge) {
    toMerge.forEachProgramDirectMethod(
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          assert !definition.isClassInitializer();

          if (!definition.isInstanceInitializer()) {
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
        method.getDefinition().proto(),
        group.getTarget().getType(),
        classMethodsBuilder::isFresh);
  }

  void mergeConstructors(SyntheticArgumentClass syntheticArgumentClass) {
    constructorMergers.forEach(
        merger ->
            merger.merge(
                classMethodsBuilder,
                lensBuilder,
                fieldAccessChangesBuilder,
                classIdentifiers,
                syntheticArgumentClass));
  }

  void mergeVirtualMethods() {
    virtualMethodMergers.forEach(
        merger ->
            merger.merge(
                classMethodsBuilder, lensBuilder, fieldAccessChangesBuilder, classIdentifiers));
    group.forEachSource(clazz -> clazz.getMethodCollection().clearVirtualMethods());
  }

  void appendClassIdField() {
    DexEncodedField encodedField =
        new DexEncodedField(
            group.getClassIdField(),
            FieldAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC + Constants.ACC_FINAL + Constants.ACC_SYNTHETIC),
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            null);
    group.getTarget().appendInstanceField(encodedField);
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
          clazz.setInstanceFields(null);
        });
    classInstanceFieldsMerger.merge();
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
    private final Map<DexProto, ConstructorMerger.Builder> constructorMergerBuilders =
        new LinkedHashMap<>();
    private final Map<Wrapper<DexMethod>, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
        new LinkedHashMap<>();

    public Builder(AppView<AppInfoWithLiveness> appView, MergeGroup group) {
      this.appView = appView;
      this.group = group;
    }

    private Builder setup() {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      DexProgramClass target = group.iterator().next();
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
      toMerge.forEachProgramDirectMethod(
          method -> {
            DexEncodedMethod definition = method.getDefinition();
            assert !definition.isClassInitializer();
            if (definition.isInstanceInitializer()) {
              addConstructor(method);
            }
          });
      toMerge.forEachProgramVirtualMethod(this::addVirtualMethod);
    }

    private void addConstructor(ProgramMethod method) {
      assert method.getDefinition().isInstanceInitializer();
      constructorMergerBuilders
          .computeIfAbsent(
              method.getDefinition().getProto(), ignore -> new ConstructorMerger.Builder(appView))
          .add(method.getDefinition());
    }

    private void addVirtualMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      virtualMethodMergerBuilders
          .computeIfAbsent(
              MethodSignatureEquivalence.get().wrap(method.getReference()),
              ignore -> new VirtualMethodMerger.Builder())
          .add(method);
    }

    public ClassMerger build(
        HorizontallyMergedClasses.Builder mergedClassesBuilder,
        HorizontalClassMergerGraphLens.Builder lensBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder) {
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
      for (ConstructorMerger.Builder builder : constructorMergerBuilders.values()) {
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
          fieldAccessChangesBuilder,
          group,
          virtualMethodMergers,
          constructorMergers);
    }
  }
}
