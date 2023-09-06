// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.Multiset;
import java.util.Objects;
import java.util.Set;

public class NonEmptyParameterUsage extends ParameterUsage {

  private Set<DexType> castsWithParameter;
  private Set<DexField> fieldsReadFromParameter;
  private Multiset<DexMethod> methodCallsWithParameterAsReceiver;

  private boolean isParameterMutated;
  private boolean isParameterReturned;
  private boolean isParameterUsedAsLock;

  NonEmptyParameterUsage(
      Set<DexType> castsWithParameter,
      Set<DexField> fieldsReadFromParameter,
      Multiset<DexMethod> methodCallsWithParameterAsReceiver,
      boolean isParameterMutated,
      boolean isParameterReturned,
      boolean isParameterUsedAsLock) {
    assert !castsWithParameter.isEmpty()
        || !fieldsReadFromParameter.isEmpty()
        || !methodCallsWithParameterAsReceiver.isEmpty()
        || isParameterMutated
        || isParameterReturned
        || isParameterUsedAsLock;
    this.castsWithParameter = castsWithParameter;
    this.fieldsReadFromParameter = fieldsReadFromParameter;
    this.methodCallsWithParameterAsReceiver = methodCallsWithParameterAsReceiver;
    this.isParameterMutated = isParameterMutated;
    this.isParameterReturned = isParameterReturned;
    this.isParameterUsedAsLock = isParameterUsedAsLock;
  }

  @Override
  ParameterUsage addCastWithParameter(DexType castType) {
    throw new Unreachable();
  }

  @Override
  ParameterUsage addFieldReadFromParameter(DexField field) {
    throw new Unreachable();
  }

  @Override
  ParameterUsage addMethodCallWithParameterAsReceiver(InvokeMethodWithReceiver invoke) {
    throw new Unreachable();
  }

  @Override
  public NonEmptyParameterUsage asNonEmpty() {
    return this;
  }

  @Override
  ParameterUsage externalize() {
    throw new Unreachable();
  }

  public boolean hasFieldsReadFromParameter() {
    return !getFieldsReadFromParameter().isEmpty();
  }

  public Set<DexType> getCastsWithParameter() {
    return castsWithParameter;
  }

  public Set<DexField> getFieldsReadFromParameter() {
    return fieldsReadFromParameter;
  }

  public Multiset<DexMethod> getMethodCallsWithParameterAsReceiver() {
    return methodCallsWithParameterAsReceiver;
  }

  @Override
  public boolean isParameterMutated() {
    return isParameterMutated;
  }

  @Override
  public boolean isParameterReturned() {
    return isParameterReturned;
  }

  @Override
  public boolean isParameterUsedAsLock() {
    return isParameterUsedAsLock;
  }

  @Override
  ParameterUsage setParameterMutated() {
    throw new Unreachable();
  }

  @Override
  ParameterUsage setParameterReturned() {
    throw new Unreachable();
  }

  @Override
  ParameterUsage setParameterUsedAsLock() {
    throw new Unreachable();
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    NonEmptyParameterUsage knownParameterUsage = (NonEmptyParameterUsage) obj;
    return isParameterMutated == knownParameterUsage.isParameterMutated
        && isParameterReturned == knownParameterUsage.isParameterReturned
        && isParameterUsedAsLock == knownParameterUsage.isParameterUsedAsLock
        && castsWithParameter.equals(knownParameterUsage.castsWithParameter)
        && fieldsReadFromParameter.equals(knownParameterUsage.fieldsReadFromParameter)
        && methodCallsWithParameterAsReceiver.equals(
            knownParameterUsage.methodCallsWithParameterAsReceiver);
  }

  @Override
  public int hashCode() {
    int hash =
        31 * (31 * (31 + castsWithParameter.hashCode()) + fieldsReadFromParameter.hashCode())
            + methodCallsWithParameterAsReceiver.hashCode();
    assert hash
        == Objects.hash(
            castsWithParameter, fieldsReadFromParameter, methodCallsWithParameterAsReceiver);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterMutated);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterReturned);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterUsedAsLock);
    return hash;
  }
}
