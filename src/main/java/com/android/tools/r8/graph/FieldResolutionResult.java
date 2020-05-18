// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.OptionalBool;

public abstract class FieldResolutionResult {

  public static FailedFieldResolutionResult failure() {
    return FailedFieldResolutionResult.INSTANCE;
  }

  public static UnknownFieldResolutionResult unknown() {
    return UnknownFieldResolutionResult.INSTANCE;
  }

  public DexEncodedField getResolvedField() {
    return null;
  }

  public abstract OptionalBool isAccessibleFrom(
      ProgramMethod context, AppInfoWithClassHierarchy appInfo);

  public boolean isSuccessfulResolution() {
    return false;
  }

  public SuccessfulFieldResolutionResult asSuccessfulResolution() {
    return null;
  }

  public boolean isFailedOrUnknownResolution() {
    return false;
  }

  public static class SuccessfulFieldResolutionResult extends FieldResolutionResult {

    private final DexClass initialResolutionHolder;
    private final DexClass resolvedHolder;
    private final DexEncodedField resolvedField;

    SuccessfulFieldResolutionResult(
        DexClass initialResolutionHolder, DexClass resolvedHolder, DexEncodedField resolvedField) {
      assert resolvedHolder.type == resolvedField.holder();
      this.initialResolutionHolder = initialResolutionHolder;
      this.resolvedHolder = resolvedHolder;
      this.resolvedField = resolvedField;
    }

    public DexClass getInitialResolutionHolder() {
      return initialResolutionHolder;
    }

    public DexClass getResolvedHolder() {
      return resolvedHolder;
    }

    @Override
    public DexEncodedField getResolvedField() {
      return resolvedField;
    }

    public DexClassAndField getResolutionPair() {
      return DexClassAndField.create(resolvedHolder, resolvedField);
    }

    @Override
    public OptionalBool isAccessibleFrom(ProgramMethod context, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isFieldAccessible(
          resolvedField, initialResolutionHolder, context.getHolder(), appInfo);
    }

    @Override
    public boolean isSuccessfulResolution() {
      return true;
    }

    @Override
    public SuccessfulFieldResolutionResult asSuccessfulResolution() {
      return this;
    }
  }

  public static class FailedFieldResolutionResult extends FieldResolutionResult {

    private static final FailedFieldResolutionResult INSTANCE = new FailedFieldResolutionResult();

    @Override
    public OptionalBool isAccessibleFrom(ProgramMethod context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isFailedOrUnknownResolution() {
      return true;
    }
  }

  /**
   * Used in D8 when trying to resolve a field that is not declared on the enclosing class of the
   * current method.
   */
  public static class UnknownFieldResolutionResult extends FieldResolutionResult {

    private static final UnknownFieldResolutionResult INSTANCE = new UnknownFieldResolutionResult();

    @Override
    public OptionalBool isAccessibleFrom(ProgramMethod context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isFailedOrUnknownResolution() {
      return true;
    }
  }
}
