// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.three;

public abstract class ThirdAbstractTopClass {

  public abstract void abstractMethod();

  public void instanceMethod() {
    System.out.println(ThirdAbstractTopClass.class.getCanonicalName());
  }

  public void otherInstanceMethod() {
    System.out.println(ThirdAbstractTopClass.class.getCanonicalName());
  }
}
