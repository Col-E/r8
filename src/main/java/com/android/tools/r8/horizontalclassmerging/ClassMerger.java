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
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The class merger is responsible for moving methods from {@link ClassMerger#toMergeGroup} into the
 * class {@link ClassMerger#target}. While performing merging, this class tracks which methods have
 * been moved, as well as which fields have been remapped in the {@link ClassMerger#lensBuilder}.
 */
public class ClassMerger {
  public static final String CLASS_ID_FIELD_NAME = "$r8$classId";

  private final AppView<AppInfoWithLiveness> appView;
  private final DexProgramClass target;
  private final Collection<DexProgramClass> toMergeGroup;
  private final DexItemFactory dexItemFactory;
  private final HorizontalClassMergerGraphLens.Builder lensBuilder;
  private final FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder;
  private final Map<DexType, DexType> mergedClasses;

  private final Reference2IntMap<DexType> classIdentifiers = new Reference2IntOpenHashMap<>();
  private final Map<DexProto, ConstructorMerger.Builder> constructorMergers =
      new IdentityHashMap<>();
  private final Map<Wrapper<DexMethod>, VirtualMethodMerger.Builder> virtualMethodMergers =
      new LinkedHashMap<>();
  private final DexField classIdField;

  ClassMerger(
      AppView<AppInfoWithLiveness> appView,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      DexProgramClass target,
      Collection<DexProgramClass> toMergeGroup,
      Map<DexType, DexType> mergedClasses) {
    this.appView = appView;
    this.lensBuilder = lensBuilder;
    this.fieldAccessChangesBuilder = fieldAccessChangesBuilder;
    this.target = target;
    this.toMergeGroup = toMergeGroup;
    this.mergedClasses = mergedClasses;

    this.dexItemFactory = appView.dexItemFactory();

    // TODO(b/165498187): ensure the name for the field is fresh
    classIdField =
        dexItemFactory.createField(target.type, dexItemFactory.intType, CLASS_ID_FIELD_NAME);

    buildClassIdentifierMap();
  }

  Wrapper<DexMethod> bySignature(DexMethod method) {
    return MethodSignatureEquivalence.get().wrap(method);
  }

  void addConstructor(DexEncodedMethod method) {
    assert method.isInstanceInitializer();
    constructorMergers
        .computeIfAbsent(method.proto(), ignore -> new ConstructorMerger.Builder())
        .add(method);
  }

  void addVirtualMethod(ProgramMethod method) {
    assert method.getDefinition().isNonPrivateVirtualMethod();
    virtualMethodMergers
        .computeIfAbsent(
            MethodSignatureEquivalence.get().wrap(method.getReference()),
            ignore -> new VirtualMethodMerger.Builder())
        .add(method);
  }

  void buildClassIdentifierMap() {
    classIdentifiers.put(target.type, 0);
    for (DexProgramClass toMerge : toMergeGroup) {
      classIdentifiers.put(toMerge.type, classIdentifiers.size());
    }
  }

  void merge(DexProgramClass toMerge) {
    toMerge.forEachProgramDirectMethod(
        method -> {
          DexEncodedMethod definition = method.getDefinition();
          assert !definition.isClassInitializer();

          if (definition.isInstanceInitializer()) {
            addConstructor(definition);
          } else {
            // TODO(b/166427795): Ensure that overriding relationships are not changed.
            DexMethod newMethod = renameMethod(method);
            // TODO(b/165000217): Add all methods to `target` in one go using addVirtualMethods().
            target.addDirectMethod(definition.toTypeSubstitutedMethod(newMethod));
            lensBuilder.moveMethod(definition.getReference(), newMethod);
          }
        });

    toMerge.forEachProgramVirtualMethod(this::addVirtualMethod);

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

  void mergeConstructors() {
    for (ConstructorMerger.Builder builder : constructorMergers.values()) {
      ConstructorMerger constructorMerger = builder.build(appView, target, classIdField);
      constructorMerger.merge(lensBuilder, fieldAccessChangesBuilder, classIdentifiers);
    }
  }

  void mergeVirtualMethods() {
    for (VirtualMethodMerger.Builder builder : virtualMethodMergers.values()) {
      VirtualMethodMerger merger = builder.build(appView, target, classIdField, mergedClasses);
      merger.merge(lensBuilder, fieldAccessChangesBuilder, classIdentifiers);
    }
  }

  /**
   * To ensure constructor merging happens correctly, add all of the target constructors methods to
   * constructor mergers.
   */
  void addTargetVirtualMethods() {
    target.forEachProgramVirtualMethod(this::addVirtualMethod);
  }

  /**
   * To ensure constructor merging happens correctly, add all of the target constructors methods to
   * constructor mergers.
   */
  void addTargetConstructors() {
    target.forEachProgramDirectMethod(
        programMethod -> {
          DexEncodedMethod method = programMethod.getDefinition();
          if (method.isInstanceInitializer()) {
            addConstructor(method);
          }
        });
  }

  void appendClassIdField() {
    DexEncodedField encodedField =
        new DexEncodedField(
            classIdField,
            FieldAccessFlags.fromSharedAccessFlags(
                Constants.ACC_PUBLIC + Constants.ACC_FINAL + Constants.ACC_SYNTHETIC),
            DexAnnotationSet.empty(),
            null);
    target.appendInstanceField(encodedField);
  }

  public void mergeGroup() {
    addTargetConstructors();
    addTargetVirtualMethods();

    appendClassIdField();

    for (DexProgramClass clazz : toMergeGroup) {
      merge(clazz);
    }

    mergeConstructors();
    mergeVirtualMethods();
  }
}
