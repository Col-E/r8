// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.DETERMINISTIC_DEBUGGING;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.DexFileOverflowDiagnostic;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AssertionConfigurationWithDefault;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ProgramClassCollection;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/** Immutable command structure for an invocation of the {@link L8} library compiler. */
@KeepForApi
public final class L8Command extends BaseCompilerCommand {

  private final D8Command d8Command;
  private final R8Command r8Command;
  private final DesugaredLibrarySpecification desugaredLibrarySpecification;
  private final DexItemFactory factory;

  boolean isShrinking() {
    return r8Command != null;
  }

  D8Command getD8Command() {
    return d8Command;
  }

  R8Command getR8Command() {
    return r8Command;
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin) {
    return L8CommandParser.parse(args, origin);
  }

  /**
   * Parse the L8 command-line.
   *
   * <p>Parsing will set the supplied options or their default value if they have any.
   *
   * @param args Command-line arguments array.
   * @param origin Origin description of the command-line arguments.
   * @param handler Custom defined diagnostics handler.
   * @return L8 command builder with state set up according to parsed command line.
   */
  public static Builder parse(String[] args, Origin origin, DiagnosticsHandler handler) {
    return L8CommandParser.parse(args, origin, handler);
  }

  private L8Command(
      R8Command r8Command,
      D8Command d8Command,
      AndroidApp inputApp,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter diagnosticsHandler,
      boolean encodeChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      DesugaredLibrarySpecification desugaredLibrarySpecification,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
      ClassConflictResolver classConflictResolver,
      CancelCompilationChecker cancelCompilationChecker,
      DexItemFactory factory) {
    super(
        inputApp,
        mode,
        programConsumer,
        mainDexListConsumer,
        minApiLevel,
        diagnosticsHandler,
        DesugarState.ON,
        false,
        encodeChecksum,
        dexClassChecksumFilter,
        assertionsConfiguration,
        outputInspections,
        threadCount,
        dumpInputFlags,
        mapIdProvider,
        null,
        false,
        Collections.emptyList(),
        Collections.emptyList(),
        classConflictResolver,
        cancelCompilationChecker);
    this.d8Command = d8Command;
    this.r8Command = r8Command;
    this.desugaredLibrarySpecification = desugaredLibrarySpecification;
    this.factory = factory;
  }

  private L8Command(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    r8Command = null;
    d8Command = null;
    desugaredLibrarySpecification = null;
    factory = null;
  }

  protected static class DefaultL8DiagnosticsHandler implements DiagnosticsHandler {

    @Override
    public void error(Diagnostic error) {
      if (error instanceof DexFileOverflowDiagnostic) {
        DexFileOverflowDiagnostic overflowDiagnostic = (DexFileOverflowDiagnostic) error;
        DiagnosticsHandler.super.error(
            new StringDiagnostic(
                overflowDiagnostic.getDiagnosticMessage()
                    + ". Library too large. L8 can only produce a single .dex file"));
        return;
      }
      DiagnosticsHandler.super.error(error);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static Builder builder(DiagnosticsHandler diagnosticsHandler) {
    return new Builder(diagnosticsHandler);
  }

  @Override
  List<ArtProfileForRewriting> getArtProfilesForRewriting() {
    if (getD8Command() != null) {
      return getD8Command().getArtProfilesForRewriting();
    }
    if (getR8Command() != null) {
      return getR8Command().getArtProfilesForRewriting();
    }
    return Collections.emptyList();
  }

  @Override
  InternalOptions getInternalOptions() {
    InternalOptions internal = new InternalOptions(factory, getReporter());
    assert !internal.debug;
    internal.debug = getMode() == CompilationMode.DEBUG;
    assert internal.mainDexListConsumer == null;
    assert !internal.minimalMainDex;
    internal.setMinApiLevel(AndroidApiLevel.getAndroidApiLevel(getMinApiLevel()));
    assert !internal.intermediate;
    assert internal.retainCompileTimeAnnotations;
    internal.programConsumer = getProgramConsumer();
    assert internal.programConsumer instanceof ClassFileConsumer;

    // Assert and fixup defaults.
    assert !internal.isShrinking();
    assert !internal.isMinifying();
    assert !internal.passthroughDexCode;

    // Assert some of R8 optimizations are disabled.
    assert !internal.inlinerOptions().enableInlining;
    assert !internal.enableClassInlining;
    assert internal.getVerticalClassMergerOptions().isDisabled();
    assert !internal.enableEnumValueOptimization;
    assert !internal.outline.enabled;
    assert !internal.enableTreeShakingOfLibraryMethodOverrides;

    internal.horizontalClassMergerOptions().disable();

    assert internal.desugarState == DesugarState.ON;
    assert internal.enableInheritanceClassInDexDistributor;
    internal.enableInheritanceClassInDexDistributor = false;

    internal.configureDesugaredLibrary(
        desugaredLibrarySpecification,
        desugaredLibrarySpecification.getSynthesizedLibraryClassesPackagePrefix());

    // Default is to remove all javac generated assertion code when generating dex.
    assert internal.assertionsConfiguration == null;
    internal.assertionsConfiguration =
        new AssertionConfigurationWithDefault(
            AssertionsConfiguration.builder(getReporter())
                .setCompileTimeDisable()
                .setScopeAll()
                .build(),
            getAssertionsConfiguration());

    internal.programClassConflictResolver =
        ProgramClassCollection.wrappedConflictResolver(
            getClassConflictResolver(), internal.reporter);

    internal.cancelCompilationChecker = getCancelCompilationChecker();

    if (!DETERMINISTIC_DEBUGGING) {
      assert internal.threadCount == ThreadUtils.NOT_SPECIFIED;
      internal.threadCount = getThreadCount();
    }

    // Disable global optimizations.
    internal.disableGlobalOptimizations();
    internal.apiModelingOptions().disableApiCallerIdentification();
    internal.apiModelingOptions().disableOutliningAndStubbing();

    internal.setDumpInputFlags(getDumpInputFlags());
    internal.dumpOptions = dumpOptions();

    return internal;
  }

  /**
   * Builder for constructing a L8Command.
   *
   * <p>A builder is obtained by calling {@link L8Command#builder}.
   */
  @KeepForApi
  public static class Builder extends BaseCompilerCommand.Builder<L8Command, Builder> {

    private final List<Pair<List<String>, Origin>> proguardConfigStrings = new ArrayList<>();
    private final List<Path> proguardConfigFiles = new ArrayList<>();

    private Builder() {
      this(new DefaultL8DiagnosticsHandler());
    }

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
    }

    public boolean isShrinking() {
      // Answers true if keep rules, even empty, are provided.
      return !proguardConfigStrings.isEmpty() || !proguardConfigFiles.isEmpty();
    }

    @Override
    Builder self() {
      return this;
    }

    @Override
    CompilationMode defaultCompilationMode() {
      return CompilationMode.DEBUG;
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(Path... paths) {
      Collections.addAll(proguardConfigFiles, paths);
      return self();
    }

    /** Add proguard configuration-file resources. */
    public Builder addProguardConfigurationFiles(List<Path> paths) {
      proguardConfigFiles.addAll(paths);
      return self();
    }

    /** Add proguard configuration. */
    public Builder addProguardConfiguration(List<String> lines, Origin origin) {
      proguardConfigStrings.add(new Pair<>(lines, origin));
      return self();
    }

    /**
     * Set an output destination to which proguard-map content should be written.
     *
     * <p>This is a short-hand for setting a {@link StringConsumer.FileConsumer} using {@link
     * #setProguardMapConsumer}. Note that any subsequent call to this method or {@link
     * #setProguardMapConsumer} will override the previous setting.
     *
     * @param proguardMapOutput File-system path to write output at.
     */
    @Override
    public Builder setProguardMapOutputPath(Path proguardMapOutput) {
      return super.setProguardMapOutputPath(proguardMapOutput);
    }

    /**
     * Set a consumer for receiving the proguard-map content.
     *
     * <p>Note that any subsequent call to this method or {@link #setProguardMapOutputPath} will
     * override the previous setting.
     *
     * @param proguardMapConsumer Consumer to receive the content once produced.
     */
    @Override
    public Builder setProguardMapConsumer(StringConsumer proguardMapConsumer) {
      return super.setProguardMapConsumer(proguardMapConsumer);
    }

    @Override
    public Builder setAndroidPlatformBuild(boolean isAndroidPlatformBuild) {
      throw getReporter().fatalError("L8 does not support configuring Android platform builds.");
    }

    @Override
    void validate() {
      if (isPrintHelp()) {
        return;
      }
      Reporter reporter = getReporter();
      boolean isGeneratingClassFiles = getProgramConsumer() instanceof ClassFileConsumer;
      if (!hasDesugaredLibraryConfiguration()) {
        reporter.error("L8 requires a desugared library configuration");
      }
      if (getProgramConsumer() instanceof DexFilePerClassFileConsumer) {
        reporter.error("L8 does not support compiling to dex per class");
      }
      if (getAppBuilder().hasMainDexList()) {
        reporter.error("L8 does not support a main dex list");
      } else if (getMainDexListConsumer() != null) {
        reporter.error("L8 does not support generating a main dex list");
      }
      if (isShrinking()) {
        if (isGeneratingClassFiles) {
          reporter.error("L8 does not support shrinking when generating class files");
        }
      } else {
        if (proguardMapConsumer != null || partitionMapConsumer != null) {
          reporter.error("L8 does not support defining a map consumer when not shrinking");
        }
        if (!getArtProfilesForRewriting().isEmpty()) {
          if (isGeneratingClassFiles) {
            reporter.error(
                "L8 does not support rewriting of ART profiles when generating class files");
          } else {
            reporter.error("L8 does not impact ART profiles when generating DEX and not shrinking");
          }
        }
      }
      super.validate();
    }

    @Override
    L8Command makeCommand() {
      if (isPrintHelp() || isPrintVersion()) {
        return new L8Command(isPrintHelp(), isPrintVersion());
      }

      if (getMode() == null) {
        setMode(defaultCompilationMode());
      }

      DexItemFactory factory = new DexItemFactory();
      DesugaredLibrarySpecification desugaredLibrarySpecification =
          getDesugaredLibraryConfiguration(factory, true);

      R8Command r8Command = null;
      D8Command d8Command = null;

      AndroidApp inputs = getAppBuilder().build();
      ProgramConsumer l8CfConsumer;
      if (isShrinking()) {
        l8CfConsumer = new InMemoryJarContent();
        R8Command.Builder r8Builder =
            R8Command.builder(getReporter())
                .addProgramResourceProvider((ProgramResourceProvider) l8CfConsumer)
                .setSynthesizedClassesPrefix(
                    desugaredLibrarySpecification.getSynthesizedLibraryClassesPackagePrefix())
                .setMinApiLevel(getMinApiLevel())
                .setMode(getMode())
                .setIncludeClassesChecksum(getIncludeClassesChecksum())
                .setDexClassChecksumFilter(getDexClassChecksumFilter())
                .setProgramConsumer(getProgramConsumer());
        for (ArtProfileForRewriting artProfileForRewriting : getArtProfilesForRewriting()) {
          r8Builder.addArtProfileForRewriting(artProfileForRewriting);
        }
        for (ClassFileResourceProvider libraryResourceProvider :
            inputs.getLibraryResourceProviders()) {
          r8Builder.addLibraryResourceProvider(libraryResourceProvider);
        }
        for (Pair<List<String>, Origin> proguardConfig : proguardConfigStrings) {
          r8Builder.addProguardConfiguration(proguardConfig.getFirst(), proguardConfig.getSecond());
        }
        if (proguardMapConsumer != null) {
          r8Builder.setProguardMapConsumer(proguardMapConsumer);
        }
        if (partitionMapConsumer != null) {
          r8Builder.setPartitionMapConsumer(partitionMapConsumer);
        }
        r8Builder.addProguardConfiguration(
            desugaredLibrarySpecification.getExtraKeepRules(), Origin.unknown());
        // TODO(b/180903899): Remove rule when -dontwarn sun.misc.Unsafe is part of config.
        r8Builder.addProguardConfiguration(
            ImmutableList.of("-dontwarn sun.misc.Unsafe"), Origin.unknown());
        r8Builder.addProguardConfigurationFiles(proguardConfigFiles);
        r8Builder.setDisableDesugaring(true);
        r8Builder.skipDump();
        r8Command = r8Builder.makeCommand();
      } else if (!(getProgramConsumer() instanceof ClassFileConsumer)) {
        l8CfConsumer = new InMemoryJarContent();
        D8Command.Builder d8Builder =
            D8Command.builder(getReporter())
                .addProgramResourceProvider((ProgramResourceProvider) l8CfConsumer)
                .setSynthesizedClassesPrefix(
                    desugaredLibrarySpecification.getSynthesizedLibraryClassesPackagePrefix())
                .setMinApiLevel(getMinApiLevel())
                .setMode(getMode())
                .setIncludeClassesChecksum(getIncludeClassesChecksum())
                .setDexClassChecksumFilter(getDexClassChecksumFilter())
                .setProgramConsumer(getProgramConsumer())
                .setEnableRewritingOfArtProfilesIsNopCheck();
        for (ClassFileResourceProvider libraryResourceProvider :
            inputs.getLibraryResourceProviders()) {
          d8Builder.addLibraryResourceProvider(libraryResourceProvider);
        }
        d8Builder.setDisableDesugaring(true);
        d8Builder.skipDump();
        d8Command = d8Builder.makeCommand();
      } else {
        assert getProgramConsumer() instanceof ClassFileConsumer;
        l8CfConsumer = getProgramConsumer();
        d8Command = null;
      }
      return new L8Command(
          r8Command,
          d8Command,
          inputs,
          getMode(),
          l8CfConsumer,
          getMainDexListConsumer(),
          getMinApiLevel(),
          getReporter(),
          getIncludeClassesChecksum(),
          getDexClassChecksumFilter(),
          desugaredLibrarySpecification,
          getAssertionsConfiguration(),
          getOutputInspections(),
          getThreadCount(),
          getDumpInputFlags(),
          getMapIdProvider(),
          getClassConflictResolver(),
          getCancelCompilationChecker(),
          factory);
    }
  }

  static class InMemoryJarContent implements ClassFileConsumer, ProgramResourceProvider {

    private final List<ProgramResource> resources = new ArrayList<>();

    @Override
    public synchronized void accept(
        ByteDataView data, String descriptor, DiagnosticsHandler handler) {
      // TODO(b/139273544): Map Origin information.
      resources.add(
          ProgramResource.fromBytes(
              Origin.unknown(), Kind.CF, data.copyByteData(), Collections.singleton(descriptor)));
    }

    @Override
    public Collection<ProgramResource> getProgramResources() {
      return resources;
    }

    @Override
    public void finished(DiagnosticsHandler handler) {}
  }

  private DumpOptions dumpOptions() {
    DumpOptions.Builder builder = DumpOptions.builder(Tool.L8).readCurrentSystemProperties();
    dumpBaseCommandOptions(builder);
    if (r8Command != null) {
      builder.setProguardConfiguration(r8Command.getInternalOptions().getProguardConfiguration());
    }
    return builder.setDesugaredLibraryConfiguration(desugaredLibrarySpecification).build();
  }
}
