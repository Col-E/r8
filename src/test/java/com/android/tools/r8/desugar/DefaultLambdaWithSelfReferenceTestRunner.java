// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.Disassemble;
import com.android.tools.r8.Disassemble.DisassembleCommand;
import com.android.tools.r8.JvmTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.Command;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DefaultLambdaWithSelfReferenceTestRunner extends DebugTestBase {

  final Class<?> CLASS = DefaultLambdaWithSelfReferenceTest.class;
  final String EXPECTED = StringUtils.lines("stateful(stateless)");

  private void runDebugger(DebugTestConfig config) throws Throwable {
    MethodReference main = Reference.methodFromMethod(CLASS.getMethod("main", String[].class));
    Command checkThis = conditional((state) ->
        state.isCfRuntime()
            ? Collections.singletonList(checkLocal("this"))
            : ImmutableList.of(
                checkNoLocal("this"),
                checkLocal("_this")));

    runDebugTest(config, CLASS,
        breakpoint(main, 26),
        run(),
        checkLine(26),
        stepInto(INTELLIJ_FILTER),
        checkLine(16),
        // When desugaring, the InterfaceProcessor makes this static on the companion class.
        checkThis,
        breakpoint(main, 27),
        run(),
        checkLine(27),
        stepInto(INTELLIJ_FILTER),
        checkLine(17),
        // When desugaring, the LambdaClass will change this to a static (later moved to companion).
        checkThis,
        run());
  }

  @Test
  public void testJvm() throws Throwable {
    JvmTestBuilder builder = testForJvm().addTestClasspath();
    builder.run(CLASS).assertSuccessWithOutput(EXPECTED);
    runDebugger(builder.debugConfig());
  }

  @Test
  public void testR8Cf() throws Throwable {
    R8TestCompileResult compileResult = testForR8(Backend.CF)
        .addProgramClassesAndInnerClasses(CLASS)
        .noMinification()
        .noTreeShaking()
        .debug()
        .compile();
    compileResult
        // TODO(b/123506120): Add .assertNoMessages()
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(CLASS), isPresent()));
    runDebugger(compileResult.debugConfig());
  }

  @Test
  public void testD8() throws Throwable {
    Path out1 = temp.newFolder().toPath().resolve("out1.zip");
    testForD8()
        .addProgramClassesAndInnerClasses(CLASS)
        .setMinApi(AndroidApiLevel.K)
        .compile()
        // TODO(b/123506120): Add .assertNoMessages()
        .writeToZip(out1)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);

    Path outPerClassDir = temp.newFolder().toPath();
    Collection<Path> innerClasses =
        ToolHelper.getClassFilesForInnerClasses(Collections.singleton(CLASS));

    int i = 0;
    List<Path> outs = new ArrayList<>();
    {
      Path mainOut = outPerClassDir.resolve("class" + i++ + ".zip");
      outs.add(mainOut);
      testForD8()
          .addProgramClasses(CLASS)
          .addClasspathFiles(ToolHelper.getClassPathForTests())
          .setIntermediate(true)
          .setMinApi(AndroidApiLevel.K)
          .compile()
          // TODO(b/123506120): Add .assertNoMessages()
          .writeToZip(mainOut);
    }
    for (Path innerClass : innerClasses) {
      Path out = outPerClassDir.resolve("class" + i++ + ".zip");
      outs.add(out);
      testForD8()
          .addProgramFiles(innerClass)
          .addClasspathFiles(ToolHelper.getClassPathForTests())
          .setIntermediate(true)
          .setMinApi(AndroidApiLevel.K)
          .compile()
          // TODO(b/123506120): Add .assertNoMessages()
          .writeToZip(out);
    }

    Path out2 = temp.newFolder().toPath().resolve("out2.zip");
    D8TestCompileResult compiledResult = testForD8()
        .addProgramFiles(outs)
        .compile();

    compiledResult
        // TODO(b/123506120): Add .assertNoMessages()
        .writeToZip(out2)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);

    runDebugger(compiledResult.debugConfig());

    Path dissasemble1 = temp.newFolder().toPath().resolve("disassemble1.txt");
    Path dissasemble2 = temp.newFolder().toPath().resolve("disassemble2.txt");
    Disassemble.disassemble(
        DisassembleCommand.builder().addProgramFiles(out1).setOutputPath(dissasemble1).build());
    Disassemble.disassemble(
        DisassembleCommand.builder().addProgramFiles(out2).setOutputPath(dissasemble2).build());
    String content1 = StringUtils.join(Files.readAllLines(dissasemble1), "\n");
    String content2 = StringUtils.join(Files.readAllLines(dissasemble2), "\n");
    assertEquals(content1, content2);
  }
}
