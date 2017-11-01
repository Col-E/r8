// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.CompilationMode;
import java.util.Collections;
import org.junit.BeforeClass;
import org.junit.Test;

/** Tests source file and line numbers on inlined methods. */
public class DebugInfoWhenInliningTest extends DebugTestBase {

  public static final String SOURCE_FILE = "Inlining1.java";
  private static DebuggeePath debuggeePath;

  @BeforeClass
  public static void initDebuggeePath() throws Exception {
    debuggeePath =
        DebuggeePath.makeDex(
            compileToDexViaR8(
                null,
                null,
                DEBUGGEE_JAR,
                Collections.<String>emptyList(),
                true,
                CompilationMode.RELEASE));
  }

  @Test
  public void testEachLine() throws Throwable {
    final String className = "Inlining1";
    final String methodName = "main";
    final String signature = "([Ljava/lang/String;)V";
    runDebugTest(
        debuggeePath,
        className,
        breakpoint(className, methodName, signature),
        run(),
        checkMethod(className, methodName, signature),
        // TODO(tamaskenez) to be continued as the feature is implemented in class Inliner
        run());
  }
}
