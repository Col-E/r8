// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
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

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private final TestParameters parameters;

  public InvokeSuperToRewrittenDefaultMethodTest(TestParameters parameters) {
    this.parameters = parameters;
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
    testForD8()
        .addInnerClasses(InvokeSuperToRewrittenDefaultMethodTest.class)
        .setMinApi(parameters.getApiLevel())
        .addLibraryFiles(getLibraryFile())
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forApiLevel(parameters.getApiLevel()))
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

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
