// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.CustomLibrarySpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// See b/204518518.
@RunWith(Parameterized.class)
public class ProgramInterfaceWithLibraryMethod extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("Hello, world!");

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public ProgramInterfaceWithLibraryMethod(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
  }

  @Test
  public void testProgramInterfaceWithLibraryMethod() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClasses(Executor.class, ProgramInterface.class, ProgramClass.class)
        .setCustomLibrarySpecification(
            new CustomLibrarySpecification(LibraryClass.class, AndroidApiLevel.B))
        .addKeepMainRule(Executor.class)
        .run(parameters.getRuntime(), Executor.class)
        .applyIf(
            !libraryDesugaringSpecification.hasJDollarFunction(parameters)
                || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N),
            r -> r.assertSuccessWithOutput(EXPECTED_RESULT),
            r -> {
              if (compilationSpecification.isProgramShrink()) {
                r.assertFailureWithErrorThatThrows(NoSuchMethodError.class);
              } else {
                r.assertFailureWithErrorThatThrows(AbstractMethodError.class);
              }
            });
  }

  static class Executor {

    public static void main(String[] args) {
      invoke(new ProgramClass());
    }

    static void invoke(ProgramInterface i) {
      i.methodTakingConsumer(null);
    }
  }

  interface ProgramInterface {
    void methodTakingConsumer(Consumer<String> consumer);
  }

  static class ProgramClass extends LibraryClass implements ProgramInterface {
    // TODO(b/204518518): Adding this forwarding method fixes the issue.
    // public void methodTakingConsumer(Consumer<String> consumer) {
    //   super.methodTakingConsumer(consumer);
    // }
  }

  // This class will be put at compilation time as library and on the runtime class path.
  // This class is convenient for easy testing. Each method plays the role of methods in the
  // platform APIs for which argument/return values need conversion.
  static class LibraryClass {

    public void methodTakingConsumer(Consumer<String> consumer) {
      System.out.println("Hello, world!");
    }
  }
}
