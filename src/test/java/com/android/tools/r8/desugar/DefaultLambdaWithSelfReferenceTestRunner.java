// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.Disassemble;
import com.android.tools.r8.Disassemble.DisassembleCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class DefaultLambdaWithSelfReferenceTestRunner extends TestBase {

  final Class<?> CLASS = DefaultLambdaWithSelfReferenceTest.class;
  final String EXPECTED = StringUtils.lines("stateful(stateless)");

  @Test
  public void testJvm() throws Exception {
    testForJvm().addTestClasspath().run(CLASS).assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
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
    testForD8()
        .addProgramFiles(outs)
        .compile()
        // TODO(b/123506120): Add .assertNoMessages()
        .writeToZip(out2)
        .run(CLASS)
        .assertSuccessWithOutput(EXPECTED);

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
