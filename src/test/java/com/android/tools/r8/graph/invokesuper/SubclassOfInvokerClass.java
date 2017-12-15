// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

public class SubclassOfInvokerClass extends InvokerClass {

  public void subLevel2Method() {
    System.out.println("subLevel2Method in SubclassOfInvokerClass");
  }
}
