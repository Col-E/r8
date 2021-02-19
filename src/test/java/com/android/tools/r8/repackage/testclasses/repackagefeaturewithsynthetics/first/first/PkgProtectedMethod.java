// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.repackage.testclasses.repackagefeaturewithsynthetics.first.first;

import java.io.PrintStream;

public class PkgProtectedMethod {

  /* package protected */
  static PrintStream getStream() {
    return System.out;
  }
}
