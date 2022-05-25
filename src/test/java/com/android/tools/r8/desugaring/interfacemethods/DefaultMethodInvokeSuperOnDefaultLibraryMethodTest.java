// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugaring.interfacemethods;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.InterfaceDesugarMissingTypeDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DefaultMethodInvokeSuperOnDefaultLibraryMethodTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1", "2");

  private boolean runtimeHasConsumerInterface(TestParameters parameters) {
    // java,util.function.Consumer was introduced at API level 24.
    return parameters.asDexRuntime().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
  }

  @Test
  public void testD8WithDefaultInterfaceMethodDesugaringWithAPIInLibrary() throws Exception {
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.I_MR1)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        allOf(
                            diagnosticType(StringDiagnostic.class),
                            diagnosticMessage(
                                containsString(
                                    "Interface method desugaring has inserted NoSuchMethodError"
                                        + " replacing a super call in")),
                            diagnosticMessage(containsString("forEachPrint")))))
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            // If the platform does not have java.util.function.Consumer the lambda instantiation
            // will throw NoClassDefFoundError as it implements java.util.function.Consumer.
            // Otherwise, the generated code will throw NoSuchMethodError.
            runtimeHasConsumerInterface(parameters),
            b -> b.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testD8WithDefaultInterfaceMethodDesugaringWithoutAPIInLibrary() throws Exception {
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.M))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.I_MR1)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyWarnings()
                    .assertWarningsMatch(
                        diagnosticType(InterfaceDesugarMissingTypeDiagnostic.class)))
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            // If the platform does not have java.util.function.Consumer the lambda instantiation
            // will throw NoClassDefFoundError as it implements java.util.function.Consumer.
            // Otherwise, the generated code will throw NoSuchMethodError.
            runtimeHasConsumerInterface(parameters),
            b -> b.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testD8WithDefaultInterfaceMethodSupport() throws Exception {
    assumeTrue(
        parameters.asDexRuntime().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.N)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8WithDefaultInterfaceMethodDesugaringWithAPIInLibrary() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.I_MR1)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(NoSuchMethodError.class);
  }

  @Test
  public void testR8WithDefaultInterfaceMethodDesugaringWithoutAPIInLibrary() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.M))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.I_MR1)
        .addKeepMainRule(TestClass.class)
        .addDontWarn(Consumer.class)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            // If the platform does not have java.util.function.Consumer the lambda instantiation
            // will throw NoClassDefFoundError as it implements java.util.function.Consumer.
            // Otherwise, the generated code will throw NoSuchMethodError.
            runtimeHasConsumerInterface(parameters),
            b -> b.assertFailureWithErrorThatThrows(NoSuchMethodError.class),
            b -> b.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8WithDefaultInterfaceMethodSupport() throws Exception {
    assumeTrue(
        parameters.asDexRuntime().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.T))
        .addInnerClasses(getClass())
        .setMinApi(AndroidApiLevel.N)
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  interface IntegerIterable extends Iterable<Integer> {
    default void forEachPrint() {
      Iterable.super.forEach(System.out::println);
    }
  }

  static class IntegerIterable1And2 implements IntegerIterable {

    @Override
    public Iterator<Integer> iterator() {
      List<Integer> result = new ArrayList<>();
      result.add(1);
      result.add(2);
      return result.iterator();
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new IntegerIterable1And2().forEachPrint();
    }
  }
}
