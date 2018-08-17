// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.jasmin.JasminBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

/**
 * Regression test to ensure that an incomplete phi creation cannot introduce a local before the
 * locals actual start. The recursive read-register in IRBuilder did not previously account for the
 * change in blocks when determining the local information of the incomplete phi.
 *
 * <p>TODO(b/65474850) Should we build Jasmin at compile time or runtime ?
 */
public class NoLocalForPhiDebugTest extends DebugTestBase {

  private static final String className = "NoLocalForPhi";
  private static final String sourcefile = className + ".j";
  private static final String methodName = "test";

  @Test
  public void testCf() throws Throwable {
    JasminBuilder builder = getBuilderForUselessCheckcast(className, methodName);
    Path outdir = temp.newFolder().toPath();
    builder.writeClassFiles(outdir);
    CfDebugTestConfig config = new CfDebugTestConfig();
    config.addPaths(outdir);
    run(config);
  }

  @Test
  public void testD8() throws Throwable {
    JasminBuilder builder = getBuilderForUselessCheckcast(className, methodName);
    List<Path> outputs = builder.writeClassFiles(temp.newFolder().toPath());
    run(new D8DebugTestConfig().compileAndAdd(temp, outputs));
  }

  private void run(DebugTestConfig config) throws Throwable {
    runDebugTest(
        config,
        className,
        breakpoint(className, methodName),
        run(),
        checkLine(sourcefile, 1),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 2),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 4),
        checkLocal("arg"),
        checkLocal("local"),
        // local == 0 => branch to line 5
        stepOver(),
        checkLine(sourcefile, 5),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 2),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 4),
        checkLocal("arg"),
        checkLocal("local"),
        // local == -1 => branch to line 6 then 7
        stepOver(),
        checkLine(sourcefile, 6),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 7),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 2),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 4),
        checkLocal("arg"),
        checkLocal("local"),
        // local == 1 => branch to line 6 then 8
        stepOver(),
        checkLine(sourcefile, 6),
        checkLocal("arg"),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 8),
        checkLocal("arg"),
        checkNoLocal("local"),
        run());
  }

  private JasminBuilder getBuilderForUselessCheckcast(String testClassName, String testMethodName) {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(testClassName);

    clazz.addStaticMethod(testMethodName, ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is arg Ljava/lang/Object; from L_init to L_end",
        ".var 1 is local I from L_local_start to L_local_end",
        "L_init:",
        ".line 1",
        "  ldc 0",
        "  istore 1",

        "L_join:", // A phi flowing to 'local' starts here.
        ".line 2",
        "  getstatic java/lang/System/out Ljava/io/PrintStream;",
        ".line 3", // An intermediate instruction to ensure the phi did not start the local.
        "  nop",

        "L_local_start:", // Delayed start of local.
        ".line 4",
        "  iload 1",
        "  invokevirtual java/io/PrintStream/println(I)V",
        "  iload 1",
        "  ifne L_b2",
        "L_local_end:", // End of local (could also just end at exit).

        "L_b1:",
        ".line 5",
        "  ldc -1",
        "  istore 1",
        "  goto L_join", // Back branch to ensure an incomplete phi is created in L_join.

        "L_b2:",
        ".line 6",
        "  iload 1",
        "  ifgt L_b3",

        ".line 7",
        "  ldc 1",
        "  istore 1",
        "  goto L_join", // Back branch to ensure an incomplete phi is created in L_join.

        "L_b3:",
        ".line 8",
        "return",
        "L_end:");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "aconst_null",
        "invokestatic " + testClassName + "/" + testMethodName + "(Ljava/lang/Object;)V",
        "return");

    return builder;
  }
}
