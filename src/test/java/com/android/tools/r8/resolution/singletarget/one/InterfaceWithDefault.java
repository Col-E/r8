// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.one;

public interface InterfaceWithDefault {

  // Avoid InterfaceWithDefault.class.getCanonicalName() as it may change during shrinking.
  String TAG = "InterfaceWithDefault";

  default void defaultMethod() {
    System.out.println(TAG);
  }

  default void overriddenDefault() {
    System.out.println(TAG);
  }

  default void overriddenInOtherInterface() {
    System.out.println(TAG);
  }
}
