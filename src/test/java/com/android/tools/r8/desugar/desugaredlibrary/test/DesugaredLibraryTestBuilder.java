// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestBuilder;
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
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.tracereferences.TraceReferences;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.Assume;

public class DesugaredLibraryTestBuilder<T extends DesugaredLibraryTestBase> {

  private final T test;
  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> builder;
  private List<ArtProfileForRewriting> l8ArtProfilesForRewriting = new ArrayList<>();
  private String l8ExtraKeepRules = "";
  private Consumer<InternalOptions> l8OptionModifier = ConsumerUtils.emptyConsumer();
  private boolean l8FinalPrefixVerification = true;
  private boolean overrideDefaultLibrary = false;
  private CustomLibrarySpecification customLibrarySpecification = null;
  private TestingKeepRuleConsumer keepRuleConsumer = null;
  private List<ExternalArtProfile> l8ResidualArtProfiles = new ArrayList<>();
  private boolean managedPostPrefix = false;

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
    setUp();
  }

  private void setUp() {
    builder.setMinApi(parameters).setMode(compilationSpecification.getProgramCompilationMode());
    LibraryDesugaringTestConfiguration.Builder libraryConfBuilder =
        LibraryDesugaringTestConfiguration.builder()
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(libraryDesugaringSpecification.getSpecification()));
    if (compilationSpecification.isL8Shrink() && !compilationSpecification.isCfToCf()) {
      keepRuleConsumer = new TestingKeepRuleConsumer();
      libraryConfBuilder.setKeepRuleConsumer(keepRuleConsumer);
    }
    builder.enableCoreLibraryDesugaring(libraryConfBuilder.build());
  }

  private TestCompilerBuilder<?, ?, ?, ? extends SingleTestRunResult<?>, ?> generateBuilder() {
    if (compilationSpecification.isCfToCf()) {
      assert !compilationSpecification.isProgramShrink();
      if (compilationSpecification.isL8Shrink()) {
        // L8 with Cf backend and shrinking is not a supported pipeline.
        Assume.assumeTrue(parameters.getBackend().isDex());
      }
      return test.testForD8(Backend.CF);
    }
    // Cf back-end is only allowed in Cf to cf compilations.
    Assume.assumeTrue(parameters.getBackend().isDex());
    if (compilationSpecification.isProgramShrink()) {
      return test.testForR8(parameters.getBackend());
    }
    return test.testForD8(Backend.DEX);
  }

  public DesugaredLibraryTestBuilder<T> setCustomLibrarySpecification(
      CustomLibrarySpecification customLibrarySpecification) {
    this.customLibrarySpecification = customLibrarySpecification;
    customLibrarySpecification.addLibraryClasses(builder);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addL8OptionsModification(
      Consumer<InternalOptions> optionModifier) {
    l8OptionModifier = l8OptionModifier.andThen(optionModifier);
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

  public DesugaredLibraryTestBuilder<T> addClasspathClasses(Class<?>... clazz) {
    builder.addClasspathClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClasses(Class<?>... clazz) {
    builder.addProgramClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClasses(Collection<Class<?>> clazz) {
    builder.addProgramClasses(clazz);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramFiles(Path... files) {
    builder.addProgramFiles(files);
    return this;
  }

  /**
   * By default the compilation uses as library libraryDesugaringSpecification.getLibraryFiles(),
   * which is android.jar at the required compilation api level. Use this Api to set different
   * library files.
   */
  public DesugaredLibraryTestBuilder<T> overrideLibraryFiles(Path... files) {
    overrideDefaultLibrary = true;
    builder.addLibraryFiles(files);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> overrideLibraryProvider(
      ClassFileResourceProvider provider) {
    overrideDefaultLibrary = true;
    builder.addLibraryProvider(provider);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramFiles(Collection<Path> files) {
    builder.addProgramFiles(files);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> setL8PostPrefix(String postPrefix) {
    System.setProperty("com.android.tools.r8.desugaredLibraryPostPrefix", postPrefix);
    this.managedPostPrefix = true;
    return this;
  }

  public DesugaredLibraryTestBuilder<T> ignoreL8FinalPrefixVerification() {
    l8FinalPrefixVerification = false;
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public DesugaredLibraryTestBuilder<T> addProgramClassFileData(
      Collection<byte[]> programClassFileData) {
    builder.addProgramClassFileData(programClassFileData);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addRunClasspathFiles(Path... files) {
    builder.addRunClasspathFiles(files);
    return this;
  }

  /**
   * By default the compilation uses libraryDesugaringSpecification.getProgramCompilationMode()
   * which maps to the studio set-up: D8-debug, D8-release and R8-release. Use this Api to set a
   * different compilation mode.
   */
  public DesugaredLibraryTestBuilder<T> overrideCompilationMode(CompilationMode mode) {
    builder.setMode(mode);
    return this;
  }

  private void withD8TestBuilder(Consumer<D8TestBuilder> consumer) {
    if (!builder.isD8TestBuilder()) {
      return;
    }
    consumer.accept((D8TestBuilder) builder);
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

  public DesugaredLibraryTestBuilder<T> allowUnusedDontWarnKotlinReflectJvmInternal(boolean allow) {
    withR8TestBuilder(b -> b.allowUnusedDontWarnKotlinReflectJvmInternal(allow));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowDiagnosticInfoMessages() {
    withR8TestBuilder(R8TestBuilder::allowDiagnosticInfoMessages);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> applyIfD8TestBuilder(Consumer<D8TestBuilder> consumer) {
    withD8TestBuilder(consumer);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> applyIfR8TestBuilder(Consumer<R8TestBuilder<?>> consumer) {
    withR8TestBuilder(consumer);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> allowDiagnosticWarningMessages() {
    withR8TestBuilder(R8TestBuilder::allowDiagnosticWarningMessages);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepRules(String keepRules) {
    withR8TestBuilder(b -> b.addKeepRules(keepRules));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addL8KeepRules(String keepRules) {
    if (compilationSpecification.isL8Shrink()) {
      l8ExtraKeepRules += keepRules + "\n";
    }
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addKeepClassAndMembersRules(Class<?>... clazz) {
    withR8TestBuilder(b -> b.addKeepClassAndMembersRules(clazz));
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

  public DesugaredLibraryTestBuilder<T> enableNeverClassInliningAnnotations() {
    withR8TestBuilder(R8TestBuilder::enableNeverClassInliningAnnotations);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> enableInliningAnnotations() {
    withR8TestBuilder(R8TestBuilder::enableInliningAnnotations);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> enableNoVerticalClassMergingAnnotations() {
    withR8TestBuilder(R8TestBuilder::enableNoVerticalClassMergingAnnotations);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> addVerticallyMergedClassesInspector(
      Consumer<VerticallyMergedClassesInspector> inspector) {
    withR8TestBuilder(b -> b.addVerticallyMergedClassesInspector(inspector));
    return this;
  }

  public DesugaredLibraryTestBuilder<T> noMinification() {
    withR8TestBuilder(R8TestBuilder::addDontObfuscate);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> enableConstantArgumentAnnotations() {
    withR8TestBuilder(R8TestBuilder::enableConstantArgumentAnnotations);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> applyOnBuilder(
      Consumer<TestCompilerBuilder<?, ?, ?, ?, ?>> consumer) {
    consumer.accept(builder);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> apply(Consumer<DesugaredLibraryTestBuilder<T>> consumer) {
    consumer.accept(this);
    return this;
  }

  public DesugaredLibraryTestBuilder<T> applyIf(
      boolean apply, Consumer<DesugaredLibraryTestBuilder<T>> consumer) {
    if (apply) {
      return apply(consumer);
    }
    return this;
  }

  public DesugaredLibraryTestBuilder<T> disableL8AnnotationRemoval() {
    l8OptionModifier =
        l8OptionModifier.andThen(options -> options.disableL8AnnotationRemoval = true);
    return this;
  }

  private void prepareCompilation() {
    if (overrideDefaultLibrary) {
      return;
    }
    builder.addLibraryFiles(libraryDesugaringSpecification.getLibraryFiles());
  }

  public DesugaredLibraryTestCompileResult<T> compile()
      throws CompilationFailedException, IOException, ExecutionException {
    prepareCompilation();
    TestCompileResult<?, ? extends SingleTestRunResult<?>> compile = builder.compile();
    return internalCompile(compile);
  }

  public DesugaredLibraryTestCompileResult<T> compileWithExpectedDiagnostics(
      DiagnosticsConsumer consumer)
      throws CompilationFailedException, IOException, ExecutionException {
    prepareCompilation();
    TestCompileResult<?, ? extends SingleTestRunResult<?>> compile =
        builder.compileWithExpectedDiagnostics(consumer);
    return internalCompile(compile);
  }

  private DesugaredLibraryTestCompileResult<T> internalCompile(
      TestCompileResult<?, ? extends SingleTestRunResult<?>> compile)
      throws CompilationFailedException, IOException, ExecutionException {
    L8TestCompileResult l8Compile = compileDesugaredLibrary(compile, keepRuleConsumer);
    D8TestCompileResult customLibCompile = compileCustomLib();
    if (managedPostPrefix) {
      System.clearProperty("com.android.tools.r8.desugaredLibraryPostPrefix");
    }
    return new DesugaredLibraryTestCompileResult<>(
        test,
        compile,
        parameters,
        libraryDesugaringSpecification,
        compilationSpecification,
        customLibCompile,
        l8Compile,
        l8ResidualArtProfiles);
  }

  private D8TestCompileResult compileCustomLib() throws CompilationFailedException {
    if (customLibrarySpecification == null) {
      return null;
    }
    return customLibrarySpecification.compileCustomLibrary(test.testForD8(parameters.getBackend()));
  }

  private L8TestCompileResult compileDesugaredLibrary(
      TestCompileResult<?, ? extends SingleTestRunResult<?>> compile,
      KeepRuleConsumer keepRuleConsumer)
      throws CompilationFailedException, IOException, ExecutionException {
    if (!compilationSpecification.isL8Shrink()) {
      return compileDesugaredLibrary(null);
    }
    if (!compilationSpecification.isTraceReferences()) {
      // When going to dex we can get the generated keep rule through the keep rule consumer.
      assert keepRuleConsumer != null;
      return compileDesugaredLibrary(keepRuleConsumer.get());
    }
    L8TestCompileResult nonShrunk =
        test.testForL8(parameters.getApiLevel(), Backend.CF)
            .apply(libraryDesugaringSpecification::configureL8TestBuilder)
            .apply(b -> configure(b, Backend.CF))
            .compile();
    String keepRules =
        collectKeepRulesWithTraceReferences(compile.writeToZip(), nonShrunk.writeToZip());
    return compileDesugaredLibrary(keepRules);
  }

  private L8TestCompileResult compileDesugaredLibrary(String keepRule)
      throws CompilationFailedException, IOException, ExecutionException {
    assert !compilationSpecification.isL8Shrink() || keepRule != null;
    return test.testForL8(parameters.getApiLevel(), parameters.getBackend())
        .apply(
            b ->
                libraryDesugaringSpecification.configureL8TestBuilder(
                    b, compilationSpecification.isL8Shrink(), keepRule))
        .apply(b -> configure(b, parameters.getBackend()))
        .compile();
  }

  private void configure(L8TestBuilder l8Builder, Backend backend) {
    l8Builder
        .applyIf(!l8FinalPrefixVerification, L8TestBuilder::ignoreFinalPrefixVerification)
        .applyIf(
            compilationSpecification.isL8Shrink() && !backend.isCf() && !l8ExtraKeepRules.isEmpty(),
            b -> b.addKeepRules(l8ExtraKeepRules))
        .addOptionsModifier(l8OptionModifier);
    for (ArtProfileForRewriting artProfileForRewriting : l8ArtProfilesForRewriting) {
      l8Builder.addArtProfileForRewriting(
          artProfileForRewriting.getArtProfileProvider(),
          artProfileForRewriting.getResidualArtProfileConsumer());
    }
  }

  public String collectKeepRulesWithTraceReferences(
      Path desugaredProgramClassFile, Path desugaredLibraryClassFile)
      throws CompilationFailedException, IOException {
    Path generatedKeepRules = test.temp.newFile().toPath();
    ArrayList<String> args = new ArrayList<>();
    args.add("--keep-rules");
    for (Path libraryFile : libraryDesugaringSpecification.getLibraryFiles()) {
      args.add("--lib");
      args.add(libraryFile.toString());
    }
    args.add("--target");
    args.add(desugaredLibraryClassFile.toString());
    args.add("--source");
    args.add(desugaredProgramClassFile.toString());
    args.add("--output");
    args.add(generatedKeepRules.toString());
    args.add("--map-diagnostics");
    args.add("error");
    args.add("info");
    TraceReferences.run(args.toArray(new String[0]));
    return FileUtils.readTextFile(generatedKeepRules, Charsets.UTF_8);
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws ExecutionException, IOException, CompilationFailedException {
    return compile().run(runtime, mainClass.getTypeName(), args);
  }

  public SingleTestRunResult<?> run(TestRuntime runtime, String mainClass, String... args)
      throws ExecutionException, IOException, CompilationFailedException {
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

  public DesugaredLibraryTestBuilder<T> addL8ArtProfileForRewriting(
      ArtProfileProvider artProfileProvider) {
    return addL8ArtProfileForRewriting(
        artProfileProvider,
        ArtProfileTestingUtils.createResidualArtProfileConsumer(l8ResidualArtProfiles::add));
  }

  public DesugaredLibraryTestBuilder<T> addL8ArtProfileForRewriting(ExternalArtProfile artProfile) {
    return addL8ArtProfileForRewriting(ArtProfileTestingUtils.createArtProfileProvider(artProfile));
  }

  public DesugaredLibraryTestBuilder<T> addL8ArtProfileForRewriting(
      ArtProfileProvider artProfileProvider, ArtProfileConsumer residualArtProfileConsumer) {
    l8ArtProfilesForRewriting.add(
        new ArtProfileForRewriting(artProfileProvider, residualArtProfileConsumer));
    return this;
  }
}
