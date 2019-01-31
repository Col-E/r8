// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b123068484.data;

// Hide the strField via class-level access modifier.
abstract class Abs extends PublicAbs {
  public final String strField;

  Abs(String x) {
    strField = x;
  }
}
