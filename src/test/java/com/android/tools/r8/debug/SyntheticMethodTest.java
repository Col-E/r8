// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.classes.Bridges;
import com.android.tools.r8.debug.classes.InnerAccessors;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticMethodTest extends DebugTestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public DebugTestConfig getConfig() throws Exception {
    return testForD8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(Bridges.class, InnerAccessors.class)
        .setMinApi(parameters)
        .debugConfig(parameters.getRuntime());
  }

  @Test
  public void testInnerAccessors_NoFilter() throws Throwable {
    debugInnerAccessors(NO_FILTER);
  }

  @Test
  public void testInnerAccessors_IntelliJ() throws Throwable {
    debugInnerAccessors(INTELLIJ_FILTER);
  }

  @Test
  public void testGenericBridges_NoFilter() throws Throwable {
    debugGenericBridges(NO_FILTER);
  }

  @Test
  public void testGenericBridges_IntelliJ() throws Throwable {
    debugGenericBridges(INTELLIJ_FILTER);
  }

  private void debugInnerAccessors(StepFilter stepFilter) throws Throwable {
    final String sourceFile = "InnerAccessors.java";
    String debuggeeClass = typeName(InnerAccessors.class);
    List<Command> commands = new ArrayList<>();
    commands.add(
        breakpoint(typeName(InnerAccessors.class) + "$Inner", "callPrivateMethodInOuterClass"));
    commands.add(run());
    commands.add(checkLine(sourceFile, 15));
    commands.add(stepInto(stepFilter));  // skip synthetic accessor
    if (stepFilter == NO_FILTER) {
      commands.add(stepInto(stepFilter));
    }
    commands.add(checkMethod(debuggeeClass, "privateMethod"));
    commands.add(checkLine(sourceFile, 10));
    commands.add(run());
    runDebugTest(getConfig(), debuggeeClass, commands);
  }

  private void debugGenericBridges(StepFilter stepFilter) throws Throwable {
    final String sourceFile = "Bridges.java";
    String debuggeeClass = typeName(Bridges.class);
    List<Command> commands = new ArrayList<>();
    commands.add(breakpoint(debuggeeClass, "testGenericBridge"));
    commands.add(run());
    commands.add(checkLine(sourceFile, 23));
    commands.add(stepInto(stepFilter));  // skip synthetic accessor
    String implementationClassName = debuggeeClass + "$StringImpl";
    String methodName = "get";
    if (stepFilter == NO_FILTER) {
      commands.add(checkMethod(implementationClassName, methodName, "(Ljava/lang/Object;)V"));
      commands.add(stepInto(stepFilter));
    }
    commands.add(checkMethod(implementationClassName, methodName, "(Ljava/lang/String;)V"));
    commands.add(checkLine(sourceFile, 18));
    commands.add(run());
    runDebugTest(getConfig(), debuggeeClass, commands);
  }
}
