// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Test to check that locals that are introduced in a block that is not hit, still start in the
 * block where they first become visible.
 *
 * <p>See b/75251251 or b/78617758
 */
public class LocalsLiveAtBlockEntryDebugTest extends DebugTestBase {

  final String className = "LocalsLiveAtEntry";
  final String sourcefile = className + ".j";
  final String methodName = "test";

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void testCF() throws Throwable {
    JasminBuilder builder = getBuilderForTest(className, methodName);
    Path outdir = temp.newFolder().toPath();
    builder.writeClassFiles(outdir);
    CfDebugTestConfig config = new CfDebugTestConfig();
    config.addPaths(outdir);
    runTest(config);
  }

  @Test
  @Ignore("b/78617758")
  public void testD8() throws Throwable {
    JasminBuilder builder = getBuilderForTest(className, methodName);
    List<Path> outputs = builder.writeClassFiles(temp.newFolder().toPath());
    runTest(new D8DebugTestConfig().compileAndAdd(temp, outputs));
  }

  private void runTest(DebugTestConfig config) throws Throwable {
    DexInspector inspector =
        new DexInspector(
            (config instanceof CfDebugTestConfig)
                ? Collections.singletonList(config.getPaths().get(1).resolve(className + ".class"))
                : config.getPaths());
    ClassSubject clazz = inspector.clazz(className);
    MethodSubject method = clazz.method("void", methodName, ImmutableList.of("java.lang.Object"));
    assertTrue(method.isPresent());
    runDebugTest(
        config,
        className,
        breakpoint(className, methodName),
        run(),
        checkLine(sourcefile, 1),
        checkNoLocal("obj"),
        stepOver(),
        checkLine(sourcefile, 3),
        checkLocal("obj"),
        stepOver(),
        checkLine(sourcefile, 100),
        run());
  }

  private JasminBuilder getBuilderForTest(String testClassName, String testMethodName) {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass(testClassName);

    clazz.addStaticMethod(
        testMethodName,
        ImmutableList.of("Ljava/lang/Object;"),
        "V",
        ".limit stack 2",
        ".limit locals 3",
        ".var 0 is obj L" + testClassName + "; from L1 to L3",
        "L0:", // Preamble code that does not have any live locals (eg, no formals too!).
        ".line 1",
        " ldc 42",
        " lookupswitch",
        "   0: L1",
        "   default: L2",
        "L1:", // late introduction of formals.
        ".line 2",
        " aconst_null",
        " pop",
        "L2:", // target block with first visible location of locals.
        ".line 3",
        " aconst_null",
        " pop",
        " return",
        "L3:");

    clazz.addMainMethod(
        ".limit stack 2",
        ".limit locals 1",
        ".line 100",
        "aconst_null",
        "invokestatic " + testClassName + "/" + testMethodName + "(Ljava/lang/Object;)V",
        "return");

    return builder;
  }
}
