// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The class merger is responsible for moving methods from {@link ClassMerger#toMergeGroup} into the
 * class {@link ClassMerger#target}. While performing merging, this class tracks which methods have
 * been moved, as well as which fields have been remapped in the {@link ClassMerger#lensBuilder}.
 */
public class ClassMerger {
  public static final String CLASS_ID_FIELD_NAME = "$r8$classId";

  private final DexProgramClass target;
  private final List<DexProgramClass> toMergeGroup;
  private final DexItemFactory dexItemFactory;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final HorizontallyMergedClasses.Builder mergedClassesBuilder;
  private final FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder;

  private final Reference2IntMap<DexType> classIdentifiers = new Reference2IntOpenHashMap<>();
  private final Collection<VirtualMethodMerger> virtualMethodMergers;
  private final Collection<ConstructorMerger> constructorMergers;
  private final DexField classIdField;

  private ClassMerger(
      AppView<AppInfoWithLiveness> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      HorizontallyMergedClasses.Builder mergedClassesBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      DexProgramClass target,
      List<DexProgramClass> toMergeGroup,
      DexField classIdField,
      Collection<VirtualMethodMerger> virtualMethodMergers,
      Collection<ConstructorMerger> constructorMergers) {
    this.lensBuilder = lensBuilder;
    this.mergedClassesBuilder = mergedClassesBuilder;
    this.fieldAccessChangesBuilder = fieldAccessChangesBuilder;
    this.target = target;
    this.toMergeGroup = toMergeGroup;
    this.classIdField = classIdField;
    this.virtualMethodMergers = virtualMethodMergers;
    this.constructorMergers = constructorMergers;

    this.dexItemFactory = appView.dexItemFactory();

    buildClassIdentifierMap();
  }

  /** Returns an iterable over all classes that should be merged into the target class. */
  public Iterable<DexProgramClass> getToMergeClasses() {
    return toMergeGroup;
  }

  /**
   * Returns an iterable over both the target class as well as all classes that should be merged
   * into the target class.
   */
  public Iterable<DexProgramClass> getClasses() {
    return Iterables.concat(Collections.singleton(target), getToMergeClasses());
  }

  void buildClassIdentifierMap() {
    classIdentifiers.put(target.type, 0);
    for (DexProgramClass toMerge : toMergeGroup) {
      classIdentifiers.put(toMerge.type, classIdentifiers.size());
    }
  }

  void merge(DexProgramClass toMerge) {
    if (!toMerge.isFinal()) {
      target.getAccessFlags().demoteFromFinal();
    }

    toMerge.forEachProgramDirectMethod(
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          assert !definition.isClassInitializer();

          if (!definition.isInstanceInitializer()) {
            DexMethod newMethod = renameMethod(method);
            // TODO(b/165000217): Add all methods to `target` in one go using addDirectMethods().
            target.addDirectMethod(definition.toTypeSubstitutedMethod(newMethod));
            lensBuilder.moveMethod(definition.getReference(), newMethod);
          }
        });

    // Clear the members of the class to be merged since they have now been moved to the target.
    toMerge.setVirtualMethods(null);
    toMerge.setDirectMethods(null);
    toMerge.setInstanceFields(null);
    toMerge.setStaticFields(null);
  }

  /**
   * Find a new name for the method.
   *
   * @param method The class the method originally belonged to.
   */
  DexMethod renameMethod(ProgramMethod method) {
    return dexItemFactory.createFreshMethodName(
        method.getDefinition().method.name.toSourceString(),
        method.getHolderType(),
        method.getDefinition().proto(),
        target.type,
        tryMethod -> target.lookupMethod(tryMethod) == null);
  }

  void mergeConstructors(SyntheticArgumentClass syntheticArgumentClass) {
    for (ConstructorMerger merger : constructorMergers) {
      merger.merge(
          lensBuilder, fieldAccessChangesBuilder, classIdentifiers, syntheticArgumentClass);
    }
  }

  void mergeVirtualMethods() {
    for (VirtualMethodMerger merger : virtualMethodMergers) {
      merger.merge(lensBuilder, fieldAccessChangesBuilder, classIdentifiers);
    }
  }

  void appendClassIdField() {
    DexEncodedField encodedField =
        new DexEncodedField(
            classIdField,
            FieldAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC + Constants.ACC_FINAL + Constants.ACC_SYNTHETIC),
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            null);
    target.appendInstanceField(encodedField);
  }

  public void mergeGroup(SyntheticArgumentClass syntheticArgumentClass) {
    appendClassIdField();

    mergedClassesBuilder.addMergeGroup(target, toMergeGroup);
    for (DexProgramClass clazz : toMergeGroup) {
      merge(clazz);
    }

    mergeConstructors(syntheticArgumentClass);
    mergeVirtualMethods();
  }

  public static class Builder {
    private final DexProgramClass target;
    private final List<DexProgramClass> toMergeGroup = new ArrayList<>();
    private final Map<DexProto, ConstructorMerger.Builder> constructorMergerBuilders =
        new LinkedHashMap<>();
    private final Map<Wrapper<DexMethod>, VirtualMethodMerger.Builder> virtualMethodMergerBuilders =
        new LinkedHashMap<>();

    public Builder(DexProgramClass target) {
      this.target = target;
      setupForMethodMerging(target);
    }

    public Builder mergeClass(DexProgramClass toMerge) {
      setupForMethodMerging(toMerge);
      toMergeGroup.add(toMerge);
      return this;
    }

    public Builder addClassesToMerge(List<DexProgramClass> toMerge) {
      toMerge.forEach(this::mergeClass);
      return this;
    }

    void setupForMethodMerging(DexProgramClass toMerge) {
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

    void addConstructor(ProgramMethod method) {
      assert method.getDefinition().isInstanceInitializer();
      constructorMergerBuilders
          .computeIfAbsent(
              method.getDefinition().getProto(), ignore -> new ConstructorMerger.Builder())
          .add(method.getDefinition());
    }

    void addVirtualMethod(ProgramMethod method) {
      assert method.getDefinition().isNonPrivateVirtualMethod();
      virtualMethodMergerBuilders
          .computeIfAbsent(
              MethodSignatureEquivalence.get().wrap(method.getReference()),
              ignore -> new VirtualMethodMerger.Builder())
          .add(method);
    }

    public ClassMerger build(
        AppView<AppInfoWithLiveness> appView,
        HorizontallyMergedClasses.Builder mergedClassesBuilder,
        HorizontalClassMergerGraphLens.Builder lensBuilder,
        FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder) {
      DexItemFactory dexItemFactory = appView.dexItemFactory();
      // TODO(b/165498187): ensure the name for the field is fresh
      DexField classIdField =
          dexItemFactory.createField(target.type, dexItemFactory.intType, CLASS_ID_FIELD_NAME);

      List<VirtualMethodMerger> virtualMethodMergers =
          new ArrayList<>(virtualMethodMergerBuilders.size());
      for (VirtualMethodMerger.Builder builder : virtualMethodMergerBuilders.values()) {
        virtualMethodMergers.add(builder.build(appView, target, classIdField));
      }
      // Try and merge the functions with the most arguments first, to avoid using synthetic
      // arguments if possible.
      virtualMethodMergers.sort(Comparator.comparing(VirtualMethodMerger::getArity).reversed());

      List<ConstructorMerger> constructorMergers =
          new ArrayList<>(constructorMergerBuilders.size());
      for (ConstructorMerger.Builder builder : constructorMergerBuilders.values()) {
        constructorMergers.add(builder.build(appView, target, classIdField));
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
          target,
          toMergeGroup,
          classIdField,
          virtualMethodMergers,
          constructorMergers);
    }
  }
}
