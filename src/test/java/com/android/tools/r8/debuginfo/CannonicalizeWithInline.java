// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexSection;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assert;
import org.junit.Test;

public class CannonicalizeWithInline extends TestBase {

  private int getNumberOfDebugInfos(Path file) throws IOException {
    DexSection[] dexSections = DexParser.parseMapFrom(file);
    for (DexSection dexSection : dexSections) {
      if (dexSection.type == Constants.TYPE_DEBUG_INFO_ITEM) {
        return dexSection.length;
      }
    }
    return 0;
  }

  @Test
  public void testCannonicalize() throws Exception {
    Class clazzA = ClassA.class;
    Class clazzB = ClassB.class;

    R8TestCompileResult result = testForR8(Backend.DEX)
        .addProgramClasses(clazzA, clazzB)
        .addKeepRules(
            "-keepattributes SourceFile,LineNumberTable",
            "-keep class ** {\n" +
                "public void call(int);\n" +
            "}"
        )
        .compile();
    Path classesPath = temp.getRoot().toPath();
    result.app.write(classesPath, OutputMode.DexIndexed);
    int numberOfDebugInfos = getNumberOfDebugInfos(
        Paths.get(temp.getRoot().getCanonicalPath(), "classes.dex"));
    Assert.assertEquals(1, numberOfDebugInfos);
  }

  // Two classes which has debug info that looks exactly the same, except for SetInlineFrame.
  // R8 will inline the call to foobar in both classes, causing us to store a SetInlineFrame in the
  // debug info.
  // Ensure that we still canonicalize when writing.
  public static class ClassA {

    public void call(int a) {
        foobar(a);
    }

    private String foobar(int a) {
      String s = "aFoobar" + a;
      return s;
    }
  }

  public static class ClassB {

    public void call(int a) {
      foobar(a);
    }

    private String foobar(int a) {
      String s = "bFoobar" + a;
      return s;
    }
  }
}
