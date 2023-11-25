// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class FieldCollection {

  // Threshold between using an array and a map for the backing store.
  // The choice of 30 is just a copy from the method backing threshold.
  private static final int ARRAY_BACKING_THRESHOLD = 30;

  private final DexClass holder;
  private FieldCollectionBacking backing;

  private FieldCollection(DexClass holder, FieldCollectionBacking backing) {
    this.holder = holder;
    this.backing = backing;
  }

  public static FieldCollection create(
      DexClass holder, DexEncodedField[] staticFields, DexEncodedField[] instanceFields) {
    int fieldCount = staticFields.length + instanceFields.length;
    FieldCollectionBacking backing;
    if (fieldCount > ARRAY_BACKING_THRESHOLD) {
      backing = FieldMapBacking.createLinked(fieldCount);
      backing.setStaticFields(staticFields);
      backing.setInstanceFields(instanceFields);
    } else {
      backing = FieldArrayBacking.fromArrays(staticFields, instanceFields);
    }
    return createInternal(holder, backing);
  }

  private static FieldCollection createInternal(DexClass holder, FieldCollectionBacking backing) {
    // Internal create mirrors MethodCollection in case of adding a concurrency checker.
    return new FieldCollection(holder, backing);
  }

  public int size() {
    return backing.size();
  }

  public void forEachField(Consumer<DexClassAndField> fn) {
    traverse(
        field -> {
          fn.accept(field);
          return TraversalContinuation.doContinue();
        });
  }

  public Iterable<DexEncodedField> fields(Predicate<? super DexEncodedField> predicate) {
    return backing.fields(predicate);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverse(
      Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn) {
    return backing.traverse(holder, fn);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverse(
      BiFunction<? super DexClassAndField, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return backing.traverse(holder, fn, initialValue);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseInstanceFields(
      Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn) {
    return backing.traverseInstanceFields(holder, fn);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseInstanceFields(
      BiFunction<? super DexClassAndField, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return backing.traverseInstanceFields(holder, fn, initialValue);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseStaticFields(
      Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn) {
    return backing.traverseStaticFields(holder, fn);
  }

  public <BT, CT> TraversalContinuation<BT, CT> traverseStaticFields(
      BiFunction<? super DexClassAndField, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue) {
    return backing.traverseStaticFields(holder, fn, initialValue);
  }

  public boolean verify() {
    forEachField(
        field -> {
          assert verifyCorrectnessOfFieldHolder(field);
        });
    assert backing.verify();
    return true;
  }

  private boolean verifyCorrectnessOfFieldHolder(DexClassAndField field) {
    assert verifyCorrectnessOfFieldHolder(field.getDefinition());
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean verifyCorrectnessOfFieldHolder(DexEncodedField field) {
    assert field.getHolderType() == holder.type
        : "Expected field `"
            + field.getReference().toSourceString()
            + "` to have holder `"
            + holder.type.toSourceString()
            + "`";
    return true;
  }

  private boolean verifyCorrectnessOfFieldHolders(Iterable<DexEncodedField> fields) {
    for (DexEncodedField field : fields) {
      assert verifyCorrectnessOfFieldHolder(field);
    }
    return true;
  }

  public boolean hasStaticFields() {
    return backing.numberOfStaticFields() > 0;
  }

  public List<DexEncodedField> staticFieldsAsList() {
    return backing.staticFieldsAsList();
  }

  public void appendStaticField(DexEncodedField field) {
    assert verifyCorrectnessOfFieldHolder(field);
    backing.appendStaticField(field);
    assert backing.verify();
  }

  public void appendStaticFields(Collection<DexEncodedField> fields) {
    assert verifyCorrectnessOfFieldHolders(fields);
    backing.appendStaticFields(fields);
    assert backing.verify();
  }

  public void clearStaticFields() {
    backing.clearStaticFields();
  }

  public void setStaticFields(DexEncodedField[] fields) {
    backing.setStaticFields(fields);
    assert backing.verify();
  }

  public boolean hasInstanceFields() {
    return backing.numberOfInstanceFields() > 0;
  }

  public List<DexEncodedField> instanceFieldsAsList() {
    return backing.instanceFieldsAsList();
  }

  public void appendInstanceField(DexEncodedField field) {
    assert verifyCorrectnessOfFieldHolder(field);
    backing.appendInstanceField(field);
    assert backing.verify();
  }

  public void appendInstanceFields(Collection<DexEncodedField> fields) {
    assert verifyCorrectnessOfFieldHolders(fields);
    backing.appendInstanceFields(fields);
    assert backing.verify();
  }

  public void clearInstanceFields() {
    backing.clearInstanceFields();
  }

  public void setInstanceFields(DexEncodedField[] fields) {
    backing.setInstanceFields(fields);
    assert backing.verify();
  }

  public DexEncodedField lookupField(DexField field) {
    return backing.lookupField(field);
  }

  public DexEncodedField lookupStaticField(DexField field) {
    return backing.lookupStaticField(field);
  }

  public DexEncodedField lookupInstanceField(DexField field) {
    return backing.lookupInstanceField(field);
  }

  public void replaceFields(Function<DexEncodedField, DexEncodedField> replacement) {
    backing.replaceFields(replacement);
  }

  public List<DexEncodedField> allFieldsSorted() {
    List<DexEncodedField> sorted = new ArrayList<>(size());
    forEachField(field -> sorted.add(field.getDefinition()));
    sorted.sort(Comparator.comparing(DexEncodedMember::getReference));
    return sorted;
  }

  public boolean hasAnnotations() {
    return traverse(field -> TraversalContinuation.breakIf(field.getDefinition().hasAnnotations()))
        .shouldBreak();
  }
}
