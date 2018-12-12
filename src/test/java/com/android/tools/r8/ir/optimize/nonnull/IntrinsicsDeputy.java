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
    return System.currentTimeMillis() > 0 ? new IntrinsicsDeputy(null) : null;
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
    IntrinsicsDeputy maybeNull = getInstance();
    checkParameterIsNotNullDifferently(maybeNull, "maybeNull");
    // After the check, it's not null, hence live.
    System.out.println(maybeNull);
  }

  @NeverInline
  static void nonNullAfterParamCheck() {
    IntrinsicsDeputy maybeNull = getInstance();
    checkParameterIsNotNull(maybeNull, "maybeNull");
    if (maybeNull != null) {
      System.out.println(maybeNull.toString());
    } else {
      throw new IllegalArgumentException("maybeNull != null");
    }
  }

  @NeverInline
  static void nonNullAfterParamCheckDifferently() {
    IntrinsicsDeputy maybeNull = getInstance();
    checkParameterIsNotNullDifferently(maybeNull, "maybeNull");
    if (maybeNull == null) {
      throw new IllegalArgumentException("maybeNull != null");
    } else {
      System.out.println(maybeNull.toString());
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

    nonNullAfterParamCheck();
    nonNullAfterParamCheckDifferently();

    // To prevent those utils from being force inlined.
    checkParameterIsNotNull(instance, "instance");
    checkParameterIsNotNullDifferently(instance, "instance");
  }

}
