// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getCompanionClassNameSuffix;
import static com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting.getDefaultMethodPrefix;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringForTesting;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InterfaceMethodTest extends DebugTestBase {

  private static final Path JAR = DebugTestBase.DEBUGGEE_JAVA8_JAR;
  private static final String TEST_SOURCE_FILE = "DebugInterfaceMethod.java";
  private static final String INTERFACE_SOURCE_FILE = "InterfaceWithDefaultAndStaticMethods.java";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Parameter public TestParameters parameters;
  String debuggeeClass = "DebugInterfaceMethod";

  private boolean supportsDefaultMethods() {
    return parameters.isCfRuntime()
        || parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testDefaultMethod() throws Throwable {
    assumeFalse(
        "b/244683447: Incorrect behavior on Art 13 and 14",
        parameters.isDexRuntimeVersion(Version.V13_0_0)
            || parameters.isDexRuntimeVersion(Version.V14_0_0));
    testForRuntime(parameters)
        .addProgramFiles(JAR)
        .run(parameters.getRuntime(), debuggeeClass)
        .debugger(this::runDefaultMethod);
  }

  private void runDefaultMethod(DebugTestConfig config) throws Throwable {
    String parameterName = "msg";
    String localVariableName = "name";

    final String defaultMethodContainerClass;
    final String defaultMethodName;
    final String defaultMethodThisName;

    if (supportsDefaultMethods()) {
      defaultMethodContainerClass = "InterfaceWithDefaultAndStaticMethods";
      defaultMethodName = "doSomething";
      defaultMethodThisName = "this";
    } else {
      defaultMethodContainerClass =
          "InterfaceWithDefaultAndStaticMethods" + getCompanionClassNameSuffix();
      // IntelliJ's debugger does not know about the companion class. The only way to match it with
      // the source file or the desguared interface is to make it an inner class.
      assertEquals('$', getCompanionClassNameSuffix().charAt(0));
      defaultMethodName = getDefaultMethodPrefix() + "doSomething";
      defaultMethodThisName = "_this";
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
    testForRuntime(parameters)
        .addProgramFiles(JAR)
        .run(parameters.getRuntime(), debuggeeClass)
        .debugger(this::runOverrideDefaultMethod);
  }

  private void runOverrideDefaultMethod(DebugTestConfig config) throws Throwable {
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
    testForRuntime(parameters)
        .addProgramFiles(JAR)
        .run(parameters.getRuntime(), debuggeeClass)
        .debugger(this::runStaticMethod);
  }

  private void runStaticMethod(DebugTestConfig config) throws Throwable {
    String parameterName = "msg";

    final String staticMethodContainerClass;
    final String staticMethodName = "printString";
    if (supportsDefaultMethods()) {
      staticMethodContainerClass = "InterfaceWithDefaultAndStaticMethods";
    } else {
      staticMethodContainerClass =
          "InterfaceWithDefaultAndStaticMethods"
              + InterfaceDesugaringForTesting.getCompanionClassNameSuffix();
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
