// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class InterfaceMethodTest extends DebugTestBase {

  private static final String SOURCE_FILE = "DebugInterfaceMethod.java";
  private static final boolean RUN_JAVA = false;

  @Test
  public void testDefaultMethod() throws Throwable {
    String debuggeeClass = "DebugInterfaceMethod";
    String parameterName = "msg";
    String localVariableName = "name";

    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testDefaultMethod"));
    commands.add(run());
    commands.add(checkMethod(debuggeeClass, "testDefaultMethod"));
    commands.add(checkLine(SOURCE_FILE, 31));
    if (!supportsDefaultMethod(RUN_JAVA)) {
      // We desugared default method. This means we're going to step through an extra (forward)
      // method first.
      commands.add(stepInto(INTELLIJ_FILTER));
    }
    commands.add(stepInto(INTELLIJ_FILTER));
    commands.add(checkLine(SOURCE_FILE, 9));
    // TODO(shertz) we should see the local variable this even when desugaring.
    if (supportsDefaultMethod(RUN_JAVA)) {
      commands.add(checkLocal("this"));
    }
    commands.add(checkLocal(parameterName));
    commands.add(stepOver(INTELLIJ_FILTER));
    commands.add(checkLocal(parameterName));
    commands.add(checkLocal(localVariableName));
    // TODO(shertz) check current method name ?
    commands.add(run());
    commands.add(run()  /* resume after 2nd breakpoint */);

    runDebugTest(getDebuggeeJava8DexD8OrCf(RUN_JAVA), debuggeeClass, commands);
  }

  @Test
  public void testOverrideDefaultMethod() throws Throwable {
    String debuggeeClass = "DebugInterfaceMethod";
    String parameterName = "msg";
    String localVariableName = "newMsg";

    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testDefaultMethod"));
    commands.add(run());
    commands.add(run() /* resume after 1st breakpoint */);
    commands.add(checkMethod(debuggeeClass, "testDefaultMethod"));
    commands.add(checkLine(SOURCE_FILE, 31));
    commands.add(stepInto());
    commands.add(checkMethod("DebugInterfaceMethod$OverrideImpl", "doSomething"));
    commands.add(checkLocal("this"));
    commands.add(checkLocal(parameterName));
    commands.add(stepOver());
    commands.add(checkLocal("this"));
    commands.add(checkLocal(parameterName));
    commands.add(checkLocal(localVariableName));
    commands.add(run());

    runDebugTest(getDebuggeeJava8DexD8OrCf(RUN_JAVA), debuggeeClass, commands);
  }

  @Test
  public void testStaticMethod() throws Throwable {
    String debuggeeClass = "DebugInterfaceMethod";
    String parameterName = "msg";

    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testStaticMethod"));
    commands.add(run());
    commands.add(checkMethod(debuggeeClass, "testStaticMethod"));
    commands.add(checkLine(SOURCE_FILE, 35));
    commands.add(stepInto());
    commands.add(checkLocal(parameterName));
    commands.add(stepOver());
    commands.add(checkLocal(parameterName));
    commands.add(run());

    runDebugTest(getDebuggeeJava8DexD8OrCf(RUN_JAVA), debuggeeClass, commands);
  }
}
