// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.TestBase.Backend.DEX;
import static com.android.tools.r8.TestBase.testForD8;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.TriFunction;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ObjectArrays;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hamcrest.Matcher;

public abstract class TestCompileResult<
        CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
    extends TestBaseResult<CR, RR> {

  public final AndroidApp app;
  public final int minApiLevel;
  private final OutputMode outputMode;
  final List<Path> additionalRunClassPath = new ArrayList<>();
  final List<Path> additionalBootClasspath = new ArrayList<>();
  final List<String> vmArguments = new ArrayList<>();
  private boolean withArt6Plus64BitsLib = false;
  private LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration;

  TestCompileResult(TestState state, AndroidApp app, int minApiLevel, OutputMode outputMode) {
    super(state);
    this.app = app;
    this.minApiLevel = minApiLevel;
    this.outputMode = outputMode;
    this.libraryDesugaringTestConfiguration = LibraryDesugaringTestConfiguration.DISABLED;
  }

  TestCompileResult(
      TestState state,
      AndroidApp app,
      int minApiLevel,
      OutputMode outputMode,
      LibraryDesugaringTestConfiguration libraryDesugaringTestConfiguration) {
    super(state);
    this.app = app;
    this.minApiLevel = minApiLevel;
    this.outputMode = outputMode;
    this.libraryDesugaringTestConfiguration = libraryDesugaringTestConfiguration;
  }

  public CR addVmArguments(Collection<String> arguments) {
    vmArguments.addAll(arguments);
    return self();
  }

  public CR addVmArguments(String... arguments) {
    return addVmArguments(Arrays.asList(arguments));
  }

  public CR noVerify() {
    return addVmArguments("-noverify");
  }

  public CR applyIf(boolean condition, ThrowableConsumer<CR> thenConsumer) {
    return applyIf(condition, thenConsumer, result -> {});
  }

  public CR applyIf(
      boolean condition, ThrowableConsumer<CR> thenConsumer, ThrowableConsumer<CR> elseConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(self());
    } else {
      elseConsumer.acceptWithRuntimeException(self());
    }
    return self();
  }

  public final CR withArt6Plus64BitsLib() {
    withArt6Plus64BitsLib = true;
    return self();
  }

  public final AndroidApp getApp() {
    return app;
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

  public CR inspectDiagnosticMessages(Consumer<TestDiagnosticMessages> consumer) {
    consumer.accept(getDiagnosticMessages());
    return self();
  }

  public abstract Set<String> getMainDexClasses();

  public final CR inspectMainDexClasses(Consumer<Set<String>> consumer) {
    consumer.accept(getMainDexClasses());
    return self();
  }

  public final CR inspectMainDexClasses(BiConsumer<CodeInspector, Set<String>> consumer)
      throws IOException {
    consumer.accept(inspector(), getMainDexClasses());
    return self();
  }

  public abstract String getStdout();

  public abstract String getStderr();

  public OutputMode getOutputMode() {
    return outputMode;
  }

  protected abstract RR createRunResult(TestRuntime runtime, ProcessResult result);

  @Deprecated
  public RR run(Class<?> mainClass) throws ExecutionException, IOException {
    return run(mainClass.getTypeName());
  }

  @Deprecated
  public RR run(String mainClass) throws IOException {
    assert !libraryDesugaringTestConfiguration.isEnabled();
    ClassSubject mainClassSubject = inspector().clazz(mainClass);
    assertThat(mainClassSubject, isPresent());
    switch (getBackend()) {
      case DEX:
        return runArt(
            new DexRuntime(ToolHelper.getDexVm()),
            mainClassSubject.getFinalName());
      case CF:
        return runJava(
            TestRuntime.getDefaultJavaRuntime(),
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

  public RR run(TestRuntime runtime, String mainClass, String... args) throws IOException {
    assert getBackend() == runtime.getBackend();
    ClassSubject mainClassSubject = inspector().clazz(mainClass);
    if (!mainClassSubject.isPresent()) {
      for (Path classpathFile : additionalRunClassPath) {
        mainClassSubject = new CodeInspector(classpathFile).clazz(mainClass);
        if (mainClassSubject.isPresent()) {
          break;
        }
      }
    }
    assertThat("Did you forget a keep rule for the main method?", mainClassSubject, isPresent());
    if (runtime.isDex()) {
      return runArt(runtime, mainClassSubject.getFinalName(), args);
    }
    assert runtime.isCf();
    return runJava(
        runtime,
        ObjectArrays.concat(mainClassSubject.getFinalName(), args));
  }

  public RR runWithJaCoCo(Path output, TestRuntime runtime, String mainClass, String... args)
      throws IOException, ExecutionException {
    setSystemProperty("jacoco-agent.destfile", output.toString());
    setSystemProperty("jacoco-agent.dumponexit", "true");
    setSystemProperty("jacoco-agent.output", "file");
    return run(runtime, mainClass, args);
  }

  public CR addRunClasspathFiles(Path... classpath) {
    return addRunClasspathFiles(Arrays.asList(classpath));
  }

  public CR addRunClasspathFiles(List<Path> classpath) {
    additionalRunClassPath.addAll(classpath);
    return self();
  }

  public CR addRunClasspathClasses(Class<?>... classpath) throws Exception {
    return addRunClasspathClasses(Arrays.asList(classpath));
  }

  public CR addRunClasspathClasses(List<Class<?>> classpath) throws Exception {
    if (getBackend() == Backend.DEX) {
      return addRunClasspathFiles(
          testForD8(state.getTempFolder())
              .addProgramClasses(classpath)
              .setMinApi(minApiLevel)
              .compile()
              .writeToZip());
    }
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

  public CR addRunClasspathClassFileData(byte[]... classes) throws Exception {
    return addRunClasspathClassFileData(Arrays.asList(classes));
  }

  public CR addRunClasspathClassFileData(Collection<byte[]> classes) throws Exception {
    if (getBackend() == Backend.DEX) {
      additionalRunClassPath.add(
          testForD8(state.getTempFolder())
              .addProgramClassFileData(classes)
              .setMinApi(minApiLevel)
              .compile()
              .writeToZip());
      return self();
    }
    assert getBackend() == Backend.CF;
    try {
      AndroidApp.Builder appBuilder = AndroidApp.builder();
      for (byte[] clazz : classes) {
        appBuilder.addClassProgramData(
            clazz, Origin.unknown(), ImmutableSet.of(TestBase.extractClassDescriptor(clazz)));
      }
      Path path = state.getNewTempFolder().resolve("runtime-classes.jar");
      appBuilder.build().writeToZipForTesting(path, OutputMode.ClassFile);
      additionalRunClassPath.add(path);
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public CR addBootClasspathClasses(Class<?>... classes) throws Exception {
    return addBootClasspathClasses(Arrays.asList(classes));
  }

  public CR addBootClasspathClasses(List<Class<?>> classes) throws Exception {
    if (getBackend() == Backend.DEX) {
      additionalBootClasspath.add(
          testForD8(state.getTempFolder())
              .addProgramClasses(classes)
              .setMinApi(minApiLevel)
              .compile()
              .writeToZip());
      return self();
    }
    assert getBackend() == Backend.CF;
    try {
      Path path = state.getNewTempFolder().resolve("runtime-classes.jar");
      ArchiveConsumer consumer = new ArchiveConsumer(path);
      for (Class<?> clazz : classes) {
        consumer.accept(
            ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
            DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()),
            null);
      }
      consumer.finished(null);
      additionalBootClasspath.add(path);
      return self();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public CR addBootClasspathFiles(Path... files) {
    return addBootClasspathFiles(Arrays.asList(files));
  }

  public CR addBootClasspathFiles(Collection<Path> files) {
    additionalBootClasspath.addAll(files);
    return self();
  }

  public CR addDesugaredCoreLibraryRunClassPath(
      Function<AndroidApiLevel, Path> classPathSupplier, AndroidApiLevel minAPILevel) {
    addRunClasspathFiles(classPathSupplier.apply(minAPILevel));
    return self();
  }

  public CR addDesugaredCoreLibraryRunClassPath(
      TriFunction<AndroidApiLevel, String, Boolean, Path> classPathSupplier,
      AndroidApiLevel minAPILevel,
      String keepRules,
      boolean shrink) {
    addRunClasspathFiles(classPathSupplier.apply(minAPILevel, keepRules, shrink));
    return self();
  }

  public CR enableRuntimeAssertions() {
    assert getBackend() == Backend.CF;
    if (!vmArguments.contains("-ea")) {
      vmArguments.add("-ea");
    }
    return self();
  }

  public CR enableJVMPreview() {
    assert getBackend() == Backend.CF;
    if (!vmArguments.contains("--enable-preview")) {
      vmArguments.add("--enable-preview");
    }
    return self();
  }

  public CR enableRuntimeAssertions(boolean enable) {
    if (getBackend() == Backend.CF) {
      if (enable) {
        enableRuntimeAssertions();
      }
    } else {
      // Assertions cannot be enabled on dex VMs.
      assert !enable;
    }

    if (enable) {
      if (!this.vmArguments.contains("-ea")) {
        this.vmArguments.add("-ea");
      }
    }
    return self();
  }

  public CR setSystemProperty(String name, String value) {
    vmArguments.add("-D" + name + "=" + value);
    return self();
  }

  public CR writeSingleDexOutputToFile(Path file) throws IOException {
    assertTrue(getApp().getClassProgramResourcesForTesting().isEmpty());
    try {
      List<ProgramResource> dexProgramSources = getApp().getDexProgramResourcesForTesting();
      assertEquals(1, dexProgramSources.size());
      FileUtils.writeToFile(file, null, dexProgramSources.get(0).getBytes());
      return self();
    } catch (ResourceException e) {
      throw new IOException(e);
    }
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
    app.writeToZipForTesting(file, getOutputMode());
    return self();
  }

  public Path writeToDirectory() throws IOException {
    Path directory = state.getNewTempFolder();
    writeToDirectory(directory);
    return directory;
  }

  public CR writeToDirectory(Path directory) throws IOException {
    app.writeToDirectory(directory, getOutputMode());
    return self();
  }

  public CodeInspector inspector() throws IOException {
    return new CodeInspector(app);
  }

  public CodeInspector inspector(Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException {
    return new CodeInspector(app, debugOptionsConsumer);
  }

  public <E extends Throwable> CR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, E {
    consumer.accept(inspector());
    return self();
  }

  @SuppressWarnings("unchecked")
  public <E extends Throwable> CR inspectMultiDex(ThrowingConsumer<CodeInspector, E>... consumers)
      throws IOException, E {
    return inspectMultiDex(null, consumers);
  }

  @SafeVarargs
  public final <E extends Throwable> CR inspectMultiDex(
      Path mappingFile, ThrowingConsumer<CodeInspector, E>... consumers) throws IOException, E {
    Path out = state.getNewTempFolder();
    getApp().writeToDirectory(out, OutputMode.DexIndexed);
    consumers[0].accept(new CodeInspector(out.resolve("classes.dex"), mappingFile));
    for (int i = 1; i < consumers.length; i++) {
      Path dex = out.resolve("classes" + (i + 1) + ".dex");
      CodeInspector inspector =
          dex.toFile().exists() ? new CodeInspector(dex, mappingFile) : CodeInspector.empty();
      consumers[i].accept(inspector);
    }
    return self();
  }

  public <E extends Throwable> CR inspectWithOptions(
      ThrowingConsumer<CodeInspector, E> consumer, Consumer<InternalOptions> debugOptionsConsumer)
      throws IOException, E {
    consumer.accept(inspector(debugOptionsConsumer));
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

  public CR assertOnlyErrors() {
    getDiagnosticMessages().assertOnlyErrors();
    return self();
  }

  public CR assertDiagnosticThatMatches(Matcher<Diagnostic> matcher) {
    getDiagnosticMessages().assertDiagnosticThatMatches(matcher);
    return self();
  }

  public CR assertInfoThatMatches(Matcher<Diagnostic> matcher) {
    getDiagnosticMessages().assertInfoThatMatches(matcher);
    return self();
  }

  public CR assertInfoMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertInfoThatMatches(diagnosticMessage(matcher));
    return self();
  }

  public CR assertAllInfosMatch(Matcher<Diagnostic> matcher) {
    getDiagnosticMessages().assertNoInfosMatch(not(matcher));
    return self();
  }

  public CR assertAllInfoMessagesMatch(Matcher<String> matcher) {
    return assertNoInfoMessageThatMatches(not(matcher));
  }

  public CR assertNoInfoThatMatches(Matcher<Diagnostic> matcher) {
    getDiagnosticMessages().assertNoInfosMatch(matcher);
    return self();
  }

  public CR assertNoInfoMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertNoInfosMatch(diagnosticMessage(matcher));
    return self();
  }

  public CR assertAtLeastOneInfoMessage() {
    assertTrue(getDiagnosticMessages().getInfos().size() >= 1);
    return self();
  }

  public CR assertInfosCount(int count) {
    getDiagnosticMessages().assertInfosCount(count);
    return self();
  }

  public CR assertNoInfoMessages() {
    getDiagnosticMessages().assertNoInfos();
    return self();
  }

  public CR assertWarningMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertWarningMessageThatMatches(matcher);
    return self();
  }

  public CR assertWarningThatMatches(Matcher<Diagnostic> matcher) {
    getDiagnosticMessages().assertWarningThatMatches(matcher);
    return self();
  }

  public CR assertAllWarningMessagesMatch(Matcher<String> matcher) {
    getDiagnosticMessages().assertHasWarnings().assertAllWarningsMatch(diagnosticMessage(matcher));
    return self();
  }

  public CR assertNoWarningMessages() {
    getDiagnosticMessages().assertNoWarnings();
    return self();
  }

  public CR assertNoWarningMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertNoWarningsMatch(diagnosticMessage(matcher));
    return self();
  }

  public CR assertErrorMessageThatMatches(Matcher<String> matcher) {
    getDiagnosticMessages().assertErrorMessageThatMatches(matcher);
    return self();
  }

  public CR assertNoErrorMessages() {
    getDiagnosticMessages().assertNoErrors();
    return self();
  }

  public CR assertNoStdout() {
    assertEquals("", getStdout());
    return self();
  }

  public CR assertStdoutThatMatches(Matcher<String> matcher) {
    assertThat(getStdout(), matcher);
    return self();
  }

  public CR assertNoStderr() {
    assertEquals("", getStderr());
    return self();
  }

  public CR assertStderrThatMatches(Matcher<String> matcher) {
    assertThat(getStderr(), matcher);
    return self();
  }

  public CR disassemble(PrintStream ps) throws IOException {
    ToolHelper.disassemble(app, ps);
    return self();
  }

  public CR disassemble() throws IOException, ExecutionException {
    return disassemble(System.out);
  }

  @Deprecated
  public DebugTestConfig debugConfig() {
    return debugConfig(
        getBackend().isCf()
            ? TestRuntime.getDefaultCfRuntime()
            : new DexRuntime(ToolHelper.getDexVm()));
  }

  public DebugTestConfig debugConfig(TestRuntime runtime) {
    assert runtime.getBackend() == getBackend();
    // Rethrow exceptions since debug config is usually used in a delayed wrapper which
    // does not declare exceptions.
    try {
      Path out = state.getNewTempFolder().resolve("out.zip");
      app.writeToZipForTesting(out, getOutputMode());
      return DebugTestConfig.create(runtime, out);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private RR runJava(TestRuntime runtime, String... arguments) throws IOException {
    assert runtime != null;
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZipForTesting(out, OutputMode.ClassFile);
    List<Path> classPath =
        ImmutableList.<Path>builder().addAll(additionalRunClassPath).add(out).build();
    ProcessResult result =
        ToolHelper.runJava(
            runtime.asCf(), vmArguments, additionalBootClasspath, classPath, arguments);
    return createRunResult(runtime, result);
  }

  RR runArt(TestRuntime runtime, String mainClass, String... arguments) throws IOException {
    DexVm vm = runtime.asDex().getVm();
    // TODO(b/127785410): Always assume a non-null runtime.
    Path out = state.getNewTempFolder().resolve("out.zip");
    app.writeToZipForTesting(out, OutputMode.DexIndexed);
    List<String> classPath =
        ImmutableList.<String>builder()
            .addAll(
                additionalRunClassPath.stream().map(Path::toString).collect(Collectors.toList()))
            .add(out.toString())
            .build();
    Consumer<ArtCommandBuilder> commandConsumer =
        withArt6Plus64BitsLib && vm.getVersion().isNewerThanOrEqual(DexVm.Version.V6_0_1)
            ? builder -> builder.appendArtOption("--64")
            : builder -> {};
    commandConsumer =
        commandConsumer.andThen(
            builder -> {
              if (!additionalBootClasspath.isEmpty()) {
                DexVm dexVm = runtime.asDex().getVm();
                if (dexVm.isNewerThan(DexVm.ART_4_4_4_HOST)) {
                  builder.appendArtOption("-Ximage:/system/non/existent/image.art");
                  builder.appendArtOption("-Xnoimage-dex2oat");
                }
                try {
                  for (String s : ToolHelper.getBootLibs(dexVm)) {
                    builder.appendBootClasspath(new File(s).getCanonicalPath());
                  }
                } catch (Exception e) {
                  throw new RuntimeException();
                }
                additionalBootClasspath.forEach(
                    path -> builder.appendBootClasspath(path.toString()));
              }
              for (String vmArgument : vmArguments) {
                builder.appendArtOption(vmArgument);
              }
            });
    ProcessResult result =
        ToolHelper.runArtRaw(classPath, mainClass, commandConsumer, vm, true, arguments);
    return createRunResult(runtime, result);
  }

  public Dex2OatTestRunResult runDex2Oat(TestRuntime runtime) throws IOException {
    assert getBackend() == DEX;
    DexVm vm = runtime.asDex().getVm();
    Path tmp = state.getNewTempFolder();
    Path jarFile = tmp.resolve("out.jar");
    Path oatFile = tmp.resolve("out.oat");
    app.writeToZipForTesting(jarFile, OutputMode.DexIndexed);
    return new Dex2OatTestRunResult(
        app, runtime, ToolHelper.runDex2OatRaw(jarFile, oatFile, vm), state);
  }

  public CR benchmarkCodeSize(BenchmarkResults results) throws IOException {
    Path out = writeToZip();
    Box<Long> size = new Box<>(0L);
    ZipUtils.iter(
        out,
        (entry, stream) -> {
          if ((getBackend().isDex() && entry.getName().endsWith(".dex"))
              || getBackend().isCf() && entry.getName().endsWith(".class")) {
            size.set(size.get() + entry.getSize());
          }
        });
    results.addCodeSizeResult(size.get());
    return self();
  }
}
