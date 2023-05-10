// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class FieldCollectionBacking {

  // Internal consistency.

  static boolean belongsInStaticPool(DexEncodedField field) {
    return field.isStatic();
  }

  static boolean belongsInInstancePool(DexEncodedField field) {
    return !belongsInStaticPool(field);
  }

  abstract boolean verify();

  // Traversal methods.

  abstract <BT, CT> TraversalContinuation<BT, CT> traverse(
      DexClass holder, Function<? super DexClassAndField, TraversalContinuation<BT, CT>> fn);

  abstract <BT, CT> TraversalContinuation<BT, CT> traverse(
      DexClass holder,
      BiFunction<? super DexClassAndField, ? super CT, TraversalContinuation<BT, CT>> fn,
      CT initialValue);

  // Collection methods.

  abstract int size();

  abstract Iterable<DexEncodedField> fields(Predicate<? super DexEncodedField> predicate);

  // Specialized to static fields.

  abstract int numberOfStaticFields();

  abstract List<DexEncodedField> staticFieldsAsList();

  abstract void appendStaticField(DexEncodedField field);

  abstract void appendStaticFields(Collection<DexEncodedField> fields);

  abstract void clearStaticFields();

  abstract void setStaticFields(DexEncodedField[] fields);

  // Specialized to instance fields.

  abstract int numberOfInstanceFields();

  abstract List<DexEncodedField> instanceFieldsAsList();

  abstract void appendInstanceField(DexEncodedField field);

  abstract void appendInstanceFields(Collection<DexEncodedField> fields);

  abstract void clearInstanceFields();

  abstract void setInstanceFields(DexEncodedField[] fields);

  abstract DexEncodedField lookupField(DexField field);

  abstract DexEncodedField lookupStaticField(DexField field);

  abstract DexEncodedField lookupInstanceField(DexField field);

  abstract void replaceFields(Function<DexEncodedField, DexEncodedField> replacement);
}
