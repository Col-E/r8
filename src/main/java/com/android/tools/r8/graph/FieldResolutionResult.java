// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.utils.OptionalBool;

public abstract class FieldResolutionResult
    extends MemberResolutionResult<DexEncodedField, DexField> {

  public static FailedFieldResolutionResult failure() {
    return FailedFieldResolutionResult.INSTANCE;
  }

  public static UnknownFieldResolutionResult unknown() {
    return UnknownFieldResolutionResult.INSTANCE;
  }

  public DexEncodedField getResolvedField() {
    return null;
  }

  public DexField getResolvedFieldReference() {
    return null;
  }

  public boolean isSuccessfulResolution() {
    return false;
  }

  public SuccessfulFieldResolutionResult asSuccessfulResolution() {
    return null;
  }

  @Override
  public boolean isSuccessfulMemberResolutionResult() {
    return false;
  }

  @Override
  public SuccessfulFieldResolutionResult asSuccessfulMemberResolutionResult() {
    return null;
  }

  public boolean isFailedOrUnknownResolution() {
    return false;
  }

  public DexClass getInitialResolutionHolder() {
    return null;
  }

  public static class SuccessfulFieldResolutionResult extends FieldResolutionResult
      implements SuccessfulMemberResolutionResult<DexEncodedField, DexField> {

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

    @Override
    public DexClass getInitialResolutionHolder() {
      return initialResolutionHolder;
    }

    @Override
    public DexClass getResolvedHolder() {
      return resolvedHolder;
    }

    @Override
    public DexEncodedField getResolvedField() {
      return resolvedField;
    }

    @Override
    public DexField getResolvedFieldReference() {
      return resolvedField.getReference();
    }

    @Override
    public DexEncodedField getResolvedMember() {
      return resolvedField;
    }

    @Override
    public DexClassAndField getResolutionPair() {
      return DexClassAndField.create(resolvedHolder, resolvedField);
    }

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return AccessControl.isMemberAccessible(this, context, appInfo);
    }

    @Override
    public boolean isSuccessfulResolution() {
      return true;
    }

    @Override
    public SuccessfulFieldResolutionResult asSuccessfulResolution() {
      return this;
    }

    @Override
    public boolean isSuccessfulMemberResolutionResult() {
      return true;
    }

    @Override
    public SuccessfulFieldResolutionResult asSuccessfulMemberResolutionResult() {
      return this;
    }
  }

  public static class FailedFieldResolutionResult extends FieldResolutionResult {

    private static final FailedFieldResolutionResult INSTANCE = new FailedFieldResolutionResult();

    @Override
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
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
    public OptionalBool isAccessibleFrom(
        ProgramDefinition context, AppInfoWithClassHierarchy appInfo) {
      return OptionalBool.FALSE;
    }

    @Override
    public boolean isFailedOrUnknownResolution() {
      return true;
    }
  }
}
