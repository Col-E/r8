// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.L8TestCompileResult;
import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestCompilerBuilder;
import com.android.tools.r8.TestCompilerBuilder.DiagnosticsConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Assume;

public class DesugaredLibraryTestBuilder<T extends DesugaredLibraryTestBase> {

  private final T test;
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> builder;
  private final L8TestBuilder l8Builder;

  private CustomLibrarySpecification customLibrarySpecification = null;
  private TestingKeepRuleConsumer keepRuleConsumer = null;

  public DesugaredLibraryTestBuilder(
      T test,
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification runSpecification) {
    this.test = test;
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = runSpecification;
    this.builder = generateBuilder();
    this.l8Builder = generateL8Builder();
    setUp();
  }

  private void setUp() {
    builder
        .addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles())
        .setMinApi(parameters.getApiLevel());
    LibraryDesugaringTestConfiguration.Builder libraryConfBuilder =
        LibraryDesugaringTestConfiguration.builder()
            .setMinApi(parameters.getApiLevel())
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(libraryDesugaringSpecification.getSpecification()))
            .dontAddRunClasspath();
    if (compilationSpecification.isL8Shrink()) {
      keepRuleConsumer = new TestingKeepRuleConsumer();
      libraryConfBuilder.setKeepRuleConsumer(keepRuleConsumer);
    }
    builder.enableCoreLibraryDesugaring(libraryConfBuilder.build());
  }

  private TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> generateBuilder() {
    if (compilationSpecification.isCfToCf()) {
      assert !compilationSpecification.isProgramShrink();
      return test.testForD8(Backend.CF);
    }
    // Cf back-end is only allowed in Cf to cf compilations.
    Assume.assumeTrue(parameters.getBackend().isDex());
    if (compilationSpecification.isProgramShrink()) {
      return test.testForR8(parameters.getBackend());
    }
    return test.testForD8(Backend.DEX);
  }

  private L8TestBuilder generateL8Builder() {
    return test.testForL8(parameters.getApiLevel(), parameters.getBackend());
  }

  public DesugaredLibraryTestBuilder<T> setCustomLibrarySpecification(
      CustomLibrarySpecification customLibrarySpecification) {
    this.customLibrarySpecification = customLibrarySpecification;
    builder.addLibraryClasses(customLibrarySpecification.getClasses());
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addL8OptionsModification(
      Consumer<InternalOptions> optionModifier) {
    l8Builder.addOptionsModifier(optionModifier);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addOptionsModification(
      Consumer<InternalOptions> optionModifier) {
    builder.addOptionsModification(optionModifier);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClassesAndInnerClasses(Class<?>... clazz)
      throws IOException {
    builder.addProgramClassesAndInnerClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addInnerClasses(Class<?>... clazz) throws IOException {
    builder.addInnerClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClasses(Class<?>... clazz) throws IOException {
    builder.addProgramClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClasses(Collection<Class<?>> clazz)
      throws IOException {
    builder.addProgramClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramFiles(Path... files) {
    builder.addProgramFiles(files);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramFiles(Collection<Path> files) {
    builder.addProgramFiles(files);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> ignoreL8FinalPrefixVerification() {
    l8Builder.ignoreFinalPrefixVerification();
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClassFileData(
      Collection<byte[]> programClassFileData) {
    builder.addProgramClassFileData(programClassFileData);
    return this;
  }

  private void withR8TestBuilder(Consumer<R8TestBuilder<?>> consumer) {
    if (!builder.isTestShrinkerBuilder()) {
      return;
    }
    consumer.accept((R8TestBuilder<?>) builder);
  }

  public DesugaredLibraryTestBuilder<T> allowUnusedDontWarnPatterns() {
    withR8TestBuilder(R8TestBuilder::allowUnusedDontWarnPatterns);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowUnusedProguardConfigurationRules() {
    withR8TestBuilder(R8TestBuilder::allowUnusedProguardConfigurationRules);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowDiagnosticMessages() {
    withR8TestBuilder(R8TestBuilder::allowDiagnosticMessages);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowDiagnosticInfoMessages() {
    withR8TestBuilder(R8TestBuilder::allowDiagnosticInfoMessages);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowDiagnosticWarningMessages() {
    withR8TestBuilder(R8TestBuilder::allowDiagnosticWarningMessages);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepAttributes(String... attributes) {
    withR8TestBuilder(b -> b.addKeepAttributes(attributes));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepAllClassesRuleWithAllowObfuscation() {
    withR8TestBuilder(TestShrinkerBuilder::addKeepAllClassesRuleWithAllowObfuscation);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepAllClassesRule() {
    withR8TestBuilder(TestShrinkerBuilder::addKeepAllClassesRule);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepMainRule(Class<?> clazz) {
    withR8TestBuilder(b -> b.addKeepMainRule(clazz));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepMainRule(String mainClass) {
    withR8TestBuilder(b -> b.addKeepMainRule(mainClass));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepRuleFiles(Path... files) {
    withR8TestBuilder(b -> b.addKeepRuleFiles(files));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addFeatureSplit(
      Function<FeatureSplit.Builder, FeatureSplit> featureSplitBuilder) {
    withR8TestBuilder(b -> b.addFeatureSplit(featureSplitBuilder));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> enableInliningAnnotations() {
    withR8TestBuilder(R8TestBuilder::enableInliningAnnotations);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> noMinification() {
    withR8TestBuilder(R8TestBuilder::noMinification);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> applyIf(
      boolean apply, Consumer<DesugaredLibraryTestBuilder<T>> consumer) {
    if (apply) {
      consumer.accept(this);
    }
    return this;
  }

  public DesugaredLibraryTestBuilder<T> disableL8AnnotationRemoval() {
    l8Builder.setDisableL8AnnotationRemoval(true);
    return this;
  }

  public DesugaredLibraryTestCompileResult<T> compile() throws Exception {
    TestCompileResult<?, ? extends SingleTestRunResult<?>> compile = builder.compile();
    return internalCompile(compile);
  }

  public DesugaredLibraryTestCompileResult<T> compileWithExpectedDiagnostics(
      DiagnosticsConsumer consumer) throws Exception {
    TestCompileResult<?, ? extends SingleTestRunResult<?>> compile =
        builder.compileWithExpectedDiagnostics(consumer);
    return internalCompile(compile);
  }

  private DesugaredLibraryTestCompileResult<T> internalCompile(
      TestCompileResult<?, ? extends SingleTestRunResult<?>> compile) throws Exception {
    String keepRule = keepRuleConsumer == null ? null : keepRuleConsumer.get();
    L8TestCompileResult l8Compile = compileDesugaredLibrary(keepRule);
    D8TestCompileResult customLibCompile = compileCustomLib();
    return new DesugaredLibraryTestCompileResult<>(
        test,
        compile,
        parameters,
        libraryDesugaringSpecification,
        compilationSpecification,
        customLibCompile,
        l8Compile);
  }

  private D8TestCompileResult compileCustomLib() throws CompilationFailedException {
    if (customLibrarySpecification == null) {
      return null;
    }
    return test.testForD8(parameters.getBackend())
        .addProgramClasses(customLibrarySpecification.getClasses())
        .setMinApi(customLibrarySpecification.getMinApi())
        .compile();
  }

  private L8TestCompileResult compileDesugaredLibrary(String keepRule) throws Exception {
    return l8Builder
        .apply(
            b ->
                libraryDesugaringSpecification.configureL8TestBuilder(
                    b, compilationSpecification.isL8Shrink(), keepRule))
        .compile();
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws Exception {
    return compile().run(runtime, mainClass.getTypeName(), args);
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, String mainClass, String... args)
      throws Exception {
    return compile().run(runtime, mainClass, args);
  }

  public DesugaredLibraryTestBuilder<T> supportAllCallbacksFromLibrary(
      boolean supportAllCallbacksFromLibrary) {
    addL8OptionsModification(supportLibraryCallbackConsumer(supportAllCallbacksFromLibrary, true));
    builder.addOptionsModification(
        supportLibraryCallbackConsumer(supportAllCallbacksFromLibrary, false));
    return this;
  }

  private Consumer<InternalOptions> supportLibraryCallbackConsumer(
      boolean supportAllCallbacksFromLibrary, boolean libraryCompilation) {
    return opt ->
        opt.setDesugaredLibrarySpecification(
            DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecificationforTesting(
                StringResource.fromFile(libraryDesugaringSpecification.getSpecification()),
                opt.dexItemFactory(),
                opt.reporter,
                libraryCompilation,
                parameters.getApiLevel().getLevel(),
                builder ->
                    builder.setSupportAllCallbacksFromLibrary(supportAllCallbacksFromLibrary)));
  }

  public DesugaredLibraryTestBuilder<T> addAndroidBuildVersion() {
    builder.addAndroidBuildVersion();
    return this;
  }

  public DesugaredLibraryTestBuilder<T> disableDesugaring() {
    builder.disableDesugaring();
    return this;
  }
}
