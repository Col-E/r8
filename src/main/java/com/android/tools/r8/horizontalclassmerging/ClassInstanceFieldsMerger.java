// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeUtils;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMergerGraphLens.Builder;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields.InstanceFieldInfo;
import com.android.tools.r8.utils.IterableUtils;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public interface ClassInstanceFieldsMerger {

  void setClassIdField(DexEncodedField classIdField);

  DexEncodedField[] merge();

  static ClassInstanceFieldsMerger create(
      AppView<?> appView, HorizontalClassMergerGraphLens.Builder lensBuilder, MergeGroup group) {
    if (appView.hasClassHierarchy()) {
      return new ClassInstanceFieldsMergerImpl(appView.withClassHierarchy(), lensBuilder, group);
    } else {
      assert group.getInstanceFieldMap().isEmpty();
      assert appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics();
      return new ClassInstanceFieldsMerger() {
        @Override
        public void setClassIdField(DexEncodedField classIdField) {
          throw new UnsupportedOperationException("No instance field merging in D8");
        }

        @Override
        public DexEncodedField[] merge() {
          return DexEncodedField.EMPTY_ARRAY;
        }
      };
    }
  }

  /**
   * Adds all fields from {@param clazz} to the class merger. For each field, we must choose which
   * field on the target class to merge into.
   *
   * <p>A field that stores a reference type can be merged into a field that stores a different
   * reference type. To avoid that we change fields that store a reference type to have type
   * java.lang.Object when it is not needed (e.g., class Foo has fields 'A a' and 'B b' and class
   * Bar has fields 'A b' and 'B a'), we make a prepass that matches fields with the same reference
   * type.
   */
  static void mapFields(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      DexProgramClass source,
      DexProgramClass target,
      BiConsumer<DexEncodedField, DexEncodedField> consumer) {
    Map<InstanceFieldInfo, LinkedList<DexEncodedField>> availableFieldsByExactInfo =
        getAvailableFieldsByExactInfo(target);
    List<DexEncodedField> needsMerge = new ArrayList<>();

    // Pass 1: Match fields that have the exact same type.
    for (DexEncodedField oldField : source.instanceFields()) {
      InstanceFieldInfo info = InstanceFieldInfo.createExact(oldField);
      LinkedList<DexEncodedField> availableFieldsWithExactSameInfo =
          availableFieldsByExactInfo.get(info);
      if (availableFieldsWithExactSameInfo == null || availableFieldsWithExactSameInfo.isEmpty()) {
        needsMerge.add(oldField);
      } else {
        DexEncodedField newField = availableFieldsWithExactSameInfo.removeFirst();
        consumer.accept(oldField, newField);
        if (availableFieldsWithExactSameInfo.isEmpty()) {
          availableFieldsByExactInfo.remove(info);
        }
      }
    }

    // Pass 2: Match fields that do not have the same reference type.
    Map<InstanceFieldInfo, LinkedList<DexEncodedField>> availableFieldsByRelaxedInfo =
        getAvailableFieldsByRelaxedInfo(appView, availableFieldsByExactInfo);
    for (DexEncodedField oldField : needsMerge) {
      assert oldField.getType().isReferenceType();
      DexEncodedField newField =
          availableFieldsByRelaxedInfo
              .get(InstanceFieldInfo.createRelaxed(oldField, appView.dexItemFactory()))
              .removeFirst();
      assert newField != null;
      assert newField.getType().isReferenceType();
      consumer.accept(oldField, newField);
    }
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  static Map<InstanceFieldInfo, LinkedList<DexEncodedField>> getAvailableFieldsByExactInfo(
      DexProgramClass target) {
    Map<InstanceFieldInfo, LinkedList<DexEncodedField>> availableFieldsByInfo =
        new LinkedHashMap<>();
    for (DexEncodedField field : target.instanceFields()) {
      availableFieldsByInfo
          .computeIfAbsent(InstanceFieldInfo.createExact(field), ignore -> new LinkedList<>())
          .add(field);
    }
    return availableFieldsByInfo;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  static Map<InstanceFieldInfo, LinkedList<DexEncodedField>> getAvailableFieldsByRelaxedInfo(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Map<InstanceFieldInfo, LinkedList<DexEncodedField>> availableFieldsByExactInfo) {
    Map<InstanceFieldInfo, LinkedList<DexEncodedField>> availableFieldsByRelaxedInfo =
        new LinkedHashMap<>();
    availableFieldsByExactInfo.forEach(
        (info, fields) ->
            availableFieldsByRelaxedInfo
                .computeIfAbsent(
                    info.toInfoWithRelaxedType(appView.dexItemFactory()),
                    ignore -> new LinkedList<>())
                .addAll(fields));
    return availableFieldsByRelaxedInfo;
  }

  class ClassInstanceFieldsMergerImpl implements ClassInstanceFieldsMerger {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private final MergeGroup group;
    private final Builder lensBuilder;

    private DexEncodedField classIdField;

    private final Set<DexField> committedFields = Sets.newIdentityHashSet();

    private ClassInstanceFieldsMergerImpl(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        HorizontalClassMergerGraphLens.Builder lensBuilder,
        MergeGroup group) {
      this.appView = appView;
      this.group = group;
      this.lensBuilder = lensBuilder;
    }

    @Override
    public void setClassIdField(DexEncodedField classIdField) {
      this.classIdField = classIdField;
    }

    @Override
    public DexEncodedField[] merge() {
      assert group.hasInstanceFieldMap();
      List<DexEncodedField> newFields = new ArrayList<>();
      if (classIdField != null) {
        newFields.add(classIdField);
        committedFields.add(classIdField.getReference());
      }
      group
          .getInstanceFieldMap()
          .forEachManyToOneMapping(
              (sourceFields, targetField) -> {
                DexEncodedField newField =
                    mergeSourceFieldsToTargetField(targetField, sourceFields);
                newFields.add(newField);
                committedFields.add(newField.getReference());
              });
      return newFields.toArray(DexEncodedField.EMPTY_ARRAY);
    }

    private DexEncodedField mergeSourceFieldsToTargetField(
        DexEncodedField targetField, Set<DexEncodedField> sourceFields) {
      fixAccessFlags(targetField, sourceFields);

      DexEncodedField newField;
      if (needsRelaxedType(targetField, sourceFields)) {
        DexType newFieldType =
            DexTypeUtils.computeLeastUpperBound(
                appView,
                Iterables.transform(
                    Iterables.concat(IterableUtils.singleton(targetField), sourceFields),
                    DexEncodedField::getType));
        newField =
            targetField.toTypeSubstitutedField(
                appView,
                targetField.getReference().withType(newFieldType, appView.dexItemFactory()));
      } else {
        newField = targetField;
      }

      if (committedFields.contains(newField.getReference())) {
        newField =
            targetField.toTypeSubstitutedField(
                appView,
                appView
                    .dexItemFactory()
                    .createFreshFieldNameWithoutHolder(
                        newField.getHolderType(),
                        newField.getType(),
                        newField.getName().toString(),
                        Predicate.not(committedFields::contains)));
      }

      lensBuilder.recordNewFieldSignature(
          Iterables.transform(
              IterableUtils.append(sourceFields, targetField), DexEncodedField::getReference),
          newField.getReference(),
          targetField.getReference());

      return newField;
    }

    private void fixAccessFlags(DexEncodedField newField, Collection<DexEncodedField> oldFields) {
      if (newField.isSynthetic() && Iterables.any(oldFields, oldField -> !oldField.isSynthetic())) {
        newField.getAccessFlags().demoteFromSynthetic();
      }
      if (newField.isFinal() && Iterables.any(oldFields, oldField -> !oldField.isFinal())) {
        newField.getAccessFlags().demoteFromFinal();
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean needsRelaxedType(
        DexEncodedField targetField, Iterable<DexEncodedField> sourceFields) {
      return Iterables.any(
          sourceFields, sourceField -> sourceField.getType() != targetField.getType());
    }
  }
}
