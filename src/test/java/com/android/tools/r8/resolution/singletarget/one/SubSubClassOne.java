// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.one;

public class SubSubClassOne extends AbstractSubClass implements IrrelevantInterfaceWithDefault {

  // Avoid SubSubClassOne.class.getCanonicalName() as it may change during shrinking.
  private static final String TAG = "SubSubClassOne";

  @Override
  public void abstractTargetAtTop() {
    System.out.println(TAG);
  }

  @Override
  public void overriddenInTwoSubTypes() {
    System.out.println(TAG);
  }

  @Override
  public void definedInTwoSubTypes() {
    System.out.println(TAG);
  }
}
