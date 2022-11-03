// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.eligibility;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.google.common.collect.ImmutableList;

public abstract class Reason {
  public static final Reason ELIGIBLE = new StringReason("ELIGIBLE");
  public static final Reason ACCESSIBILITY = new StringReason("ACCESSIBILITY");
  public static final Reason ANNOTATION = new StringReason("ANNOTATION");
  public static final Reason PINNED = new StringReason("PINNED");
  public static final Reason DOWN_CAST = new StringReason("DOWN_CAST");
  public static final Reason SUBTYPES = new StringReason("SUBTYPES");
  public static final Reason MANY_INSTANCE_FIELDS = new StringReason("MANY_INSTANCE_FIELDS");
  public static final Reason DEFAULT_METHOD_INVOKE = new StringReason("DEFAULT_METHOD_INVOKE");
  public static final Reason UNRESOLVABLE_FIELD = new StringReason("UNRESOLVABLE_FIELD");
  public static final Reason CONST_CLASS = new StringReason("CONST_CLASS");
  public static final Reason INVALID_PHI = new StringReason("INVALID_PHI");
  public static final Reason NO_INIT = new StringReason("NO_INIT");
  public static final Reason INVALID_INIT = new StringReason("INVALID_INIT");
  public static final Reason INVALID_CLINIT = new StringReason("INVALID_CLINIT");
  public static final Reason INVALID_INVOKE = new StringReason("INVALID_INVOKE");
  public static final Reason INVALID_INVOKE_CLASSPATH =
      new StringReason("INVALID_INVOKE_CLASSPATH");
  public static final Reason INVALID_INVOKE_CUSTOM = new StringReason("INVALID_INVOKE_CUSTOM");
  public static final Reason INVALID_INVOKE_ON_ARRAY = new StringReason("INVALID_INVOKE_ON_ARRAY");
  public static final Reason IMPLICIT_UP_CAST_IN_RETURN =
      new StringReason("IMPLICIT_UP_CAST_IN_RETURN");
  public static final Reason INVALID_FIELD_PUT = new StringReason("INVALID_FIELD_PUT");
  public static final Reason INVALID_ARRAY_PUT = new StringReason("INVALID_ARRAY_PUT");
  public static final Reason INVALID_INVOKE_NEW_ARRAY =
      new StringReason("INVALID_INVOKE_NEW_ARRAY");
  public static final Reason TYPE_MISMATCH_FIELD_PUT = new StringReason("TYPE_MISMATCH_FIELD_PUT");
  public static final Reason INVALID_IF_TYPES = new StringReason("INVALID_IF_TYPES");
  public static final Reason ASSIGNMENT_OUTSIDE_INIT = new StringReason("ASSIGNMENT_OUTSIDE_INIT");
  public static final Reason ENUM_METHOD_CALLED_WITH_NULL_RECEIVER =
      new StringReason("ENUM_METHOD_CALLED_WITH_NULL_RECEIVER");
  public static final Reason OTHER_UNSUPPORTED_INSTRUCTION =
      new StringReason("OTHER_UNSUPPORTED_INSTRUCTION");

  public abstract Object getKind();

  @Override
  public abstract String toString();

  public static class StringReason extends Reason {

    private final String message;

    public StringReason(String message) {
      this.message = message;
    }

    @Override
    public Object getKind() {
      return this;
    }

    @Override
    public String toString() {
      return message;
    }
  }

  public static class IllegalInvokeWithImpreciseParameterTypeReason extends Reason {

    private final DexMethod invokedMethod;

    public IllegalInvokeWithImpreciseParameterTypeReason(DexMethod invokedMethod) {
      this.invokedMethod = invokedMethod;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "IllegalInvokeWithImpreciseParameterType(" + invokedMethod.toSourceString() + ")";
    }
  }

  public static class MissingEnumStaticFieldValuesReason extends Reason {

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "MissingEnumStaticFieldValues";
    }
  }

  public static class MissingContentsForEnumValuesArrayReason extends Reason {

    private final DexField valuesField;

    public MissingContentsForEnumValuesArrayReason(DexField valuesField) {
      this.valuesField = valuesField;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "MissingContentsForEnumValuesArray(" + valuesField.toSourceString() + ")";
    }
  }

  public static class MissingInstanceFieldValueForEnumInstanceReason extends Reason {

    private final DexField enumField;
    private final int ordinal;
    private final DexField instanceField;

    public MissingInstanceFieldValueForEnumInstanceReason(DexField instanceField) {
      this.enumField = null;
      this.ordinal = -1;
      this.instanceField = instanceField;
    }

    public MissingInstanceFieldValueForEnumInstanceReason(
        DexField instanceField, DexField enumField) {
      this.enumField = enumField;
      this.ordinal = -1;
      this.instanceField = instanceField;
    }

    public MissingInstanceFieldValueForEnumInstanceReason(DexField instanceField, int ordinal) {
      this.enumField = null;
      this.ordinal = ordinal;
      this.instanceField = instanceField;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      if (enumField != null) {
        return "MissingInstanceFieldValueForEnumInstance(enum field="
            + enumField.toSourceString()
            + ", instance field="
            + instanceField.toSourceString()
            + ")";
      }
      if (ordinal == -1) {
        return "MissingInstanceFieldValueForEnumInstance(Cannot resolve instance field="
            + instanceField.toSourceString()
            + ")";
      }
      assert ordinal >= 0;
      return "MissingInstanceFieldValueForEnumInstance(ordinal="
          + ordinal
          + ", instance field="
          + instanceField.toSourceString()
          + ")";
    }
  }

  public static class MissingObjectStateForEnumInstanceReason extends Reason {

    private final DexField enumField;

    public MissingObjectStateForEnumInstanceReason(DexField enumField) {
      this.enumField = enumField;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "MissingObjectStateForEnumInstance(" + enumField + ")";
    }
  }

  public static class UnsupportedInstanceFieldValueForEnumInstanceReason extends Reason {

    private final int ordinal;
    private final DexField instanceField;

    public UnsupportedInstanceFieldValueForEnumInstanceReason(int ordinal, DexField instanceField) {
      this.ordinal = ordinal;
      this.instanceField = instanceField;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "UnsupportedInstanceFieldValueForEnumInstance(ordinal="
          + ordinal
          + ", instance field="
          + instanceField.toSourceString()
          + ")";
    }
  }

  public static class UnsupportedLibraryInvokeReason extends Reason {

    private final DexMethod invokedMethod;

    public UnsupportedLibraryInvokeReason(DexMethod invokedMethod) {
      this.invokedMethod = invokedMethod;
    }

    @Override
    public Object getKind() {
      return ImmutableList.of(getClass(), invokedMethod);
    }

    @Override
    public String toString() {
      return "UnsupportedLibraryInvoke(" + invokedMethod.toSourceString() + ")";
    }
  }

  public static class UnsupportedStaticFieldReason extends Reason {

    private final DexField field;

    public UnsupportedStaticFieldReason(DexField field) {
      this.field = field;
    }

    @Override
    public Object getKind() {
      return getClass();
    }

    @Override
    public String toString() {
      return "UnsupportedStaticField(" + field.toSourceString() + ")";
    }
  }
}
