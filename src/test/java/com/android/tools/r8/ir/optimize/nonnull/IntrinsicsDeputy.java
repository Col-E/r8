// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.nonnull;

import com.android.tools.r8.NeverInline;

public class IntrinsicsDeputy {

  String name;

  IntrinsicsDeputy(String name){
    this.name = name;
  }

  @NeverInline
  public static IntrinsicsDeputy getInstance() {
    // Trick to ensure that R8 cannot conclude that this method always returns null.
    return System.currentTimeMillis() < 0 ? new IntrinsicsDeputy(null) : null;
  }

  @Override
  public String toString() {
    return name;
  }

  static void checkParameterIsNotNull(Object object, String paramName) {
    if (object == null) {
      throwParameterIsNullException(paramName);
    }
  }

  static void checkParameterIsNotNullDifferently(Object object, String paramName) {
    if (object != null) {
      return;
    }
    throwParameterIsNullException(paramName);
  }

  @NeverInline
  static void throwParameterIsNullException(String paramName) {
    throw new NullPointerException(paramName);
  }

  @NeverInline
  void selfCheck() {
    // If invoked, `this` is not null.
    checkParameterIsNotNull(this, "self");
    // Hence, live code below.
    System.out.println(this);
  }

  @NeverInline
  static void checkNull() {
    IntrinsicsDeputy nullObject = getInstance();
    checkParameterIsNotNullDifferently(nullObject, "nullObject");
    // Dead code below.
    System.out.println(nullObject);
  }

  @NeverInline
  static void nonNullAfterParamCheck(IntrinsicsDeputy arg) {
    checkParameterIsNotNull(arg, "arg");
    if (arg != null) {
      System.out.println(arg.toString());
    } else {
      throw new IllegalArgumentException("arg != null");
    }
  }

  @NeverInline
  static void nonNullAfterParamCheckDifferently(IntrinsicsDeputy arg) {
    checkParameterIsNotNullDifferently(arg, "arg");
    if (arg == null) {
      throw new IllegalArgumentException("arg != null");
    } else {
      System.out.println(arg.toString());
    }
  }

  public static void main(String[] args) {
    IntrinsicsDeputy instance = new IntrinsicsDeputy("INTRINSICS");
    instance.selfCheck();

    try {
      checkNull();
    } catch (NullPointerException npe) {
      // Expected
    }

    nonNullAfterParamCheck(instance);
    try {
      nonNullAfterParamCheck(null);
    } catch (NullPointerException npe) {
      // Expected
    }

    nonNullAfterParamCheckDifferently(instance);
    try {
      nonNullAfterParamCheckDifferently(null);
    } catch (NullPointerException npe) {
      // Expected
    }

    // To prevent those utils from being force inlined.
    checkParameterIsNotNull(instance, "instance");
    checkParameterIsNotNullDifferently(instance, "instance");
  }

}
