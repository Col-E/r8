// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests local variable information. */
@RunWith(Parameterized.class)
public class MinificationTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Minified.java";

  @Parameterized.Parameters(name = "minification: {0}, proguardMap: {1}")
  public static Collection minificationControl() {
    return Arrays.asList(new Object[][] {{false, false}, {true, false}, {true, true}});
  }

  private static boolean firstRun = true;
  private static boolean minificationEnabled;

  public MinificationTest(boolean enableMinification, boolean writeProguardMap) throws Exception {
    // TODO(tamaskenez) The way we're shadowing and calling the static setUp() methods should be
    // updated when we refactor DebugTestBase.
    if (firstRun
        || this.minificationEnabled != enableMinification
        || this.writeProguardMap != writeProguardMap) {

      firstRun = false;
      this.minificationEnabled = enableMinification;
      this.writeProguardMap = writeProguardMap;

      if (minificationEnabled) {
        proguardConfigurations =
            ImmutableList.of(
                "-keep public class Minified { public static void main(java.lang.String[]); }");
        setUp(
            null,
            pg -> {
              pg.addKeepAttributePatterns(ImmutableList.of("SourceFile", "LineNumberTable"));
            });
      } else {
        setUp(null, null);
      }
    }
  }

  @BeforeClass
  public static void setUp() throws Exception {}

  @Test
  public void testBreakInMainClass() throws Throwable {
    boolean minifiedNames = (minificationEnabled && !writeProguardMap);
    final String className = "Minified";
    final String methodName = minifiedNames ? "a" : "test";
    final String signature = "()V";
    final String innerClassName = minifiedNames ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames ? "a" : "innerTest";
    final String innerSignature = "()I";
    List<Command> commands =
        new ArrayList<>(
            Arrays.asList(
                breakpoint(className, methodName, signature),
                run(),
                checkMethod(className, methodName, signature),
                checkLine(SOURCE_FILE, 14),
                stepOver(),
                checkMethod(className, methodName, signature),
                checkLine(SOURCE_FILE, 15),
                stepInto()));
    // Dalvik first enters ClassLoader, step over it.
    // See also b/67225390.
    if (ToolHelper.getDexVm() == DexVm.ART_4_4_4_HOST) {
      commands.add(stepOver());
    }
    commands.addAll(
        Arrays.asList(
            checkMethod(innerClassName, innerMethodName, innerSignature),
            checkLine(SOURCE_FILE, 8),
            run()));
    runDebugTestR8(className, commands);
  }

  @Test
  public void testBreakInPossiblyRenamedClass() throws Throwable {
    boolean minifiedNames = (minificationEnabled && !writeProguardMap);
    final String className = "Minified";
    final String innerClassName = minifiedNames ? "a" : "Minified$Inner";
    final String innerMethodName = minifiedNames ? "a" : "innerTest";
    final String innerSignature = "()I";
    runDebugTestR8(
        className,
        breakpoint(innerClassName, innerMethodName, innerSignature),
        run(),
        checkMethod(innerClassName, innerMethodName, innerSignature),
        checkLine(SOURCE_FILE, 8),
        run());
  }
}
