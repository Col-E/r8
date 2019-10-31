// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.TestBase.Backend.DEX;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.CfDebugTestConfig;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.debug.DexDebugTestConfig;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.TriFunction;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ObjectArrays;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;

public abstract class TestCompileResult<
        CR extends TestCompileResult<CR, RR>, RR extends TestRunResult>
    extends TestBaseResult<CR, RR> {

  public final AndroidApp app;
  private final OutputMode outputMode;
  final List<Path> additionalRunClassPath = new ArrayList<>();
  final List<String> vmArguments = new ArrayList<>();
  private boolean withArt6Plus64BitsLib = false;
  private boolean withArtFrameworks = true;

  TestCompileResult(TestState state, AndroidApp app, OutputMode outputMode) {
    super(state);
    this.app = app;
    this.outputMode = outputMode;
  }

  public final CR withArt6Plus64BitsLib() {
    withArt6Plus64BitsLib = true;
    return self();
  }

  public final CR withArtFrameworks() {
    withArtFrameworks = true;
    return self();
  }

  public final Backend getBackend() {
    if (outputMode == OutputMode.ClassFile) {
      return Backend.CF;
    }
    assert outputMode == OutputMode.DexIndexed
        || outputMode == OutputMode.DexFilePerClass
        || outputMode == OutputMode.DexFilePerClassFile;
    return Backend.DEX;
  }

  public abstract TestDiagnosticMessages getDiagnosticMessages();

  public OutputMode getOutputMode() {
    return outputMode;
  }

  protected abstract RR createRunResult(TestRuntime runtime, ProcessResult result);

  @Deprecated
  public RR run(Class<?> mainClass) throws ExecutionException, IOException {
    return run(mainClass.getTypeName());
  }

  @Deprecated
  public RR run(String mainClass) throws ExecutionException, IOException {
    ClassSubject mainClassSubject = inspector().clazz(mainClass);
    assertThat(mainClassSubject, isPresent());
    switch (getBackend()) {
      case DEX:
        return runArt(
            new DexRuntime(ToolHelper.getDexVm()),
            additionalRunClassPath,
            mainClassSubject.getFinalName());
      case CF:
        return runJava(
            TestRuntime.getDefaultJavaRuntime(),
            additionalRunClassPath,
            mainClassSubject.getFinalName());
      default:
        throw new Unreachable();
    }
  }

  public RR run(TestRuntime runtime, Class<?> mainClass) throws ExecutionException, IOException {
    return run(runtime, mainClass.getTypeName());
  }

  public RR run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws ExecutionException, IOException {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public RR run(TestRuntime runtime, String mainClass) throws ExecutionException, IOException {
    return run(runtime, mainClass, new String[] {});
  }

  public RR run(TestRuntime runtime, String mainClass, String... args)
      throws ExecutionException, IOException {
    assert getBackend() == runtime.getBackend();
    ClassSubject mainClassSubject = inspector().clazz(mainClass);
    assertThat("Did you forget a keep rule for the main method?", mainClassSubject, isPresent());
    if (runtime.isDex()) {
      return runArt(runtime, additionalRunClassPath, mainClassSubject.getFinalName(), args);
    }
    assert runtime.isCf();
    return runJava(
        runtime,
        additionalRunClassPath,
        ObjectArrays.concat(mainClassSubject.getFinalName(), args));
  }

  public CR addRunClasspathFiles(Path... classpath) {
    return addRunClasspathFiles(Arrays.asList(classpath));
  }

  public CR addRunClasspathFiles(List<Path> classpath) {
    additionalRunClassPath.addAll(classpath);
    return self();
  }

  public CR addRunClasspathClasses(Class<?>... classpath) {
    return addRunClasspathClasses(Arrays.asList(classpath));
  }

  public CR addRunClasspathClasses(List<Class<?>> classpath) {
    assert getBackend() == Backend.CF;
    try {
      Path path = state.getNewTempFolder().resolve("runtime-classes.jar");
      ArchiveConsumer consumer = new ArchiveConsumer(path);
      for (Class clazz : classpath) {
        consumer.accept(
            ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
            DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()),
            null);
      }
      consumer.finished(null);
      additionalRunClassPath.addAll(Collections.singletonList(path));
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public CR addDesugaredCoreLibraryRunClassPath(
      Function<AndroidApiLevel, Path> classPathSupplier, AndroidApiLevel minAPILevel) {
    if (minAPILevel.getLevel() < AndroidApiLevel.O.getLevel()) {
      addRunClasspathFiles(classPathSupplier.apply(minAPILevel));
    }
    return self();
  }

  public CR addDesugaredCoreLibraryRunClassPath(
      TriFunction<AndroidApiLevel, String, Boolean, Path> classPathSupplier,
      AndroidApiLevel minAPILevel,
      String keepRules,
      boolean shrink) {
    if (minAPILevel.getLevel() < AndroidApiLevel.O.getLevel()) {
      addRunClasspathFiles(classPathSupplier.apply(minAPILevel, keepRules, shrink));
    }
    return self();
  }

  public CR enableRuntimeAssertions() {
    assert getBackend() == Backend.CF;
    if (!this.vmArguments.contains("-ea")) {
      this.vmArguments.add("-ea");
    }
    return self();
  }

  public Path writeToZip() throws IOException {
    Path file = state.getNewTempFolder().resolve("out.zip");
    writeToZip(file);
    return file;
  }

  public CR writeToZip(Consumer<Path> fn) throws IOException {
    fn.accept(writeToZip());
    return self();
  }

  public CR writeToZip(Path file) throws IOException {
    app.writeToZip(file, getOutputMode());
    return self();
  }

  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app);
  }

  public <E extends Throwable> CR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    consumer.accept(inspector());
    return self();
  }

  public CR assertNoMessages() {
    getDiagnosticMessages().assertNoMessages();
    return self();
  }

  public CR assertOnlyInfos() {
    getDiagnosticMessages().assertOnlyInfos();
    return self();
  }

  public CR assertOnlyWarnings() {
    getDiagnosticMessages().assertOnlyWarnings();
    return self();
  }

  public CR assertInfoMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertInfoMessageThatMatches(matcher);
    return self();
  }

  public CR assertNoInfoMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertNoInfoMessageThatMatches(matcher);
    return self();
  }

  public CR assertWarningMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertWarningMessageThatMatches(matcher);
    return self();
  }

  public CR assertNoWarningMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertNoWarningMessageThatMatches(matcher);
    return self();
  }

  public CR disassemble(PrintStream ps) throws IOException, ExecutionException {
    ToolHelper.disassemble(app, ps);
    return self();
  }

  public CR disassemble() throws IOException, ExecutionException {
    return disassemble(System.out);
  }

  public DebugTestConfig debugConfig() {
    // Rethrow exceptions since debug config is usually used in a delayed wrapper which
    // does not declare exceptions.
    try {
      Path out = state.getNewTempFolder().resolve("out.zip");
      app.writeToZip(out, getOutputMode());
      switch (getBackend()) {
        case CF:
          return new CfDebugTestConfig().addPaths(out);
        case DEX:
          return new DexDebugTestConfig().addPaths(out);
        default:
          throw new Unreachable();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RR runJava(TestRuntime runtime, List<Path> additionalClassPath, String... arguments)
      throws IOException {
    assert runtime != null;
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.ClassFile);
    List<Path> classPath = ImmutableList.<Path>builder()
        .addAll(additionalClassPath)
        .add(out)
        .build();
    ProcessResult result =
        ToolHelper.runJava(runtime.asCf().getVm(), vmArguments, classPath, arguments);
    return createRunResult(runtime, result);
  }

  private RR runArt(
      TestRuntime runtime, List<Path> additionalClassPath, String mainClass, String... arguments)
      throws IOException {
    DexVm vm = runtime.asDex().getVm();
    // TODO(b/127785410): Always assume a non-null runtime.
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZip(out, OutputMode.DexIndexed);
    List<String> classPath = ImmutableList.<String>builder()
        .addAll(additionalClassPath.stream().map(Path::toString).collect(Collectors.toList()))
        .add(out.toString())
        .build();
    Consumer<ArtCommandBuilder> commandConsumer =
        withArt6Plus64BitsLib && vm.getVersion().isAtLeast(DexVm.Version.V6_0_1)
            ? builder -> builder.appendArtOption("--64")
            : builder -> {};
    ProcessResult result =
        ToolHelper.runArtRaw(
            classPath, mainClass, commandConsumer, vm, withArtFrameworks, arguments);
    return createRunResult(runtime, result);
  }

  public Dex2OatTestRunResult runDex2Oat(TestRuntime runtime) throws IOException {
    assert getBackend() == DEX;
    DexVm vm = runtime.asDex().getVm();
    Path tmp = state.getNewTempFolder();
    Path jarFile = tmp.resolve("out.jar");
    Path oatFile = tmp.resolve("out.oat");
    app.writeToZip(jarFile, OutputMode.DexIndexed);
    return new Dex2OatTestRunResult(app, runtime, ToolHelper.runDex2OatRaw(jarFile, oatFile, vm));
  }
}
