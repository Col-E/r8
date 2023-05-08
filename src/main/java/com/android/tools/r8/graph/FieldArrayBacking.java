// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.TraversalContinuation;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class FieldArrayBacking extends FieldCollectionBacking {

  private DexEncodedField[] staticFields;
  private DexEncodedField[] instanceFields;

  public static FieldCollectionBacking fromArrays(
      DexEncodedField[] staticFields, DexEncodedField[] instanceFields) {
    return new FieldArrayBacking(staticFields, instanceFields);
  }

  private FieldArrayBacking(DexEncodedField[] staticFields, DexEncodedField[] instanceFields) {
    assert staticFields != null;
    assert instanceFields != null;
    this.staticFields = staticFields;
    this.instanceFields = instanceFields;
  }

  @Override
  boolean verify() {
    assert verifyNoDuplicateFields();
    return true;
  }

  private boolean verifyNoDuplicateFields() {
    Set<DexField> unique = Sets.newIdentityHashSet();
    for (DexEncodedField field : fields(alwaysTrue())) {
      boolean changed = unique.add(field.getReference());
      assert changed : "Duplicate field `" + field.getReference().toSourceString() + "`";
    }
    return true;
  }

  @Override
  int numberOfStaticFields() {
    return staticFields.length;
  }

  @Override
  int numberOfInstanceFields() {
    return instanceFields.length;
  }

  @Override
  int size() {
    return staticFields.length + instanceFields.length;
  }

  @Override
  <BT, CT> TraversalContinuation<BT, CT> traverse(
      DexClass holder, Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn) {
    TraversalContinuation<BT, CT> traversalContinuation = TraversalContinuation.doContinue();
    for (DexEncodedField definition : staticFields) {
      DexClassAndField field = DexClassAndField.create(holder, definition);
      traversalContinuation = fn.apply(field);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    for (DexEncodedField definition : instanceFields) {
      DexClassAndField field = DexClassAndField.create(holder, definition);
      traversalContinuation = fn.apply(field);
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return traversalContinuation;
  }

  @Override
  <BT, CT> TraversalContinuation<BT, CT> traverse(
      DexClass holder,
      BiFunction<? super DexClassAndField, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    TraversalContinuation<BT, CT> traversalContinuation =
        TraversalContinuation.doContinue(initialValue);
    for (DexEncodedField definition : staticFields) {
      DexClassAndField field = DexClassAndField.create(holder, definition);
      traversalContinuation = fn.apply(field, traversalContinuation.asContinue().getValue());
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    for (DexEncodedField definition : instanceFields) {
      DexClassAndField field = DexClassAndField.create(holder, definition);
      traversalContinuation = fn.apply(field, traversalContinuation.asContinue().getValue());
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return traversalContinuation;
  }

  @Override
  Iterable<DexEncodedField> fields(Predicate<? super DexEncodedField> predicate) {
    return Iterables.concat(
        Iterables.filter(Arrays.asList(instanceFields), predicate::test),
        Iterables.filter(Arrays.asList(staticFields), predicate::test));
  }

  @Override
  List<DexEncodedField> staticFieldsAsList() {
    if (InternalOptions.assertionsEnabled()) {
      return Collections.unmodifiableList(Arrays.asList(staticFields));
    }
    return Arrays.asList(staticFields);
  }

  @Override
  void appendStaticField(DexEncodedField field) {
    staticFields = appendFieldHelper(staticFields, field);
  }

  @Override
  void appendStaticFields(Collection<DexEncodedField> fields) {
    staticFields = appendFieldsHelper(staticFields, fields);
  }

  @Override
  void clearStaticFields() {
    staticFields = DexEncodedField.EMPTY_ARRAY;
  }

  @Override
  public void setStaticFields(DexEncodedField[] fields) {
    assert fields != null;
    staticFields = fields;
  }

  @Override
  List<DexEncodedField> instanceFieldsAsList() {
    if (InternalOptions.assertionsEnabled()) {
      return Collections.unmodifiableList(Arrays.asList(instanceFields));
    }
    return Arrays.asList(instanceFields);
  }

  @Override
  void appendInstanceField(DexEncodedField field) {
    instanceFields = appendFieldHelper(instanceFields, field);
  }

  @Override
  void appendInstanceFields(Collection<DexEncodedField> fields) {
    instanceFields = appendFieldsHelper(instanceFields, fields);
  }

  @Override
  void clearInstanceFields() {
    instanceFields = DexEncodedField.EMPTY_ARRAY;
  }

  @Override
  void setInstanceFields(DexEncodedField[] fields) {
    assert fields != null;
    instanceFields = fields;
  }

  @Override
  DexEncodedField lookupField(DexField field) {
    DexEncodedField result = lookupInstanceField(field);
    return result == null ? lookupStaticField(field) : result;
  }

  @Override
  DexEncodedField lookupStaticField(DexField field) {
    return lookupFieldHelper(staticFields, field);
  }

  @Override
  DexEncodedField lookupInstanceField(DexField field) {
    return lookupFieldHelper(instanceFields, field);
  }

  @Override
  void replaceFields(Function<DexEncodedField, DexEncodedField> replacement) {
    staticFields =
        replaceFieldsHelper(
            staticFields,
            replacement,
            FieldCollectionBacking::belongsInStaticPool,
            this::appendInstanceFields);
    instanceFields =
        replaceFieldsHelper(
            instanceFields,
            replacement,
            FieldCollectionBacking::belongsInInstancePool,
            this::appendStaticFields);
  }

  private static DexEncodedField[] appendFieldHelper(
      DexEncodedField[] existingItems, DexEncodedField itemToAppend) {
    DexEncodedField[] newFields = new DexEncodedField[existingItems.length + 1];
    System.arraycopy(existingItems, 0, newFields, 0, existingItems.length);
    newFields[existingItems.length] = itemToAppend;
    return newFields;
  }

  private static DexEncodedField[] appendFieldsHelper(
      DexEncodedField[] existingItems, Collection<DexEncodedField> itemsToAppend) {
    DexEncodedField[] newFields = new DexEncodedField[existingItems.length + itemsToAppend.size()];
    System.arraycopy(existingItems, 0, newFields, 0, existingItems.length);
    int i = existingItems.length;
    for (DexEncodedField field : itemsToAppend) {
      newFields[i] = field;
      i++;
    }
    return newFields;
  }

  private static DexEncodedField lookupFieldHelper(DexEncodedField[] items, DexField reference) {
    for (int i = 0; i < items.length; i++) {
      DexEncodedField item = items[i];
      if (reference.match(item)) {
        return item;
      }
    }
    return null;
  }

  private static DexEncodedField[] replaceFieldsHelper(
      DexEncodedField[] fields,
      Function<DexEncodedField, DexEncodedField> replacement,
      Predicate<DexEncodedField> inThisPool,
      Consumer<List<DexEncodedField>> onMovedToOtherPool) {
    List<DexEncodedField> movedToOtherPool = new ArrayList<>();
    for (int i = 0; i < fields.length; i++) {
      DexEncodedField existingField = fields[i];
      assert inThisPool.test(existingField);
      DexEncodedField newField = replacement.apply(existingField);
      assert newField != null;
      if (existingField != newField) {
        if (inThisPool.test(newField)) {
          fields[i] = newField;
        } else {
          fields[i] = null;
          movedToOtherPool.add(newField);
        }
      }
    }
    if (movedToOtherPool.isEmpty()) {
      return fields;
    }
    onMovedToOtherPool.accept(movedToOtherPool);
    return ArrayUtils.filter(
        fields,
        Objects::nonNull,
        DexEncodedField.EMPTY_ARRAY,
        fields.length - movedToOtherPool.size());
  }
}
