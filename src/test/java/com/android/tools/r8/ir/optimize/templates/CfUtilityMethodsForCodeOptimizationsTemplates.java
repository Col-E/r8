// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.templates;

public class CfUtilityMethodsForCodeOptimizationsTemplates {

  public static void toStringIfNotNull(Object o) {
    if (o != null) {
      o.toString();
    }
  }

  public static void throwClassCastExceptionIfNotNull(Object o) {
    if (o != null) {
      throw new ClassCastException();
    }
  }

  public static IllegalAccessError throwIllegalAccessError() {
    throw new IllegalAccessError();
  }

  public static IncompatibleClassChangeError throwIncompatibleClassChangeError() {
    throw new IncompatibleClassChangeError();
  }

  public static NoSuchMethodError throwNoSuchMethodError() {
    throw new NoSuchMethodError();
  }

  public static RuntimeException throwRuntimeExceptionWithMessage(String message) {
    throw new RuntimeException(message);
  }
}
