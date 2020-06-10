// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeSuperToRewrittenDefaultMethodTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED = StringUtils.lines("Y", "89");

  @Parameterized.Parameters(name = "{0}, old-rt:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean rtWithoutConsumer;

  public InvokeSuperToRewrittenDefaultMethodTest(
      TestParameters parameters, boolean rtWithoutConsumer) {
    this.parameters = parameters;
    this.rtWithoutConsumer = rtWithoutConsumer;
  }

  private boolean needsDefaultInterfaceMethodDesugaring() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testReference() throws Exception {
    assumeFalse(needsDefaultInterfaceMethodDesugaring());
    testForRuntime(parameters)
        .addInnerClasses(InvokeSuperToRewrittenDefaultMethodTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(needsDefaultInterfaceMethodDesugaring());
    try {
      testForD8()
          .addInnerClasses(InvokeSuperToRewrittenDefaultMethodTest.class)
          .setMinApi(parameters.getApiLevel())
          .apply(
              b -> {
                if (rtWithoutConsumer) {
                  b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.B));
                  // TODO(b/158543446): Remove this once enableCoreLibraryDesugaring is fixed.
                  b.getBuilder()
                      .addDesugaredLibraryConfiguration(
                          StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING));
                } else {
                  // TODO(b/158543446): Move this out to the shared builder once
                  //  enableCoreLibraryDesugaring is fixed.
                  b.enableCoreLibraryDesugaring(parameters.getApiLevel());
                }
              })
          .compileWithExpectedDiagnostics(
              diagnostics -> {
                if (rtWithoutConsumer) {
                  diagnostics.assertOnlyErrors();
                  // TODO(b/158543011): Should fail with a nice user error for invalid library.
                  diagnostics.assertErrorsMatch(
                      allOf(
                          diagnosticType(ExceptionDiagnostic.class),
                          diagnosticMessage(containsString("AssertionError"))));
                } else {
                  diagnostics.assertNoMessages();
                }
              })
          .addDesugaredCoreLibraryRunClassPath(
              this::buildDesugaredLibrary, parameters.getApiLevel())
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED);
      assertFalse(rtWithoutConsumer);
    } catch (CompilationFailedException e) {
      assertTrue(rtWithoutConsumer);
    }
  }

  @FunctionalInterface
  public interface CharConsumer extends Consumer<Character>, IntConsumer {

    void accept(char c);

    @Override
    default void accept(int value) {
      accept((char) value);
    }

    @Override
    default void accept(Character character) {
      accept(character.charValue());
    }

    @Override
    default Consumer<Character> andThen(Consumer<? super Character> after) {
      // Simple forward to the default method of the parent.
      // Must be rewritten to target the companion class of the rewritten Consumer type.
      return Consumer.super.andThen(after);
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      CharConsumer consumer = System.out::println;
      consumer.andThen((Consumer<? super Character>) c -> System.out.println((int) c)).accept('Y');
    }
  }
}
