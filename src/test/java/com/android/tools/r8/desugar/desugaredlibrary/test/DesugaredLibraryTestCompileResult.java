// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.function.Consumer;

public class DesugaredLibraryTestCompileResult<T extends DesugaredLibraryTestBase> {

  private final T test;
  private final TestCompileResult<?, ?> compileResult;
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final CustomLibrarySpecification customLibrarySpecification;
  private final L8TestCompileResult l8Compile;

  public DesugaredLibraryTestCompileResult(
      T test,
      TestCompileResult<?, ?> compileResult,
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      CustomLibrarySpecification customLibrarySpecification,
      L8TestCompileResult l8Compile) {
    this.test = test;
    this.compileResult = compileResult;
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.customLibrarySpecification = customLibrarySpecification;
    this.l8Compile = l8Compile;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectL8(
      ThrowingConsumer<CodeInspector, E> consumer) throws Throwable {
    l8Compile.inspect(consumer);
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspect(
      ThrowingConsumer<CodeInspector, E> consumer) throws Throwable {
    compileResult.inspect(consumer);
    return this;
  }

  public DesugaredLibraryTestCompileResult<T> inspectDiagnosticMessages(
      Consumer<TestDiagnosticMessages> consumer) {
    compileResult.inspectDiagnosticMessages(consumer);
    return this;
  }

  public TestRunResult<?> run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws Exception {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public TestRunResult<?> run(TestRuntime runtime, String mainClassName, String... args)
      throws Exception {

    Path desugaredLibrary = l8Compile.writeToZip();

    if (runtime.getBackend().isCf()) {
      assert compilationSpecification.isCfToCf();
      return compileResult.addRunClasspathFiles(desugaredLibrary).run(runtime, mainClassName);
    }

    TestCompileResult<?, ?> actualCompileResult =
        compilationSpecification.isCfToCf() ? convertCompileResultToDex() : compileResult;

    if (customLibrarySpecification != null) {
      Path customLib =
          test.testForD8()
              .addProgramClasses(customLibrarySpecification.getClasses())
              .setMinApi(customLibrarySpecification.getMinApi())
              .compile()
              .writeToZip();
      actualCompileResult.addRunClasspathFiles(customLib);
    }

    actualCompileResult.addRunClasspathFiles(desugaredLibrary);

    return actualCompileResult.run(runtime, mainClassName, args);
  }

  private TestCompileResult<?, ?> convertCompileResultToDex() throws Exception {
    return test.testForD8()
        .addProgramFiles(compileResult.writeToZip())
        .setMinApi(parameters.getApiLevel())
        .disableDesugaring()
        .compile();
  }
}
