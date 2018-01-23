// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

public class BreakPointEventsTest {

  private static int bValue = 1;

  private static int b() {
    return bValue;
  }

  public static int singleLineDeclarations() {
    { int x = b(); int y = b(); int z = b(); }
    bValue = 2;
    int x = b(); int y = b(); int z = b(); return x + y + z;
  }

  public static void main(String[] args) {
    singleLineDeclarations();
  }
}
