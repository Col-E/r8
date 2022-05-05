// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class DesugaredLibraryTestCompileResult<T extends DesugaredLibraryTestBase> {

  private final T test;
  private final TestCompileResult<?, ? extends SingleTestRunResult<?>> compileResult;
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final D8TestCompileResult customLibCompile;
  private final L8TestCompileResult l8Compile;
  private final List<Path> runClasspathFiles = new ArrayList<>();

  public DesugaredLibraryTestCompileResult(
      T test,
      TestCompileResult<?, ? extends SingleTestRunResult<?>> compileResult,
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      D8TestCompileResult customLibCompile,
      L8TestCompileResult l8Compile) {
    this.test = test;
    this.compileResult = compileResult;
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.customLibCompile = customLibCompile;
    this.l8Compile = l8Compile;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectCustomLib(
      ThrowingConsumer<CodeInspector, E> consumer) throws Throwable {
    assert customLibCompile != null;
    customLibCompile.inspect(consumer);
    return this;
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

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> apply(
      ThrowingConsumer<DesugaredLibraryTestCompileResult<T>, E> consumer) throws Throwable {
    consumer.accept(this);
    return this;
  }

  public CodeInspector customLibInspector() throws Throwable {
    assert customLibCompile != null;
    return customLibCompile.inspector();
  }

  public CodeInspector l8Inspector() throws Throwable {
    return l8Compile.inspector();
  }

  public CodeInspector inspector() throws Throwable {
    return compileResult.inspector();
  }

  public DesugaredLibraryTestCompileResult<T> inspectDiagnosticMessages(
      Consumer<TestDiagnosticMessages> consumer) {
    compileResult.inspectDiagnosticMessages(consumer);
    return this;
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws Exception {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, String mainClassName, String... args)
      throws Exception {

    Path desugaredLibrary = l8Compile.writeToZip();

    if (runtime.getBackend().isCf()) {
      assert compilationSpecification.isCfToCf();
      return compileResult.addRunClasspathFiles(desugaredLibrary).run(runtime, mainClassName);
    }

    TestCompileResult<?, ? extends SingleTestRunResult<?>> actualCompileResult =
        compilationSpecification.isCfToCf() ? convertCompileResultToDex() : compileResult;

    if (customLibCompile != null) {
      actualCompileResult.addRunClasspathFiles(customLibCompile.writeToZip());
    }

    actualCompileResult
        .addRunClasspathFiles(desugaredLibrary)
        .addRunClasspathFiles(runClasspathFiles);

    return actualCompileResult.run(runtime, mainClassName, args);
  }

  private TestCompileResult<?, ? extends SingleTestRunResult<?>> convertCompileResultToDex()
      throws Exception {
    return test.testForD8()
        .addProgramFiles(compileResult.writeToZip())
        .setMinApi(parameters.getApiLevel())
        .disableDesugaring()
        .compile();
  }

  public Path writeToZip() throws IOException {
    return compileResult.writeToZip();
  }

  public DesugaredLibraryTestCompileResult<T> addRunClasspathFiles(Path... classpathFiles) {
    Collections.addAll(runClasspathFiles, classpathFiles);
    return this;
  }

  public DesugaredLibraryTestCompileResult<T> withArt6Plus64BitsLib() {
    compileResult.withArt6Plus64BitsLib();
    return this;
  }
}
