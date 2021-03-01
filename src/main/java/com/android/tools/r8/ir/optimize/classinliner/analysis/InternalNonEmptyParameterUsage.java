// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.utils.BooleanUtils;
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

  private Set<DexField> fieldsReadFromParameter;
  private Set<InvokeMethodWithReceiver> methodCallsWithParameterAsReceiver;

  private boolean isParameterMutated;
  private boolean isParameterReturned;
  private boolean isParameterUsedAsLock;

  InternalNonEmptyParameterUsage(
      Set<DexField> fieldsReadFromParameter,
      Set<InvokeMethodWithReceiver> methodCallsWithParameterAsReceiver,
      boolean isParameterMutated,
      boolean isParameterReturned,
      boolean isParameterUsedAsLock) {
    assert !fieldsReadFromParameter.isEmpty()
        || !methodCallsWithParameterAsReceiver.isEmpty()
        || isParameterMutated
        || isParameterReturned
        || isParameterUsedAsLock;
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
  public InternalNonEmptyParameterUsage asInternalNonEmpty() {
    return this;
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
        .addFieldsReadFromParameter(other.fieldsReadFromParameter)
        .addMethodCallsWithParameterAsReceiver(other.methodCallsWithParameterAsReceiver)
        .joinIsReceiverMutated(other.isParameterMutated)
        .joinIsReceiverReturned(other.isParameterReturned)
        .joinIsReceiverUsedAsLock(other.isParameterUsedAsLock)
        .build();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || obj.getClass() != getClass()) {
      return false;
    }
    InternalNonEmptyParameterUsage knownParameterUsage = (InternalNonEmptyParameterUsage) obj;
    return isParameterMutated == knownParameterUsage.isParameterMutated
        && isParameterReturned == knownParameterUsage.isParameterReturned
        && isParameterUsedAsLock == knownParameterUsage.isParameterUsedAsLock
        && fieldsReadFromParameter.equals(knownParameterUsage.fieldsReadFromParameter)
        && methodCallsWithParameterAsReceiver.equals(
            knownParameterUsage.methodCallsWithParameterAsReceiver);
  }

  @Override
  public int hashCode() {
    int hash =
        31 * (31 + fieldsReadFromParameter.hashCode())
            + methodCallsWithParameterAsReceiver.hashCode();
    assert hash == Objects.hash(fieldsReadFromParameter, methodCallsWithParameterAsReceiver);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterMutated);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterReturned);
    hash = (hash << 1) | BooleanUtils.intValue(isParameterUsedAsLock);
    return hash;
  }

  static class Builder {

    private ImmutableSet.Builder<DexField> fieldsReadFromParameterBuilder;
    private ImmutableSet.Builder<InvokeMethodWithReceiver>
        methodCallsWithParameterAsReceiverBuilder;
    private boolean isParameterMutated;
    private boolean isParameterReturned;
    private boolean isParameterUsedAsLock;

    Builder() {
      fieldsReadFromParameterBuilder = ImmutableSet.builder();
      methodCallsWithParameterAsReceiverBuilder = ImmutableSet.builder();
    }

    Builder(InternalNonEmptyParameterUsage methodBehavior) {
      fieldsReadFromParameterBuilder =
          ImmutableSet.<DexField>builder().addAll(methodBehavior.fieldsReadFromParameter);
      methodCallsWithParameterAsReceiverBuilder =
          ImmutableSet.<InvokeMethodWithReceiver>builder()
              .addAll(methodBehavior.methodCallsWithParameterAsReceiver);
      isParameterMutated = methodBehavior.isParameterMutated;
      isParameterReturned = methodBehavior.isParameterReturned;
      isParameterUsedAsLock = methodBehavior.isParameterUsedAsLock;
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
          fieldsReadFromParameterBuilder.build(),
          methodCallsWithParameterAsReceiverBuilder.build(),
          isParameterMutated,
          isParameterReturned,
          isParameterUsedAsLock);
    }
  }
}
