// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.one;

public abstract class AbstractTopClass implements InterfaceWithDefault {

  public void singleTargetAtTop() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public void singleShadowingOverride() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public abstract void abstractTargetAtTop();

  public void overridenInAbstractClassOnly() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public void overriddenInTwoSubTypes() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public void definedInTwoSubTypes() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public static void staticMethod() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }

  public void overriddenByIrrelevantInterface() {
    System.out.println(AbstractTopClass.class.getCanonicalName());
  }
}
