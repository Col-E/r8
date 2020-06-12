// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.L8;
import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.NoneRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibaryChecksumsTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DesugaredLibaryChecksumsTest(TestParameters parameters) {
    assertEquals(NoneRuntime.getInstance(), parameters.getRuntime());
  }

  @Test
  public void test() throws Exception {
    Path out = temp.newFolder().toPath().resolve("out.jar");
    L8.run(
        L8Command.builder()
            .setIncludeClassesChecksum(true)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.getDesugarJDKLibs())
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .setMode(CompilationMode.DEBUG)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setMinApiLevel(AndroidApiLevel.B.getLevel())
            .setOutput(out, OutputMode.DexIndexed)
            .build());

    try {
      CodeInspector inspector = new CodeInspector(out);
      for (FoundClassSubject clazz : inspector.allClasses()) {
        assertTrue(clazz.getDexProgramClass().getChecksum() > 0);
      }
    } catch (CompilationError e) {
      // TODO(b/158746302): Desugared library should support checksums.
      //  also, the failure should have occured in the L8.run above!
      assertThat(e.getMessage(), containsString("has no checksum"));
    }
  }
}
