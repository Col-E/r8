// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmptyMainDexInputTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public EmptyMainDexInputTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testMainDexRulesAvoidsMainDexCheck() throws Exception {
    Path app = temp.newFolder().toPath().resolve("app.jar");
    MainDexListTests.generateManyClassesMultiDexApp(app);

    Path debugOut =
        testForD8(Backend.DEX)
            .addProgramFiles(app)
            .setMinApi(AndroidApiLevel.B)
            .addMainDexRules("-keep class com.example.NotPresent")
            .debug()
            .compile()
            .writeToZip();
    checkOutputIsTwoFiles(debugOut);

    Path releaseOut =
        testForD8(Backend.DEX)
            .addProgramFiles(app)
            .setMinApi(AndroidApiLevel.B)
            .addMainDexRules("-keep class com.example.NotPresent")
            .release()
            .compile()
            .writeToZip();
    checkOutputIsTwoFiles(releaseOut);
  }

  private void checkOutputIsTwoFiles(Path out) throws IOException {
    // The example application fits in two files, check we don't have an empty "main-dex file".
    IntBox classesFileCount = new IntBox(0);
    ZipUtils.iter(
        out,
        (entry, input) -> {
          if (entry.getName().startsWith("classes")) {
            classesFileCount.increment();
          }
        });
    assertEquals(2, classesFileCount.get());
  }
}
