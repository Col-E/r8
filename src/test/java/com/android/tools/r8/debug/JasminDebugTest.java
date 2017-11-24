// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

// TODO(b/65474850) Should we build Jasmin at compile time or runtime ?
public class JasminDebugTest extends DebugTestBase {

  final String className = "UselessCheckCast";
  final String sourcefile = className + ".j";
  final String methodName = "test";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testUselessCheckcastCF() throws Throwable {
    JasminBuilder builder = getBuilderForUselessCheckcast(className, methodName);
    Path outdir = temp.newFolder().toPath();
    builder.writeClassFiles(outdir);
    CfDebugTestConfig config = new CfDebugTestConfig();
    config.addPaths(outdir);
    runUselessCheckcast(config);
  }

  @Test
  public void testUselessCheckcastD8() throws Throwable {
    JasminBuilder builder = getBuilderForUselessCheckcast(className, methodName);
    List<Path> outputs = builder.writeClassFiles(temp.newFolder().toPath());
    runUselessCheckcast(D8DebugTestConfig.fromUncompiledPaths(temp, outputs));
  }

  private void runUselessCheckcast(DebugTestConfig config) throws Throwable {
    runDebugTest(
        config,
        className,
        breakpoint(className, methodName),
        run(),
        checkLine(sourcefile, 1),
        stepOver(),
        checkLine(sourcefile, 2),
        checkLocal("local"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkNoLocal("local"),
        stepOver(),
        checkLine(sourcefile, 4),
        run());
  }

  private JasminBuilder getBuilderForUselessCheckcast(String testClassName, String testMethodName) {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(testClassName);

    clazz.addStaticMethod(testMethodName, ImmutableList.of("Ljava/lang/Object;"), "V",
        ".limit stack 2",
        ".limit locals 3",
        ".var 1 is local Ljava/lang/Object; from Label1 to Label2",
        ".line 1",
        " aload 0",
        " dup",
        " astore 1",
        " Label1:",
        ".line 2",
        " checkcast " + testClassName,
        " Label2:",
        ".line 3",
        " checkcast " + testClassName,
        ".line 4",
        "return");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        "aconst_null",
        "invokestatic " + testClassName + "/" + testMethodName + "(Ljava/lang/Object;)V",
        "return");

    return builder;
  }
}
