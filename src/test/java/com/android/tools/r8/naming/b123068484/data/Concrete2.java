// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b123068484.data;

public class Concrete2 extends Abs {
  Object otherField;
  public Concrete2(String x) {
    super(x);
    otherField = new Exception("Concrete2::<init>");
  }
}
