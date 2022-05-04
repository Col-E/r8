// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CallBackConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.N;
  private static final String EXPECTED_RESULT = StringUtils.lines("0", "1", "0", "1");

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public CallBackConversionTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  private void assertDuplicatedAPI(CodeInspector i) {
    List<FoundMethodSubject> virtualMethods = i.clazz(Impl.class).virtualMethods();
    assertEquals(2, virtualMethods.size());
    assertTrue(anyVirtualMethodFirstParameterMatches(virtualMethods, "j$.util.function.Consumer"));
    assertTrue(
        anyVirtualMethodFirstParameterMatches(virtualMethods, "java.util.function.Consumer"));
  }

  private boolean anyVirtualMethodFirstParameterMatches(
      List<FoundMethodSubject> virtualMethods, String anObject) {
    return virtualMethods.stream()
        .anyMatch(
            m ->
                m.getMethod()
                    .getReference()
                    .proto
                    .parameters
                    .values[0]
                    .toString()
                    .equals(anObject));
  }

  @Test
  public void testCallBack() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Impl.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(CustomLibClass.class, MIN_SUPPORTED))
        .addKeepMainRule(Impl.class)
        .compile()
        .inspect(this::assertDuplicatedAPI)
        .inspect(this::assertLibraryOverridesPresent)
        .run(parameters.getRuntime(), Impl.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void assertLibraryOverridesPresent(CodeInspector i) {
    // The j$ method can be optimized, but the java method should be present to be called
    // through the library.
    List<FoundMethodSubject> virtualMethods = i.clazz(Impl.class).virtualMethods();
    assertTrue(
        virtualMethods.stream()
            .anyMatch(
                m ->
                    m.getMethod().getReference().name.toString().equals("foo")
                        && m.getMethod()
                            .getReference()
                            .proto
                            .parameters
                            .values[0]
                            .toString()
                            .equals("java.util.function.Consumer")));
  }

  static class Impl extends CustomLibClass {

    public int foo(Consumer<Object> o) {
      o.accept(0);
      return 1;
    }

    public static void main(String[] args) {
      Impl impl = new Impl();
      // Call foo through java parameter.
      System.out.println(CustomLibClass.callFoo(impl, System.out::println));
      // Call foo through j$ parameter.
      System.out.println(impl.foo(System.out::println));
    }
  }

  abstract static class CustomLibClass {

    public abstract int foo(Consumer<Object> consumer);

    @SuppressWarnings({"UnusedReturnValue", "WeakerAccess"})
    public static int callFoo(CustomLibClass object, Consumer<Object> consumer) {
      return object.foo(consumer);
    }
  }
}
