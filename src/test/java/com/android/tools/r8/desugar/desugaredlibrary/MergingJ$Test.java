// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdktests.Jdk11DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import org.junit.Test;

public class MergingJ$Test extends Jdk11DesugaredLibraryTestBase {

  @Test
  public void testMergingJ$() throws Exception {
    Path mergerInputPart1 = buildSplitDesugaredLibraryPart1();
    Path mergerInputPart2 = buildSplitDesugaredLibraryPart2();
    CodeInspector codeInspectorOutput = null;
    try {
      codeInspectorOutput =
          testForD8()
              .addProgramFiles(mergerInputPart1, mergerInputPart2)
              .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
              .compile()
              .inspector();
    } catch (Exception e) {
      if (e.getCause().getMessage().contains("Merging dex file containing classes with prefix")) {
        // TODO(b/138278440): Forbid to merge j$ classes in a Google3 compliant way.
        // In Google 3 the Dex merger is used to merge the Bazel desugared core library.
        // The Dex merger has to be able to merge multiple classes with the prefix j$ for this case.
        // The following should therefore not raise:
        // "Merging dex file containing classes with prefix j$. is not allowed."
        fail();
      }
      throw e;
    }
    CodeInspector codeInspectorSplit1 = new CodeInspector(mergerInputPart1);
    CodeInspector codeInspectorSplit2 = new CodeInspector(mergerInputPart2);
    assertNotNull(codeInspectorOutput);
    assertTrue(codeInspectorOutput.allClasses().size() > codeInspectorSplit1.allClasses().size());
    assertTrue(codeInspectorOutput.allClasses().size() > codeInspectorSplit2.allClasses().size());
  }

  private Path buildSplitDesugaredLibraryPart1() throws Exception {
    Path outputDex = temp.newFolder().toPath().resolve("merger-input-dex.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setMinApiLevel(AndroidApiLevel.B.getLevel())
            .setOutput(outputDex, OutputMode.DexIndexed)
            .build());
    return outputDex;
  }

  private Path buildSplitDesugaredLibraryPart2() throws Exception {
    Path outputCf = temp.newFolder().toPath().resolve("merger-input-split-cf.zip");
    Path outputDex = temp.newFolder().toPath().resolve("merger-input-split-dex.zip");
    L8.run(
        L8Command.builder()
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(JDK_11_JAVA_BASE_EXTENSION_COMPILED_FILES)
            .addClasspathFiles(ToolHelper.getDesugarJDKLibs())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setMinApiLevel(AndroidApiLevel.B.getLevel())
            .setOutput(outputCf, OutputMode.DexIndexed)
            .build());
    testForD8()
        .addProgramFiles(outputCf)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .writeToZip(outputDex);
    return outputDex;
  }
}
