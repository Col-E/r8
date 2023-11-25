// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Non-trivial information (neither BOTTOM nor TOP) about a method's usage of a given parameter.
 *
 * <p>This is internal to the analysis, since {@link #methodCallsWithParameterAsReceiver} references
 * instructions from {@link com.android.tools.r8.ir.code.IRCode}, which makes it unsuited for being
 * stored in {@link com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo}.
 */
public class InternalNonEmptyParameterUsage extends ParameterUsage {

  private Set<DexType> castsWithParameter;
  private Set<DexField> fieldsReadFromParameter;
  private Set<InvokeMethodWithReceiver> methodCallsWithParameterAsReceiver;

  private boolean isParameterMutated;
  private boolean isParameterReturned;
  private boolean isParameterUsedAsLock;

  InternalNonEmptyParameterUsage(
      Set<DexType> castsWithParameter,
      Set<DexField> fieldsReadFromParameter,
      Set<InvokeMethodWithReceiver> methodCallsWithParameterAsReceiver,
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

  static Builder builder() {
    return new Builder();
  }

  Builder builderFromInstance() {
    return new Builder(this);
  }

  @Override
  ParameterUsage addCastWithParameter(DexType castType) {
    ImmutableSet.Builder<DexType> newCastsWithParameter = ImmutableSet.builder();
    newCastsWithParameter.addAll(castsWithParameter);
    newCastsWithParameter.add(castType);
    return new InternalNonEmptyParameterUsage(
        newCastsWithParameter.build(),
        fieldsReadFromParameter,
        methodCallsWithParameterAsReceiver,
        isParameterMutated,
        isParameterReturned,
        isParameterUsedAsLock);
  }

  @Override
  InternalNonEmptyParameterUsage addFieldReadFromParameter(DexField field) {
    ImmutableSet.Builder<DexField> newFieldsReadFromParameterBuilder = ImmutableSet.builder();
    newFieldsReadFromParameterBuilder.addAll(fieldsReadFromParameter);
    newFieldsReadFromParameterBuilder.add(field);
    return new InternalNonEmptyParameterUsage(
        castsWithParameter,
        newFieldsReadFromParameterBuilder.build(),
        methodCallsWithParameterAsReceiver,
        isParameterMutated,
        isParameterReturned,
        isParameterUsedAsLock);
  }

  @Override
  InternalNonEmptyParameterUsage addMethodCallWithParameterAsReceiver(
      InvokeMethodWithReceiver invoke) {
    ImmutableSet.Builder<InvokeMethodWithReceiver> newMethodCallsWithParameterAsReceiverBuilder =
        ImmutableSet.builder();
    newMethodCallsWithParameterAsReceiverBuilder.addAll(methodCallsWithParameterAsReceiver);
    newMethodCallsWithParameterAsReceiverBuilder.add(invoke);
    return new InternalNonEmptyParameterUsage(
        castsWithParameter,
        fieldsReadFromParameter,
        newMethodCallsWithParameterAsReceiverBuilder.build(),
        isParameterMutated,
        isParameterReturned,
        isParameterUsedAsLock);
  }

  @Override
  public InternalNonEmptyParameterUsage asInternalNonEmpty() {
    return this;
  }

  @Override
  ParameterUsage externalize() {
    ImmutableMultiset.Builder<DexMethod> methodCallsWithParameterAsReceiverBuilder =
        ImmutableMultiset.builder();
    methodCallsWithParameterAsReceiver.forEach(
        invoke -> methodCallsWithParameterAsReceiverBuilder.add(invoke.getInvokedMethod()));
    return new NonEmptyParameterUsage(
        castsWithParameter,
        fieldsReadFromParameter,
        methodCallsWithParameterAsReceiverBuilder.build(),
        isParameterMutated,
        isParameterReturned,
        isParameterUsedAsLock);
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

  InternalNonEmptyParameterUsage join(InternalNonEmptyParameterUsage other) {
    return builderFromInstance()
        .addCastsWithParameter(other.castsWithParameter)
        .addFieldsReadFromParameter(other.fieldsReadFromParameter)
        .addMethodCallsWithParameterAsReceiver(other.methodCallsWithParameterAsReceiver)
        .joinIsReceiverMutated(other.isParameterMutated)
        .joinIsReceiverReturned(other.isParameterReturned)
        .joinIsReceiverUsedAsLock(other.isParameterUsedAsLock)
        .build();
  }

  @Override
  InternalNonEmptyParameterUsage setParameterMutated() {
    if (isParameterMutated) {
      return this;
    }
    return new InternalNonEmptyParameterUsage(
        castsWithParameter,
        fieldsReadFromParameter,
        methodCallsWithParameterAsReceiver,
        true,
        isParameterReturned,
        isParameterUsedAsLock);
  }

  @Override
  InternalNonEmptyParameterUsage setParameterReturned() {
    if (isParameterReturned) {
      return this;
    }
    return new InternalNonEmptyParameterUsage(
        castsWithParameter,
        fieldsReadFromParameter,
        methodCallsWithParameterAsReceiver,
        isParameterMutated,
        true,
        isParameterUsedAsLock);
  }

  @Override
  InternalNonEmptyParameterUsage setParameterUsedAsLock() {
    if (isParameterUsedAsLock) {
      return this;
    }
    return new InternalNonEmptyParameterUsage(
        castsWithParameter,
        fieldsReadFromParameter,
        methodCallsWithParameterAsReceiver,
        isParameterMutated,
        isParameterReturned,
        true);
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
    InternalNonEmptyParameterUsage knownParameterUsage = (InternalNonEmptyParameterUsage) obj;
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

  static class Builder {

    private ImmutableSet.Builder<DexType> castsWithParameterBuilder;
    private ImmutableSet.Builder<DexField> fieldsReadFromParameterBuilder;
    private ImmutableSet.Builder<InvokeMethodWithReceiver>
        methodCallsWithParameterAsReceiverBuilder;
    private boolean isParameterMutated;
    private boolean isParameterReturned;
    private boolean isParameterUsedAsLock;

    Builder() {
      castsWithParameterBuilder = ImmutableSet.builder();
      fieldsReadFromParameterBuilder = ImmutableSet.builder();
      methodCallsWithParameterAsReceiverBuilder = ImmutableSet.builder();
    }

    Builder(InternalNonEmptyParameterUsage methodBehavior) {
      castsWithParameterBuilder =
          ImmutableSet.<DexType>builder().addAll(methodBehavior.castsWithParameter);
      fieldsReadFromParameterBuilder =
          ImmutableSet.<DexField>builder().addAll(methodBehavior.fieldsReadFromParameter);
      methodCallsWithParameterAsReceiverBuilder =
          ImmutableSet.<InvokeMethodWithReceiver>builder()
              .addAll(methodBehavior.methodCallsWithParameterAsReceiver);
      isParameterMutated = methodBehavior.isParameterMutated;
      isParameterReturned = methodBehavior.isParameterReturned;
      isParameterUsedAsLock = methodBehavior.isParameterUsedAsLock;
    }

    Builder addCastWithParameter(DexType castType) {
      castsWithParameterBuilder.add(castType);
      return this;
    }

    Builder addCastsWithParameter(Collection<DexType> castTypes) {
      castsWithParameterBuilder.addAll(castTypes);
      return this;
    }

    Builder addFieldReadFromParameter(DexField fieldReadFromParameter) {
      fieldsReadFromParameterBuilder.add(fieldReadFromParameter);
      return this;
    }

    Builder addFieldsReadFromParameter(Collection<DexField> fieldsReadFromParameter) {
      fieldsReadFromParameterBuilder.addAll(fieldsReadFromParameter);
      return this;
    }

    Builder addMethodCallWithParameterAsReceiver(
        InvokeMethodWithReceiver methodCallWithParameterAsReceiver) {
      methodCallsWithParameterAsReceiverBuilder.add(methodCallWithParameterAsReceiver);
      return this;
    }

    Builder addMethodCallsWithParameterAsReceiver(
        Set<InvokeMethodWithReceiver> methodCallsWithParameterAsReceiver) {
      methodCallsWithParameterAsReceiverBuilder.addAll(methodCallsWithParameterAsReceiver);
      return this;
    }

    Builder joinIsReceiverMutated(boolean isParameterMutated) {
      this.isParameterMutated |= isParameterMutated;
      return this;
    }

    Builder joinIsReceiverReturned(boolean isParameterReturned) {
      this.isParameterReturned |= isParameterReturned;
      return this;
    }

    Builder joinIsReceiverUsedAsLock(boolean isParameterUsedAsLock) {
      this.isParameterUsedAsLock |= isParameterUsedAsLock;
      return this;
    }

    Builder setParameterMutated() {
      this.isParameterMutated = true;
      return this;
    }

    Builder setParameterReturned() {
      this.isParameterReturned = true;
      return this;
    }

    Builder setParameterUsedAsLock() {
      this.isParameterUsedAsLock = true;
      return this;
    }

    InternalNonEmptyParameterUsage build() {
      return new InternalNonEmptyParameterUsage(
          castsWithParameterBuilder.build(),
          fieldsReadFromParameterBuilder.build(),
          methodCallsWithParameterAsReceiverBuilder.build(),
          isParameterMutated,
          isParameterReturned,
          isParameterUsedAsLock);
    }
  }
}
