// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class SimpleInliningConstraintFactory {

  // Immutable argument constraints for low argument indices to avoid overhead of ConcurrentHashMap.
  private final BooleanFalseSimpleInliningConstraint[] lowBooleanFalseConstraints =
      new BooleanFalseSimpleInliningConstraint[5];
  private final BooleanTrueSimpleInliningConstraint[] lowBooleanTrueConstraints =
      new BooleanTrueSimpleInliningConstraint[5];
  private final NotNullSimpleInliningConstraint[] lowNotNullConstraints =
      new NotNullSimpleInliningConstraint[5];
  private final NullSimpleInliningConstraint[] lowNullConstraints =
      new NullSimpleInliningConstraint[5];

  // Argument constraints for high argument indices.
  private final Map<Integer, BooleanFalseSimpleInliningConstraint> highBooleanFalseConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, BooleanTrueSimpleInliningConstraint> highBooleanTrueConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NotNullSimpleInliningConstraint> highNotNullConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NullSimpleInliningConstraint> highNullConstraints =
      new ConcurrentHashMap<>();

  public SimpleInliningConstraintFactory() {
    for (int i = 0; i < lowBooleanFalseConstraints.length; i++) {
      lowBooleanFalseConstraints[i] = BooleanFalseSimpleInliningConstraint.create(i, this);
    }
    for (int i = 0; i < lowBooleanTrueConstraints.length; i++) {
      lowBooleanTrueConstraints[i] = BooleanTrueSimpleInliningConstraint.create(i, this);
    }
    for (int i = 0; i < lowNotNullConstraints.length; i++) {
      lowNotNullConstraints[i] = NotNullSimpleInliningConstraint.create(i, this);
    }
    for (int i = 0; i < lowNullConstraints.length; i++) {
      lowNullConstraints[i] = NullSimpleInliningConstraint.create(i, this);
    }
  }

  public BooleanFalseSimpleInliningConstraint createBooleanFalseConstraint(int argumentIndex) {
    return createArgumentConstraint(
        argumentIndex,
        lowBooleanFalseConstraints,
        highBooleanFalseConstraints,
        () -> BooleanFalseSimpleInliningConstraint.create(argumentIndex, this));
  }

  public BooleanTrueSimpleInliningConstraint createBooleanTrueConstraint(int argumentIndex) {
    return createArgumentConstraint(
        argumentIndex,
        lowBooleanTrueConstraints,
        highBooleanTrueConstraints,
        () -> BooleanTrueSimpleInliningConstraint.create(argumentIndex, this));
  }

  public NotNullSimpleInliningConstraint createNotNullConstraint(int argumentIndex) {
    return createArgumentConstraint(
        argumentIndex,
        lowNotNullConstraints,
        highNotNullConstraints,
        () -> NotNullSimpleInliningConstraint.create(argumentIndex, this));
  }

  public NullSimpleInliningConstraint createNullConstraint(int argumentIndex) {
    return createArgumentConstraint(
        argumentIndex,
        lowNullConstraints,
        highNullConstraints,
        () -> NullSimpleInliningConstraint.create(argumentIndex, this));
  }

  public NotEqualToNumberSimpleInliningConstraint createNotNumberConstraint(
      int argumentIndex, long rawValue) {
    return NotEqualToNumberSimpleInliningConstraint.create(argumentIndex, rawValue, this);
  }

  public EqualToNumberSimpleInliningConstraint createNumberConstraint(
      int argumentIndex, long rawValue) {
    return EqualToNumberSimpleInliningConstraint.create(argumentIndex, rawValue, this);
  }

  private <T extends SimpleInliningArgumentConstraint> T createArgumentConstraint(
      int argumentIndex, T[] lowConstraints, Map<Integer, T> highConstraints, Supplier<T> fn) {
    return argumentIndex < lowConstraints.length
        ? lowConstraints[argumentIndex]
        : highConstraints.computeIfAbsent(argumentIndex, key -> fn.get());
  }
}
