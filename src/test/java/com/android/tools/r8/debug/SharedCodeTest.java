// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.junit.Test;

public class SharedCodeTest extends DebugTestBase {

  public static final String CLASS = "SharedCode";
  public static final String FILE = "SharedCode.java";

  @Test
  public void testSharedIf() throws Throwable {
    final String methodName = "sharedIf";
    runDebugTest(CLASS,
        breakpoint(CLASS, methodName),
        run(),
        checkMethod(CLASS, methodName),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 9),
        stepOver(),
        checkLine(FILE, 13),
        run(),
        checkMethod(CLASS, methodName),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 13),
        run());
  }

}
