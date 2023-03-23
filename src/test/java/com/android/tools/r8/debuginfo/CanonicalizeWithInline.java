// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;


import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexSection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CanonicalizeWithInline extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public CanonicalizeWithInline(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private int getNumberOfDebugInfos(Path file) throws IOException {
    List<DexSection> dexSections = DexParser.parseMapFrom(file);
    for (DexSection dexSection : dexSections) {
      if (dexSection.type == Constants.TYPE_DEBUG_INFO_ITEM) {
        return dexSection.length;
      }
    }
    return 0;
  }

  @Test
  public void testCanonicalize() throws Exception {
    Class<?> clazzA = ClassA.class;
    Class<?> clazzB = ClassB.class;

    Path classesPath =
        testForR8(Backend.DEX)
            .setMinApi(AndroidApiLevel.B)
            .addProgramClasses(clazzA, clazzB)
            .addKeepRules(
                "-keepattributes SourceFile,LineNumberTable",
                "-keep class ** { public void call(int); }")
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .compile()
            .writeToDirectory();
    int numberOfDebugInfos = getNumberOfDebugInfos(classesPath.resolve("classes.dex"));
    // All methods have one parameter and use a single shared pc2pc item.
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
      return doSomething(a);
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    private String doSomething(int a) {
      String s = "bFoobar" + a;
      return s;
    }
  }

  public static class ClassB {

    public void call(int a) {
      foobar(a);
    }

    private String foobar(int a) {
      return doSomething(a);
    }

    @AssumeMayHaveSideEffects
    @NeverInline
    private String doSomething(int a) {
      String s = "bFoobar" + a;
      return s;
    }
  }
}
