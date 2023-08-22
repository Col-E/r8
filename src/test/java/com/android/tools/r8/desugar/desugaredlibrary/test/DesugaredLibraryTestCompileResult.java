// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class DesugaredLibraryTestCompileResult<T extends DesugaredLibraryTestBase> {

  private final T test;
  private final TestCompileResult<?, ? extends SingleTestRunResult<?>> compileResult;
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final D8TestCompileResult customLibCompile;
  private final L8TestCompileResult l8Compile;
  private final List<ExternalArtProfile> l8ResidualArtProfiles;
  // In case of Cf2Cf desugaring the run on dex, the compileResult is the Cf desugaring result
  // while the runnableCompiledResult is the dexed compiledResult used to run on dex.
  private final TestCompileResult<?, ? extends SingleTestRunResult<?>> runnableCompiledResult;

  public DesugaredLibraryTestCompileResult(
      T test,
      TestCompileResult<?, ? extends SingleTestRunResult<?>> compileResult,
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification,
      D8TestCompileResult customLibCompile,
      L8TestCompileResult l8Compile,
      List<ExternalArtProfile> l8ResidualArtProfiles)
      throws CompilationFailedException, IOException {
    this.test = test;
    this.compileResult = compileResult;
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.customLibCompile = customLibCompile;
    this.l8Compile = l8Compile;
    this.l8ResidualArtProfiles = l8ResidualArtProfiles;
    this.runnableCompiledResult = computeRunnableCompiledResult();
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectCustomLib(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    assert customLibCompile != null;
    customLibCompile.inspect(consumer);
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectL8(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    l8Compile.inspect(consumer);
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectL8ResidualArtProfile(
      ThrowingConsumer<ArtProfileInspector, E> consumer) throws E, IOException {
    return inspectL8ResidualArtProfile(
        (rewrittenArtProfile, inspector) -> consumer.accept(rewrittenArtProfile));
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectL8ResidualArtProfile(
      ThrowingBiConsumer<ArtProfileInspector, CodeInspector, E> consumer) throws E, IOException {
    assertEquals(1, l8ResidualArtProfiles.size());
    consumer.accept(
        new ArtProfileInspector(l8ResidualArtProfiles.iterator().next()), l8Inspector());
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspect(
      ThrowingConsumer<CodeInspector, E> consumer) throws IOException, E {
    compileResult.inspect(consumer);
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> inspectKeepRules(
      ThrowingConsumer<List<String>, E> consumer) throws E {
    if (compilationSpecification.isL8Shrink()) {
      l8Compile.inspectKeepRules(consumer);
    }
    return this;
  }

  public <E extends Throwable> DesugaredLibraryTestCompileResult<T> apply(
      ThrowingConsumer<DesugaredLibraryTestCompileResult<T>, E> consumer) throws E {
    consumer.accept(this);
    return this;
  }

  public CodeInspector customLibInspector() throws IOException {
    assert customLibCompile != null;
    return customLibCompile.inspector();
  }

  public CodeInspector l8Inspector() throws IOException {
    return l8Compile.inspector();
  }

  public CodeInspector inspector() throws IOException {
    return compileResult.inspector();
  }

  public DesugaredLibraryTestCompileResult<T> inspectDiagnosticMessages(
      Consumer<TestDiagnosticMessages> consumer) {
    compileResult.inspectDiagnosticMessages(consumer);
    return this;
  }

  public DesugaredLibraryTestCompileResult<T> inspectL8DiagnosticMessages(
      Consumer<TestDiagnosticMessages> consumer) {
    l8Compile.inspectDiagnosticMessages(consumer);
    return this;
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws ExecutionException, IOException {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, String mainClassName, String... args)
      throws ExecutionException, IOException {
    return runnableCompiledResult.run(runtime, mainClassName, args);
  }

  private TestCompileResult<?, ? extends SingleTestRunResult<?>> computeRunnableCompiledResult()
      throws CompilationFailedException, IOException {
    TestCompileResult<?, ? extends SingleTestRunResult<?>> runnable = convertToDexIfNeeded();
    if (customLibCompile != null) {
      runnable.addRunClasspathFiles(customLibCompile.writeToZip());
    }
    runnable.addRunClasspathFiles(l8Compile.writeToZip());
    return runnable;
  }

  private TestCompileResult<?, ? extends SingleTestRunResult<?>> convertToDexIfNeeded()
      throws CompilationFailedException, IOException {
    if (!(compilationSpecification.isCfToCf() && parameters.getBackend().isDex())) {
      return compileResult;
    }
    return test.testForD8()
        .addProgramFiles(this.compileResult.writeToZip())
        .setMinApi(parameters)
        .setMode(compilationSpecification.getProgramCompilationMode())
        .disableDesugaring()
        .compile();
  }

  public Path writeToZip() throws IOException {
    return runnableCompiledResult.writeToZip();
  }

  public DesugaredLibraryTestCompileResult<T> writeToZip(Path path) throws IOException {
    runnableCompiledResult.writeToZip(path);
    return this;
  }

  public Path writeL8ToZip() throws IOException {
    return l8Compile.writeToZip();
  }

  public DesugaredLibraryTestCompileResult<T> addRunClasspathFiles(Path... classpathFiles) {
    runnableCompiledResult.addRunClasspathFiles(classpathFiles);
    return this;
  }

  public DesugaredLibraryTestCompileResult<T> withArt6Plus64BitsLib() {
    runnableCompiledResult.withArt6Plus64BitsLib();
    return this;
  }
}
