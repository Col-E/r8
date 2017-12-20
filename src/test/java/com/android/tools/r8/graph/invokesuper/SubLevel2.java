// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

public class SubLevel2 extends SubLevel1 {

  @Override
  public void superMethod() {
    System.out.println("superMethod in SubLevel2");
  }

  @Override
  public void subLevel1Method() {
    System.out.println("subLevel1Method in SubLevel2");
  }

  public void subLevel2Method() {
    System.out.println("subLevel2Method in SubLevel2");
  }

  public void otherSuperMethod() {
    System.out.println("otherSuperMethod in SubLevel2");
  }

  public void callOtherSuperMethodIndirect() {
    callOtherSuperMethod();
  }
}
