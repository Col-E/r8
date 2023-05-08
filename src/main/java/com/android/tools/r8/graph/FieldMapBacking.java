// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import it.unimi.dsi.fastutil.objects.Object2ReferenceLinkedOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.SortedMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class FieldMapBacking extends FieldCollectionBacking {

  private SortedMap<DexFieldSignature, DexEncodedField> fieldMap;

  public static FieldMapBacking createLinked(int capacity) {
    return new FieldMapBacking(createdLinkedMap(capacity));
  }

  private static SortedMap<DexFieldSignature, DexEncodedField> createdLinkedMap(int capacity) {
    return new Object2ReferenceLinkedOpenHashMap<>(capacity);
  }

  private FieldMapBacking(SortedMap<DexFieldSignature, DexEncodedField> fieldMap) {
    this.fieldMap = fieldMap;
  }

  // Internal map allocation that shall preserve the map-backing type.
  // Only the linked map exists for fields currently.
  private SortedMap<DexFieldSignature, DexEncodedField> internalCreateMap(int capacity) {
    return createdLinkedMap(capacity);
  }

  @Override
  boolean verify() {
    fieldMap.forEach(
        (signature, field) -> {
          assert signature.match(field.getReference());
        });
    return true;
  }

  @Override
  <BT, CT> TraversalContinuation<BT, CT> traverse(
      DexClass holder, Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn) {
    TraversalContinuation<BT, CT> traversalContinuation = TraversalContinuation.doContinue();
    for (DexEncodedField definition : fieldMap.values()) {
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
    for (DexEncodedField definition : fieldMap.values()) {
      DexClassAndField field = DexClassAndField.create(holder, definition);
      traversalContinuation = fn.apply(field, traversalContinuation.asContinue().getValue());
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return traversalContinuation;
  }

  @Override
  int size() {
    return fieldMap.size();
  }

  @Override
  Iterable<DexEncodedField> fields(Predicate<? super DexEncodedField> predicate) {
    return IterableUtils.filter(fieldMap.values(), predicate);
  }

  @Override
  int numberOfStaticFields() {
    return numberOfFieldsHelper(FieldCollectionBacking::belongsInStaticPool);
  }

  @Override
  List<DexEncodedField> staticFieldsAsList() {
    return fieldsAsListHelper(FieldCollectionBacking::belongsInStaticPool);
  }

  @Override
  void appendStaticField(DexEncodedField field) {
    assert belongsInStaticPool(field);
    DexEncodedField old = fieldMap.put(getSignature(field), field);
    assert old == null;
  }

  @Override
  void appendStaticFields(Collection<DexEncodedField> fields) {
    fields.forEach(this::appendStaticField);
  }

  @Override
  void clearStaticFields() {
    fieldMap.values().removeIf(FieldCollectionBacking::belongsInStaticPool);
  }

  @Override
  void setStaticFields(DexEncodedField[] fields) {
    setFieldsInPoolHelper(fields, FieldCollectionBacking::belongsInStaticPool);
  }

  @Override
  int numberOfInstanceFields() {
    return numberOfFieldsHelper(FieldCollectionBacking::belongsInInstancePool);
  }

  @Override
  List<DexEncodedField> instanceFieldsAsList() {
    return fieldsAsListHelper(FieldCollectionBacking::belongsInInstancePool);
  }

  @Override
  void appendInstanceField(DexEncodedField field) {
    assert belongsInInstancePool(field);
    DexEncodedField old = fieldMap.put(getSignature(field), field);
    assert old == null;
  }

  @Override
  void appendInstanceFields(Collection<DexEncodedField> fields) {
    fields.forEach(this::appendInstanceField);
  }

  @Override
  void clearInstanceFields() {
    fieldMap.values().removeIf(FieldCollectionBacking::belongsInInstancePool);
  }

  @Override
  void setInstanceFields(DexEncodedField[] fields) {
    setFieldsInPoolHelper(fields, FieldCollectionBacking::belongsInInstancePool);
  }

  @Override
  DexEncodedField lookupField(DexField field) {
    return fieldMap.get(getSignature(field));
  }

  @Override
  DexEncodedField lookupStaticField(DexField field) {
    DexEncodedField result = lookupField(field);
    return result != null && belongsInStaticPool(result) ? result : null;
  }

  @Override
  DexEncodedField lookupInstanceField(DexField field) {
    DexEncodedField result = lookupField(field);
    return result != null && belongsInInstancePool(result) ? result : null;
  }

  @Override
  void replaceFields(Function<DexEncodedField, DexEncodedField> replacement) {
    // The code assumes that when replacement.apply(field) is called, the map is up-to-date with
    // the previously replaced fields. We therefore cannot postpone the map updates to the end of
    // the replacement.
    ArrayList<DexEncodedField> initialValues = new ArrayList<>(fieldMap.values());
    for (DexEncodedField field : initialValues) {
      DexEncodedField newField = replacement.apply(field);
      if (newField != field) {
        DexFieldSignature oldSignature = getSignature(field);
        DexFieldSignature newSignature = getSignature(newField);
        if (!newSignature.isEqualTo(oldSignature)) {
          if (fieldMap.get(oldSignature) == field) {
            fieldMap.remove(oldSignature);
          }
        }
        fieldMap.put(newSignature, newField);
      }
    }
  }

  private DexFieldSignature getSignature(DexEncodedField field) {
    return getSignature(field.getReference());
  }

  private DexFieldSignature getSignature(DexField field) {
    return DexFieldSignature.fromField(field);
  }

  private int numberOfFieldsHelper(Predicate<DexEncodedField> predicate) {
    int count = 0;
    for (DexEncodedField field : fieldMap.values()) {
      if (predicate.test(field)) {
        count++;
      }
    }
    return count;
  }

  private List<DexEncodedField> fieldsAsListHelper(Predicate<DexEncodedField> predicate) {
    List<DexEncodedField> result = new ArrayList<>(fieldMap.size());
    fieldMap.forEach(
        (signature, field) -> {
          if (predicate.test(field)) {
            result.add(field);
          }
        });
    return Collections.unmodifiableList(result);
  }

  private void setFieldsInPoolHelper(
      DexEncodedField[] fields, Predicate<DexEncodedField> inThisPool) {
    if (fields.length == 0 && fieldMap.isEmpty()) {
      return;
    }
    SortedMap<DexFieldSignature, DexEncodedField> newMap =
        internalCreateMap(size() + fields.length);
    fieldMap.forEach(
        (signature, field) -> {
          if (!inThisPool.test(field)) {
            newMap.put(signature, field);
          }
        });
    for (DexEncodedField field : fields) {
      assert inThisPool.test(field);
      newMap.put(getSignature(field), field);
    }
    fieldMap = newMap;
  }
}
