// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.examples.invokeempty;

public class ClassA {

  // This class has no constructor, by design.

  public void aMethod() {
    System.out.print("A");
  }
}
