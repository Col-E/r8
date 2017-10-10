// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import org.junit.Test;

/** Test single stepping behaviour of synchronized blocks. */
public class FinallyBlockTest extends DebugTestBase {

  public static final String CLASS = "FinallyBlock";
  public static final String FILE = "FinallyBlock.java";

  @Test
  public void testEmptyBlock() throws Throwable {
    final String method = "finallyBlock";
    runDebugTest(CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 20),
        stepOver(),
        checkLine(FILE, 25), // return in callFinallyBlock
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 13), // catch AE
        stepOver(),
        checkLine(FILE, 14),
        stepOver(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 20),
        stepOver(),
        checkLine(FILE, 25), // return in callFinallyBlock
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 11),
        stepOver(),
        checkLine(FILE, 15), // catch RE
        stepOver(),
        checkLine(FILE, 16),
        stepOver(),
        checkLine(FILE, 18),
        stepOver(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 20),
        stepOver(),
        checkLine(FILE, 25), // return in callFinallyBlock
        run(),
        checkLine(FILE, 8),
        stepOver(),
        checkLine(FILE, 10),
        stepOver(),
        checkLine(FILE, 11), // throw without catch
        stepOver(),
        checkLine(FILE, 18), // finally
        stepOver(),
        checkLine(FILE, 26), // catch in callFinallyBlock
        run());
  }
}
