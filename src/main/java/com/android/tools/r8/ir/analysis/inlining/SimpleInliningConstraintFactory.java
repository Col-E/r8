// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleInliningConstraintFactory {

  private final Map<Integer, BooleanFalseSimpleInliningConstraint> booleanFalseConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, BooleanTrueSimpleInliningConstraint> booleanTrueConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NotNullSimpleInliningConstraint> notNullConstraints =
      new ConcurrentHashMap<>();
  private final Map<Integer, NullSimpleInliningConstraint> nullConstraints =
      new ConcurrentHashMap<>();

  public BooleanFalseSimpleInliningConstraint createBooleanFalseConstraint(int argumentIndex) {
    return booleanFalseConstraints.computeIfAbsent(
        argumentIndex, key -> BooleanFalseSimpleInliningConstraint.create(key, this));
  }

  public BooleanTrueSimpleInliningConstraint createBooleanTrueConstraint(int argumentIndex) {
    return booleanTrueConstraints.computeIfAbsent(
        argumentIndex, key -> BooleanTrueSimpleInliningConstraint.create(key, this));
  }

  public NotNullSimpleInliningConstraint createNotNullConstraint(int argumentIndex) {
    return notNullConstraints.computeIfAbsent(
        argumentIndex, key -> NotNullSimpleInliningConstraint.create(key, this));
  }

  public NullSimpleInliningConstraint createNullConstraint(int argumentIndex) {
    return nullConstraints.computeIfAbsent(
        argumentIndex, key -> NullSimpleInliningConstraint.create(key, this));
  }
}
