// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import org.apache.harmony.jpda.tests.framework.jdwp.Value;
import org.junit.Test;

public class ClassInitializationTest extends DebugTestBase {

  @Test
  public void testStaticAssingmentInitialization() throws Throwable {
    final String SOURCE_FILE = "ClassInitializerAssignmentInitialization.java";
    final String CLASS = "ClassInitializerAssignmentInitialization";

    runDebugTest(CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(SOURCE_FILE, 7),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        checkStaticFieldClinitSafe(CLASS, "z", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 10),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        checkStaticFieldClinitSafe(CLASS, "z", null, Value.createInt(0)),
        breakpoint(CLASS, "main"),
        run(),
        checkStaticField(CLASS, "x", null, Value.createInt(1)),
        checkStaticField(CLASS, "y", null, Value.createInt(0)),
        checkStaticField(CLASS, "z", null, Value.createInt(2)),
        run());
  }

  @Test
  public void testBreakpointInEmptyClassInitializer() throws Throwable {
    final String SOURCE_FILE = "ClassInitializerEmpty.java";
    final String CLASS = "ClassInitializerEmpty";

    runDebugTest(CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(SOURCE_FILE, 8),
        run());
  }

  @Test
  public void testStaticBlockInitialization() throws Throwable {
    final String SOURCE_FILE = "ClassInitializerStaticBlockInitialization.java";
    final String CLASS = "ClassInitializerStaticBlockInitialization";

    runDebugTest(CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(SOURCE_FILE, 12),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 13),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
        stepOver(),
        checkLine(SOURCE_FILE, 14),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
        stepOver(),
        checkLine(SOURCE_FILE, 17),
        stepOver(),
        checkLine(SOURCE_FILE, 19),
        breakpoint(CLASS, "main"),
        run(),
        checkLine(SOURCE_FILE, 23),
        checkStaticField(CLASS, "x", null, Value.createInt(3)),
        run());
  }

  @Test
  public void testStaticMixedInitialization() throws Throwable {
    final String SOURCE_FILE = "ClassInitializerMixedInitialization.java";
    final String CLASS = "ClassInitializerMixedInitialization";

    runDebugTest(CLASS,
        breakpoint(CLASS, "<clinit>"),
        run(),
        checkLine(SOURCE_FILE, 8),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(0)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 12),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(1)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 13),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 16),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(0)),
        stepOver(),
        checkLine(SOURCE_FILE, 18),
        checkStaticFieldClinitSafe(CLASS, "x", null, Value.createInt(2)),
        checkStaticFieldClinitSafe(CLASS, "y", null, Value.createInt(2)),
        breakpoint(CLASS, "main"),
        run(),
        checkLine(SOURCE_FILE, 22),
        checkStaticField(CLASS, "x", null, Value.createInt(3)),
        checkStaticField(CLASS, "y", null, Value.createInt(2)),
        run());
  }
}
