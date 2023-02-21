// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.b113138046;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NativeMethodTest extends TestBase {

  @Parameters(name = "{0}, compat:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean compatMode;

  public NativeMethodTest(TestParameters parameters, boolean compatMode) {
    this.parameters = parameters;
    this.compatMode = compatMode;
  }

  private void test(List<String> config, boolean expectedFooPresence) throws Exception {
    R8TestCompileResult compileResult =
        (compatMode ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
            .setMinApi(parameters)
            .addProgramClassesAndInnerClasses(Keep.class, Data.class, Handler.class, Outer.class)
            .addKeepRules(config)
            .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
            .compile();
    compileResult.runDex2Oat(parameters.getRuntime()).assertNoVerificationErrors();
    CodeInspector inspector = compileResult.inspector();
    boolean innerFound = false;
    for (ClassSubject clazz : inspector.allClasses()) {
      innerFound = clazz.getOriginalName().endsWith("Inner");
      if (!innerFound) {
        continue;
      }
      MethodSubject nativeFoo = clazz.method(
          "void", "foo", ImmutableList.of(Handler.class.getCanonicalName()));
      assertEquals(expectedFooPresence, nativeFoo.isPresent());
      if (expectedFooPresence) {
        assertThat(nativeFoo, isPresentAndNotRenamed());
        DexEncodedMethod method = nativeFoo.getMethod();
        assertTrue(method.accessFlags.isNative());
        assertNull(method.getCode());
      }
      break;
    }
    assertTrue(innerFound);
  }

  @Test
  public void test_keep_OnEvent() throws Exception {
    List<String> config = ImmutableList.of(
        "-keepclasseswithmembers,allowshrinking class * {",
        "  native <methods>;",
        "}",
        "-keepclassmembers,includedescriptorclasses class * {",
        "  native <methods>;",
        "}",
        "-keep class " + Outer.class.getCanonicalName() + " {",
        "  onEvent(...);",
        "}",
        "-keepattributes InnerClasses,EnclosingMethod,Signature",
        "-allowaccessmodification");
    test(config, compatMode);
  }

  @Test
  public void test_keep_annotation() throws Exception {
    List<String> config = ImmutableList.of(
        "-keepclasseswithmembers,allowshrinking class * {",
        "  native <methods>;",
        "}",
        "-keepclassmembers,includedescriptorclasses class * {",
        "  native <methods>;",
        "}",
        "-keep,allowobfuscation @interface **.Keep",
        "-keep @**.Keep class *",
        "-keepclassmembers class * {",
        "  @**.Keep <fields>;",
        "  @**.Keep <methods>;",
        "}",
        "-keepattributes InnerClasses,EnclosingMethod,Signature",
        "-allowaccessmodification");
    test(config, true);
  }
}
