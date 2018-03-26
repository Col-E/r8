// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class BaseDefaultMethodTestRunner {
  static final Class CLASS = BaseDefaultMethodTest.class;
  static final Class[] CLASSES = BaseDefaultMethodTest.CLASSES;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Test
  public void test() throws Exception {
    ProcessResult runInput =
        ToolHelper.runJava(ToolHelper.getClassPathForTests(), CLASS.getCanonicalName());
    assertEquals(0, runInput.exitCode);
    List<String> config =
        Arrays.asList(
            "-keep public class " + CLASS.getCanonicalName() + " {",
            "  public static void main(...);",
            "}");
    Path out = temp.getRoot().toPath().resolve("out.jar");
    Builder builder =
        R8Command.builder()
            .setMode(CompilationMode.DEBUG)
            .addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()))
            .setProgramConsumer(new ArchiveConsumer(out))
            .addProguardConfiguration(config, Origin.unknown());
    for (Class<?> c : CLASSES) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    // TODO(b/75997473): Enable inlining when supported
    ToolHelper.runR8(builder.build(), options -> options.enableInlining = false);
    ProcessResult runOutput = ToolHelper.runJava(out, CLASS.getCanonicalName());
    assertEquals(runInput.toString(), runOutput.toString());
  }
}
