// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.function.IntUnaryOperator;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConversionErrorMessageTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  private static final AndroidApiLevel MIN_SUPPORTED = AndroidApiLevel.O;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getConversionParametersUpToExcluding(MIN_SUPPORTED),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG));
  }

  public ConversionErrorMessageTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  public String getExpectedResult() {
    if (hasRequiredAPI()) {
      // On Cf or high device API levels, the call succeeds.
      return StringUtils.lines("npe", "null", "success");
    }
    // On low device API levels, the call fails, but the error message is different on Dalvik.
    String msg;
    if (parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < AndroidApiLevel.L.getLevel()) {
      msg = "java.util.Arrays.setAll";
    } else {
      msg =
          "No static method setAll([ILjava/util/function/IntUnaryOperator;)V in class"
              + " Ljava/util/Arrays; or its super classes (declaration of 'java.util.Arrays'"
              + " appears in out/host/linux-x86/framework/core-libart-hostdex.jar)";
    }
    return StringUtils.lines(
        "noSuchMethod",
        msg,
        "noClassDef",
        "com.android.tools.r8.desugar.desugaredlibrary.conversiontests."
            + "ConversionErrorMessageTest$MyIntUnaryOperator");
  }

  private boolean hasRequiredAPI() {
    return parameters.isCfRuntime() || parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        >= AndroidApiLevel.N.getLevel();
  }

  @Test
  public void testExceptionNoDesugaring() throws Exception {
    Assume.assumeTrue("No need to run the same test twice", libraryDesugaringSpecification == JDK8);
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addProgramClasses(Executor.class, MyIntUnaryOperator.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  @Test
  public void testExceptionDesugaring() throws Exception {
    Assume.assumeTrue("CF desugaring not supported", parameters.isDexRuntime());
    // TODO(b/143275651): Raise the right error, NoClassDefFoundError on Function or
    //  NoSuchMethodError instead of NoClassDefFoundError on the wrapper.
    Assume.assumeTrue(hasRequiredAPI());
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, MyIntUnaryOperator.class)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  @SuppressWarnings("WeakerAccess")
  static class Executor {

    public static void main(String[] args) {
      noSuchMethod();
      noClassDef();
    }

    @SuppressWarnings({"ConstantConditions", "MismatchedReadAndWriteOfArray"})
    public static void noSuchMethod() {
      int[] ints = new int[3];
      try {
        Arrays.setAll(ints, null);
        System.out.println("success");
      } catch (NoClassDefFoundError ex) {
        System.out.println("noClassDef");
        System.out.println(ex.getMessage());
      } catch (NoSuchMethodError ex) {
        System.out.println("noSuchMethod");
        System.out.println(ex.getMessage());
      } catch (NullPointerException ex) {
        System.out.println("npe");
        System.out.println(ex.getMessage());
      }
    }

    @SuppressWarnings({"MismatchedReadAndWriteOfArray"})
    public static void noClassDef() {
      int[] ints = new int[3];
      try {
        Arrays.setAll(ints, new MyIntUnaryOperator());
        System.out.println("success");
      } catch (NoClassDefFoundError ex) {
        System.out.println("noClassDef");
        System.out.println(ex.getMessage());
      } catch (NoSuchMethodError ex) {
        System.out.println("noSuchMethod");
        System.out.println(ex.getMessage());
      } catch (NullPointerException ex) {
        System.out.println("npe");
        System.out.println(ex.getMessage());
      }
    }
  }

  static class MyIntUnaryOperator implements IntUnaryOperator {

    public int applyAsInt(int x) {
      return x + 1;
    }
  }
}
