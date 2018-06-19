// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.one;

public abstract class AbstractTopClass implements InterfaceWithDefault {

  // Avoid AbstractTopClass.class.getCanonicalName() as it may change during shrinking.
  private static final String TAG = "AbstractTopClass";

  public void singleTargetAtTop() {
    System.out.println(TAG);
  }

  public void singleShadowingOverride() {
    System.out.println(TAG);
  }

  public abstract void abstractTargetAtTop();

  public void overridenInAbstractClassOnly() {
    System.out.println(TAG);
  }

  public void overriddenInTwoSubTypes() {
    System.out.println(TAG);
  }

  public void definedInTwoSubTypes() {
    System.out.println(TAG);
  }

  public static void staticMethod() {
    System.out.println(TAG);
  }

  public void overriddenByIrrelevantInterface() {
    System.out.println(TAG);
  }
}
