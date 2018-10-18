// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodTest extends DebugTestBase {

  private static final Path JAR = DebugTestBase.DEBUGGEE_JAVA8_JAR;
  private static final String TEST_SOURCE_FILE = "DebugInterfaceMethod.java";
  private static final String INTERFACE_SOURCE_FILE = "InterfaceWithDefaultAndStaticMethods.java";

  @Parameters(name = "{0}")
  public static Collection configs() {
    return parameters()
        .add("CF", new CfDebugTestConfig(JAR))
        .add("D8", temp -> new D8DebugTestConfig().compileAndAdd(temp, JAR))
        .build();
  }

  private final DebugTestConfig config;

  public InterfaceMethodTest(String name, DelayedDebugTestConfig delayedConfig) {
    this.config = delayedConfig.getConfig(temp);
  }

  @Test
  public void testDefaultMethod() throws Throwable {
    String debuggeeClass = "DebugInterfaceMethod";
    String parameterName = "msg";
    String localVariableName = "name";

    final String defaultMethodContainerClass;
    final String defaultMethodName;
    final String defaultMethodThisName;
    if (supportsDefaultMethod(config)) {
      defaultMethodContainerClass = "InterfaceWithDefaultAndStaticMethods";
      defaultMethodName = "doSomething";
      defaultMethodThisName = "this";
    } else {
      defaultMethodContainerClass = "InterfaceWithDefaultAndStaticMethods"
          + InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
      // IntelliJ's debugger does not know about the companion class. The only way to match it with
      // the source file or the desguared interface is to make it an inner class.
      assertEquals('$', InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX.charAt(0));
      defaultMethodName = InterfaceMethodRewriter.DEFAULT_METHOD_PREFIX + "doSomething";
      defaultMethodThisName = "-this";
    }


    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testDefaultMethod"));
    commands.add(run());
    commands.add(checkMethod(debuggeeClass, "testDefaultMethod"));
    commands.add(checkLine(TEST_SOURCE_FILE, 20));

    // Step into the default method.
    commands.add(stepInto(INTELLIJ_FILTER));
    commands.add(checkLine(INTERFACE_SOURCE_FILE, 7));
    commands.add(checkMethod(defaultMethodContainerClass, defaultMethodName));
    commands.add(checkLocal(defaultMethodThisName));
    commands.add(checkLocal(parameterName));
    commands.add(stepOver(INTELLIJ_FILTER));
    commands.add(checkLine(INTERFACE_SOURCE_FILE, 8));
    commands.add(checkMethod(defaultMethodContainerClass, defaultMethodName));
    commands.add(checkLocal(defaultMethodThisName));
    commands.add(checkLocal(parameterName));
    commands.add(checkLocal(localVariableName));
    commands.add(run());
    commands.add(run()  /* resume after 2nd breakpoint */);

    runDebugTest(config, debuggeeClass, commands);
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
    commands.add(checkLine(TEST_SOURCE_FILE, 20));
    commands.add(stepInto());
    commands.add(checkMethod("DebugInterfaceMethod$OverrideImpl", "doSomething"));
    commands.add(checkLocal("this"));
    commands.add(checkLocal(parameterName));
    commands.add(stepOver());
    commands.add(checkLocal("this"));
    commands.add(checkLocal(parameterName));
    commands.add(checkLocal(localVariableName));
    commands.add(run());

    runDebugTest(config, debuggeeClass, commands);
  }

  @Test
  public void testStaticMethod() throws Throwable {
    String debuggeeClass = "DebugInterfaceMethod";
    String parameterName = "msg";

    final String staticMethodContainerClass;
    final String staticMethodName = "printString";
    if (supportsDefaultMethod(config)) {
      staticMethodContainerClass = "InterfaceWithDefaultAndStaticMethods";
    } else {
      staticMethodContainerClass = "InterfaceWithDefaultAndStaticMethods"
          + InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
    }

    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testStaticMethod"));
    commands.add(run());
    commands.add(checkMethod(debuggeeClass, "testStaticMethod"));
    commands.add(checkLine(TEST_SOURCE_FILE, 24));

    // Step into static method.
    commands.add(stepInto());
    commands.add(checkLine(INTERFACE_SOURCE_FILE, 12));
    commands.add(checkMethod(staticMethodContainerClass, staticMethodName));
    commands.add(checkNoLocal("this"));
    commands.add(checkNoLocal("-this"));
    commands.add(checkLocal(parameterName));
    commands.add(stepOver());
    commands.add(checkLine(INTERFACE_SOURCE_FILE, 13));
    commands.add(checkLocal(parameterName));
    commands.add(run());

    runDebugTest(config, debuggeeClass, commands);
  }
}
