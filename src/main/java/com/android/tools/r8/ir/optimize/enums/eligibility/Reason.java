// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums.eligibility;

public class Reason {
  public static final Reason ELIGIBLE = new Reason("ELIGIBLE");
  public static final Reason ACCESSIBILITY = new Reason("ACCESSIBILITY");
  public static final Reason ANNOTATION = new Reason("ANNOTATION");
  public static final Reason PINNED = new Reason("PINNED");
  public static final Reason DOWN_CAST = new Reason("DOWN_CAST");
  public static final Reason SUBTYPES = new Reason("SUBTYPES");
  public static final Reason MANY_INSTANCE_FIELDS = new Reason("MANY_INSTANCE_FIELDS");
  public static final Reason GENERIC_INVOKE = new Reason("GENERIC_INVOKE");
  public static final Reason DEFAULT_METHOD_INVOKE = new Reason("DEFAULT_METHOD_INVOKE");
  public static final Reason UNEXPECTED_STATIC_FIELD = new Reason("UNEXPECTED_STATIC_FIELD");
  public static final Reason UNRESOLVABLE_FIELD = new Reason("UNRESOLVABLE_FIELD");
  public static final Reason CONST_CLASS = new Reason("CONST_CLASS");
  public static final Reason INVALID_PHI = new Reason("INVALID_PHI");
  public static final Reason NO_INIT = new Reason("NO_INIT");
  public static final Reason INVALID_INIT = new Reason("INVALID_INIT");
  public static final Reason INVALID_CLINIT = new Reason("INVALID_CLINIT");
  public static final Reason INVALID_INVOKE = new Reason("INVALID_INVOKE");
  public static final Reason INVALID_INVOKE_ON_ARRAY = new Reason("INVALID_INVOKE_ON_ARRAY");
  public static final Reason IMPLICIT_UP_CAST_IN_RETURN = new Reason("IMPLICIT_UP_CAST_IN_RETURN");
  public static final Reason UNSUPPORTED_LIBRARY_CALL = new Reason("UNSUPPORTED_LIBRARY_CALL");
  public static final Reason MISSING_INSTANCE_FIELD_DATA =
      new Reason("MISSING_INSTANCE_FIELD_DATA");
  public static final Reason INVALID_FIELD_PUT = new Reason("INVALID_FIELD_PUT");
  public static final Reason INVALID_ARRAY_PUT = new Reason("INVALID_ARRAY_PUT");
  public static final Reason TYPE_MISMATCH_FIELD_PUT = new Reason("TYPE_MISMATCH_FIELD_PUT");
  public static final Reason INVALID_IF_TYPES = new Reason("INVALID_IF_TYPES");
  public static final Reason ENUM_METHOD_CALLED_WITH_NULL_RECEIVER =
      new Reason("ENUM_METHOD_CALLED_WITH_NULL_RECEIVER");
  public static final Reason OTHER_UNSUPPORTED_INSTRUCTION =
      new Reason("OTHER_UNSUPPORTED_INSTRUCTION");

  private final String message;

  public Reason(String message) {
    this.message = message;
  }

  @Override
  public String toString() {
    return message;
  }
}
