// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.inspector.Inspector;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileForRewriting;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions.DesugarState;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.PartitionMapZipContainer;
import com.android.tools.r8.utils.ProgramConsumerUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Base class for commands and command builders for compiler applications/tools which besides an
 * Android application (and optional main-dex list) also configure compilation output, compilation
 * mode and min API level.
 *
 * <p>For concrete builders, see for example {@link D8Command.Builder} and {@link
 * R8Command.Builder}.
 */
@Keep
public abstract class BaseCompilerCommand extends BaseCommand {

  private final CompilationMode mode;
  private final ProgramConsumer programConsumer;
  private final StringConsumer mainDexListConsumer;
  private final int minApiLevel;
  private final Reporter reporter;
  private final DesugarState desugarState;
  private final boolean includeClassesChecksum;
  private final boolean optimizeMultidexForLinearAlloc;
  private final BiPredicate<String, Long> dexClassChecksumFilter;
  private final List<AssertionsConfiguration> assertionsConfiguration;
  private final List<Consumer<Inspector>> outputInspections;
  private final int threadCount;
  private final DumpInputFlags dumpInputFlags;
  private final MapIdProvider mapIdProvider;
  private final SourceFileProvider sourceFileProvider;
  private final boolean isAndroidPlatformBuild;
  private final List<ArtProfileForRewriting> artProfilesForRewriting;
  private final List<StartupProfileProvider> startupProfileProviders;
  private final ClassConflictResolver classConflictResolver;
  private final CancelCompilationChecker cancelCompilationChecker;

  BaseCompilerCommand(boolean printHelp, boolean printVersion) {
    super(printHelp, printVersion);
    programConsumer = null;
    mainDexListConsumer = null;
    mode = null;
    minApiLevel = 0;
    reporter = new Reporter();
    desugarState = DesugarState.ON;
    includeClassesChecksum = false;
    optimizeMultidexForLinearAlloc = false;
    dexClassChecksumFilter = (name, checksum) -> true;
    assertionsConfiguration = new ArrayList<>();
    outputInspections = null;
    threadCount = ThreadUtils.NOT_SPECIFIED;
    dumpInputFlags = DumpInputFlags.noDump();
    mapIdProvider = null;
    sourceFileProvider = null;
    isAndroidPlatformBuild = false;
    artProfilesForRewriting = null;
    startupProfileProviders = null;
    classConflictResolver = null;
    cancelCompilationChecker = null;
  }

  BaseCompilerCommand(
      AndroidApp app,
      CompilationMode mode,
      ProgramConsumer programConsumer,
      StringConsumer mainDexListConsumer,
      int minApiLevel,
      Reporter reporter,
      DesugarState desugarState,
      boolean optimizeMultidexForLinearAlloc,
      boolean includeClassesChecksum,
      BiPredicate<String, Long> dexClassChecksumFilter,
      List<AssertionsConfiguration> assertionsConfiguration,
      List<Consumer<Inspector>> outputInspections,
      int threadCount,
      DumpInputFlags dumpInputFlags,
      MapIdProvider mapIdProvider,
      SourceFileProvider sourceFileProvider,
      boolean isAndroidPlatformBuild,
      List<ArtProfileForRewriting> artProfilesForRewriting,
      List<StartupProfileProvider> startupProfileProviders,
      ClassConflictResolver classConflictResolver,
      CancelCompilationChecker cancelCompilationChecker) {
    super(app);
    assert minApiLevel > 0;
    assert mode != null;
    this.mode = mode;
    this.programConsumer = programConsumer;
    this.mainDexListConsumer = mainDexListConsumer;
    this.minApiLevel = minApiLevel;
    this.reporter = reporter;
    this.desugarState = desugarState;
    this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
    this.includeClassesChecksum = includeClassesChecksum;
    this.dexClassChecksumFilter = dexClassChecksumFilter;
    this.assertionsConfiguration = assertionsConfiguration;
    this.outputInspections = outputInspections;
    this.threadCount = threadCount;
    this.dumpInputFlags = dumpInputFlags;
    this.mapIdProvider = mapIdProvider;
    this.sourceFileProvider = sourceFileProvider;
    this.isAndroidPlatformBuild = isAndroidPlatformBuild;
    this.artProfilesForRewriting = artProfilesForRewriting;
    this.startupProfileProviders = startupProfileProviders;
    this.classConflictResolver = classConflictResolver;
    this.cancelCompilationChecker = cancelCompilationChecker;
  }

  /**
   * Get the compilation mode, e.g., {@link CompilationMode#DEBUG} or {@link
   * CompilationMode#RELEASE}.
   */
  public CompilationMode getMode() {
    return mode;
  }

  /** Get the minimum API level to compile against. */
  public int getMinApiLevel() {
    return minApiLevel;
  }

  void dumpBaseCommandOptions(DumpOptions.Builder builder) {
    builder
        .setBackend(ProgramConsumerUtils.getBackend(programConsumer))
        .setCompilationMode(getMode())
        .setMinApi(getMinApiLevel())
        .setOptimizeMultidexForLinearAlloc(isOptimizeMultidexForLinearAlloc())
        .setThreadCount(getThreadCount())
        .setDesugarState(getDesugarState())
        .setArtProfileProviders(
            ListUtils.map(
                getArtProfilesForRewriting(), ArtProfileForRewriting::getArtProfileProvider))
        .setStartupProfileProviders(getStartupProfileProviders());
    if (getAndroidPlatformBuild()) {
      builder.setAndroidPlatformBuild(true);
    }
  }

  /**
   * Get the program consumer that will receive the compilation output.
   *
   * <p>Note that the concrete consumer reference is final, the consumer itself is likely stateful.
   */
  public ProgramConsumer getProgramConsumer() {
    return programConsumer;
  }

  /**
   * Get the main dex list consumer that will receive the final complete main dex list.
   */
  public StringConsumer getMainDexListConsumer() {
    return mainDexListConsumer;
  }

  /** Get the use-desugaring state. True if enabled, false otherwise. */
  public boolean getEnableDesugaring() {
    return desugarState == DesugarState.ON;
  }

  DesugarState getDesugarState() {
    return desugarState;
  }

  public MapIdProvider getMapIdProvider() {
    return mapIdProvider;
  }

  public SourceFileProvider getSourceFileProvider() {
    return sourceFileProvider;
  }

  /** True if the output dex files has checksum information encoded in it. False otherwise. */
  public boolean getIncludeClassesChecksum() {
    return includeClassesChecksum;
  }

  /** Filter used to skip parsing of certain class in a dex file. */
  public BiPredicate<String, Long> getDexClassChecksumFilter() {
    return dexClassChecksumFilter;
  }

  /**
   * If true, legacy multidex partitioning will be optimized to reduce LinearAlloc usage during
   * Dalvik DexOpt.
   */
  public boolean isOptimizeMultidexForLinearAlloc() {
    return optimizeMultidexForLinearAlloc;
  }

  public List<AssertionsConfiguration> getAssertionsConfiguration() {
    return Collections.unmodifiableList(assertionsConfiguration);
  }

  public Collection<Consumer<Inspector>> getOutputInspections() {
    return Collections.unmodifiableList(outputInspections);
  }

  /** Get the number of threads to use for the compilation. */
  public int getThreadCount() {
    return threadCount;
  }

  public boolean getAndroidPlatformBuild() {
    return isAndroidPlatformBuild;
  }

  List<ArtProfileForRewriting> getArtProfilesForRewriting() {
    return artProfilesForRewriting;
  }

  List<StartupProfileProvider> getStartupProfileProviders() {
    return startupProfileProviders;
  }

  ClassConflictResolver getClassConflictResolver() {
    return classConflictResolver;
  }

  public CancelCompilationChecker getCancelCompilationChecker() {
    return cancelCompilationChecker;
  }

  DumpInputFlags getDumpInputFlags() {
    return dumpInputFlags;
  }

  Reporter getReporter() {
    return reporter;
  }

  /**
   * Base builder for compilation commands.
   *
   * @param <C> Command the builder is building, e.g., {@link R8Command} or {@link D8Command}.
   * @param <B> Concrete builder extending this base, e.g., {@link R8Command.Builder} or {@link
   *     D8Command.Builder}.
   */
  @Keep
  public abstract static class Builder<C extends BaseCompilerCommand, B extends Builder<C, B>>
      extends BaseCommand.Builder<C, B> {

    private ProgramConsumer programConsumer = null;
    private StringConsumer mainDexListConsumer = null;
    private Path outputPath = null;
    // TODO(b/70656566): Remove default output mode when deprecated API is removed.
    private OutputMode outputMode = OutputMode.DexIndexed;

    private CompilationMode mode;
    private int minApiLevel = 0;
    private int threadCount = ThreadUtils.NOT_SPECIFIED;
    protected DesugarState desugarState = DesugarState.ON;
    private final List<StringResource> desugaredLibrarySpecificationResources = new ArrayList<>();
    private boolean includeClassesChecksum = false;
    private boolean optimizeMultidexForLinearAlloc = false;
    private BiPredicate<String, Long> dexClassChecksumFilter = (name, checksum) -> true;
    private final List<AssertionsConfiguration> assertionsConfiguration = new ArrayList<>();
    private final List<Consumer<Inspector>> outputInspections = new ArrayList<>();
    protected StringConsumer proguardMapConsumer = null;
    protected PartitionMapConsumer partitionMapConsumer = null;
    private DumpInputFlags dumpInputFlags = DumpInputFlags.getDefault();
    private MapIdProvider mapIdProvider = null;
    private SourceFileProvider sourceFileProvider = null;
    private boolean isAndroidPlatformBuild = false;
    private final List<ArtProfileForRewriting> artProfilesForRewriting = new ArrayList<>();
    private final List<StartupProfileProvider> startupProfileProviders = new ArrayList<>();
    private ClassConflictResolver classConflictResolver = null;
    private CancelCompilationChecker cancelCompilationChecker = null;

    abstract CompilationMode defaultCompilationMode();

    Builder(DiagnosticsHandler diagnosticsHandler) {
      super(diagnosticsHandler);
      mode = defaultCompilationMode();
    }

    // Internal constructor for testing.
    Builder(AndroidApp app) {
      super(AndroidApp.builder(app));
      mode = defaultCompilationMode();
    }

    // Internal constructor for testing.
    Builder(AndroidApp app, DiagnosticsHandler diagnosticsHandler) {
      super(AndroidApp.builder(app, new Reporter(diagnosticsHandler)));
      mode = defaultCompilationMode();
    }

    /**
     * Get current compilation mode.
     */
    public CompilationMode getMode() {
      return mode;
    }

    /**
     * Set compilation mode.
     */
    public B setMode(CompilationMode mode) {
      assert mode != null;
      this.mode = mode;
      return self();
    }

    /**
     * Get the output path.
     *
     * @return Current output path, null if no output path-and-mode have been set.
     * @see #setOutput(Path, OutputMode)
     */
    public Path getOutputPath() {
      return outputPath;
    }

    /**
     * Get the output mode.
     *
     * @return Currently set output mode, null if no output path-and-mode have been set.
     * @see #setOutput(Path, OutputMode)
     */
    public OutputMode getOutputMode() {
      return outputMode;
    }

    /**
     * Get the program consumer.
     *
     * @return The currently set program consumer, null if no program consumer or output
     *     path-and-mode is set, e.g., neither {@link #setProgramConsumer} nor {@link #setOutput}
     *     have been called.
     */
    public ProgramConsumer getProgramConsumer() {
      return programConsumer;
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
    B setProguardMapOutputPath(Path proguardMapOutput) {
      assert proguardMapOutput != null;
      return setProguardMapConsumer(new StringConsumer.FileConsumer(proguardMapOutput));
    }

    /**
     * Set a consumer for receiving the proguard-map content.
     *
     * <p>Note that any subsequent call to this method or {@link #setProguardMapOutputPath} will
     * override the previous setting.
     *
     * @param proguardMapConsumer Consumer to receive the content once produced.
     */
    B setProguardMapConsumer(StringConsumer proguardMapConsumer) {
      this.proguardMapConsumer = proguardMapConsumer;
      return self();
    }

    /**
     * Set an output destination to which partition-map content should be written.
     *
     * <p>This is a short-hand for setting a {@link PartitionMapConsumer} using {@link
     * #setPartitionMapConsumer}. Note that any subsequent call to this method or {@link
     * #setPartitionMapConsumer} will override the previous setting.
     *
     * @param partitionMapOutput File-system path to write output at.
     */
    public B setPartitionMapOutputPath(Path partitionMapOutput) {
      assert partitionMapOutput != null;
      return setPartitionMapConsumer(
          PartitionMapZipContainer.createPartitionMapZipContainerConsumer(partitionMapOutput));
    }

    /**
     * Set a consumer for receiving the partition map content.
     *
     * <p>Note that any subsequent call to this method or {@link #setPartitionMapOutputPath} will
     * override the previous setting.
     *
     * @param partitionMapConsumer Consumer to receive the content once produced.
     */
    public B setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
      this.partitionMapConsumer = partitionMapConsumer;
      return self();
    }

    /**
     * Get the main dex list consumer that will receive the final complete main dex list.
     */
    public StringConsumer getMainDexListConsumer() {
      return mainDexListConsumer;
    }

    /**
     * Filter used to skip parsing of certain class in a dex file.
     */
    public BiPredicate<String, Long> getDexClassChecksumFilter() {
      return dexClassChecksumFilter;
    }

    /**
     * If set to true, legacy multidex partitioning will be optimized to reduce LinearAlloc usage
     * during Dalvik DexOpt. Has no effect when compiling for a target with native multidex support
     * or without main dex list specification.
     */
    public B setOptimizeMultidexForLinearAlloc(boolean optimizeMultidexForLinearAlloc) {
      this.optimizeMultidexForLinearAlloc = optimizeMultidexForLinearAlloc;
      return self();
    }

    /**
     * If true, legacy multidex partitioning will be optimized to reduce LinearAlloc usage during
     * Dalvik DexOpt.
     */
    protected boolean isOptimizeMultidexForLinearAlloc() {
      return optimizeMultidexForLinearAlloc;
    }

    /**
     * Set the program consumer.
     *
     * <p>Setting the program consumer will override any previous set consumer or any previous set
     * output path & mode.
     *
     * @param programConsumer Program consumer to set as current. A null argument will clear the
     *     program consumer / output.
     */
    public B setProgramConsumer(ProgramConsumer programConsumer) {
      // Setting an explicit program consumer resets any output-path/mode setup.
      outputPath = null;
      outputMode = null;
      this.programConsumer = programConsumer;
      return self();
    }

    /**
     * Set an output destination to which main-dex-list content should be written.
     *
     * <p>This is a short-hand for setting a {@link StringConsumer.FileConsumer} using {@link
     * #setMainDexListConsumer}. Note that any subsequent call to this method or {@link
     * #setMainDexListConsumer} will override the previous setting.
     *
     * @param mainDexListOutputPath File-system path to write output at.
     */
    public B setMainDexListOutputPath(Path mainDexListOutputPath) {
      mainDexListConsumer = new StringConsumer.FileConsumer(mainDexListOutputPath);
      return self();
    }

    /**
     * Set a consumer for receiving the main-dex-list content.
     *
     * <p>Note that any subsequent call to this method or {@link #setMainDexListOutputPath} will
     * override the previous setting.
     *
     * @param mainDexListConsumer Consumer to receive the content once produced.
     */
    public B setMainDexListConsumer(StringConsumer mainDexListConsumer) {
      this.mainDexListConsumer = mainDexListConsumer;
      return self();
    }

    /**
     * Set the output path-and-mode.
     *
     * <p>Setting the output path-and-mode will override any previous set consumer or any previous
     * output path-and-mode, and implicitly sets the appropriate program consumer to write the
     * output.
     *
     * @param outputPath Path to write the output to. Must be an archive or and existing directory.
     * @param outputMode Mode in which to write the output.
     */
    public B setOutput(Path outputPath, OutputMode outputMode) {
      return setOutput(outputPath, outputMode, false);
    }

    // This is only public in R8Command.
    protected B setOutput(Path outputPath, OutputMode outputMode, boolean includeDataResources) {
      assert outputPath != null;
      assert outputMode != null;
      this.outputPath = outputPath;
      this.outputMode = outputMode;
      programConsumer = createProgramOutputConsumer(outputPath, outputMode, includeDataResources);
      return self();
    }

    /**
     * Setting a dex class filter.
     *
     * A filter is a function that given a name of a class and a checksum can return false the user
     * decides to skip parsing and ignore that class in the dex file.
     */
    public B setDexClassChecksumFilter(BiPredicate<String, Long> filter) {
      assert filter != null;
      this.dexClassChecksumFilter = filter;
      return self();
    }

    protected InternalProgramOutputPathConsumer createProgramOutputConsumer(
        Path path,
        OutputMode mode,
        boolean consumeDataResources) {
      if (mode == OutputMode.DexIndexed) {
        return FileUtils.isArchive(path)
            ? new DexIndexedConsumer.ArchiveConsumer(path, consumeDataResources)
            : new DexIndexedConsumer.DirectoryConsumer(path, consumeDataResources);
      }
      if (mode == OutputMode.DexFilePerClass) {
        if (FileUtils.isArchive(path)) {
          return new DexFilePerClassFileConsumer.ArchiveConsumer(path, consumeDataResources) {
            @Override
            public boolean combineSyntheticClassesWithPrimaryClass() {
              return false;
            }
          };
        } else {
          return new DexFilePerClassFileConsumer.DirectoryConsumer(path, consumeDataResources) {
            @Override
            public boolean combineSyntheticClassesWithPrimaryClass() {
              return false;
            }
          };
        }
      }
      if (mode == OutputMode.DexFilePerClassFile) {
        return FileUtils.isArchive(path)
            ? new DexFilePerClassFileConsumer.ArchiveConsumer(path, consumeDataResources)
            : new DexFilePerClassFileConsumer.DirectoryConsumer(path, consumeDataResources);
      }
      if (mode == OutputMode.ClassFile) {
        return FileUtils.isArchive(path)
            ? new ClassFileConsumer.ArchiveConsumer(path, consumeDataResources)
            : new ClassFileConsumer.DirectoryConsumer(path, consumeDataResources);
      }
      throw new Unreachable("Unexpected output mode: " + mode);
    }

    /** Get the minimum API level (aka SDK version). */
    public int getMinApiLevel() {
      return isMinApiLevelSet() ? minApiLevel : AndroidApiLevel.getDefault().getLevel();
    }

    boolean isMinApiLevelSet() {
      return minApiLevel != 0;
    }

    /** Set the minimum required API level (aka SDK version). */
    public B setMinApiLevel(int minApiLevel) {
      if (minApiLevel <= 0) {
        getReporter().error("Invalid minApiLevel: " + minApiLevel);
      } else {
        this.minApiLevel = minApiLevel;
      }
      return self();
    }

    @Deprecated
    public B setEnableDesugaring(boolean enableDesugaring) {
      this.desugarState = enableDesugaring ? DesugarState.ON : DesugarState.OFF;
      return self();
    }

    /**
     * Force disable desugaring.
     *
     * <p>There are a few use cases where it makes sense to force disable desugaring, such as:
     * <ul>
     * <li>if all inputs are known to be at most Java 7; or
     * <li>if a separate desugar tool has been used prior to compiling with D8.
     * </ul>
     *
     * <p>Note that even for API 27, desugaring is still required for closures support on ART.
     */
    public B setDisableDesugaring(boolean disableDesugaring) {
      this.desugarState = disableDesugaring ? DesugarState.OFF : DesugarState.ON;
      return self();
    }

    /** Is desugaring forcefully disabled. */
    public boolean getDisableDesugaring() {
      return desugarState == DesugarState.OFF;
    }

    DesugarState getDesugaringState() {
      return desugarState;
    }

    /** Set a custom provider for defining the map id for the build. */
    public B setMapIdProvider(MapIdProvider mapIdProvider) {
      this.mapIdProvider = mapIdProvider;
      return self();
    }

    public MapIdProvider getMapIdProvider() {
      return mapIdProvider;
    }

    /** Set a custom provider for defining source-file attributes for classes. */
    public B setSourceFileProvider(SourceFileProvider sourceFileProvider) {
      this.sourceFileProvider = sourceFileProvider;
      return self();
    }

    public SourceFileProvider getSourceFileProvider() {
      return sourceFileProvider;
    }

    @Deprecated
    @SuppressWarnings("InlineMeSuggester")
    public B addSpecialLibraryConfiguration(String configuration) {
      return addDesugaredLibraryConfiguration(configuration);
    }

    /** Desugared library configuration */
    // Configuration "default" is for testing only and support will be dropped.
    public B addDesugaredLibraryConfiguration(String configuration) {
      this.desugaredLibrarySpecificationResources.add(
          StringResource.fromString(configuration, Origin.unknown()));
      return self();
    }

    /** Desugared library configuration */
    public B addDesugaredLibraryConfiguration(StringResource configuration) {
      this.desugaredLibrarySpecificationResources.add(configuration);
      return self();
    }

    DesugaredLibrarySpecification getDesugaredLibraryConfiguration(
        DexItemFactory factory, boolean libraryCompilation) {
      if (desugaredLibrarySpecificationResources.isEmpty()) {
        return HumanDesugaredLibrarySpecification.empty();
      }
      if (desugaredLibrarySpecificationResources.size() > 1) {
        throw new CompilationError("Only one desugared library configuration is supported.");
      }
      StringResource desugaredLibrarySpecificationResource =
          desugaredLibrarySpecificationResources.get(0);
      return DesugaredLibrarySpecificationParser.parseDesugaredLibrarySpecification(
          desugaredLibrarySpecificationResource,
          factory,
          getReporter(),
          libraryCompilation,
          getMinApiLevel());
    }

    boolean hasDesugaredLibraryConfiguration() {
      return !desugaredLibrarySpecificationResources.isEmpty();
    }

    /** Encodes checksum for each class when generating dex files. */
    public B setIncludeClassesChecksum(boolean enabled) {
      this.includeClassesChecksum = enabled;
      return self();
    }

    /** Set the number of threads to use for the compilation */
    B setThreadCount(int threadCount) {
      if (threadCount <= 0) {
        getReporter().error("Invalid threadCount: " + threadCount);
      } else {
        this.threadCount = threadCount;
      }
      return self();
    }

    int getThreadCount() {
      return threadCount;
    }

    /** Encodes the checksums into the dex output. */
    public boolean getIncludeClassesChecksum() {
      return includeClassesChecksum;
    }

    List<AssertionsConfiguration> getAssertionsConfiguration() {
      return assertionsConfiguration;
    }

    /** Configure compile time assertion enabling through a {@link AssertionsConfiguration}. */
    public B addAssertionsConfiguration(
        Function<AssertionsConfiguration.Builder, AssertionsConfiguration>
            assertionsConfigurationGenerator) {
      assertionsConfiguration.add(
          assertionsConfigurationGenerator.apply(AssertionsConfiguration.builder(getReporter())));
      return self();
    }

    /**
     * Configure the present build as a "Android platform build".
     *
     * <p>A platform build, is a build where the runtime "bootclasspath" is known at compile time.
     * In other words, the specified <i>min-api</i> is also known to be the <i>max-api</i>. In this
     * mode the compiler will disable various features that provide support for newer runtimes as
     * well as disable workarounds for older runtimes.
     */
    public B setAndroidPlatformBuild(boolean isAndroidPlatformBuild) {
      this.isAndroidPlatformBuild = isAndroidPlatformBuild;
      return self();
    }

    public boolean getAndroidPlatformBuild() {
      return isAndroidPlatformBuild;
    }

    /**
     * Add an ART profiles that should be rewritten to match the residual application. The ART
     * profile is given to the compiler by the {@link ArtProfileProvider} and passed back to the
     * {@link ArtProfileConsumer} at the end of compilation when the ART profile has been fully
     * rewritten to match the residual application.
     */
    public B addArtProfileForRewriting(
        ArtProfileProvider artProfileProvider, ArtProfileConsumer residualArtProfileProvider) {
      return addArtProfileForRewriting(
          new ArtProfileForRewriting(artProfileProvider, residualArtProfileProvider));
    }

    B addArtProfileForRewriting(ArtProfileForRewriting artProfileForRewriting) {
      artProfilesForRewriting.add(artProfileForRewriting);
      return self();
    }

    List<ArtProfileForRewriting> getArtProfilesForRewriting() {
      return artProfilesForRewriting;
    }

    B addStartupProfileProviders(StartupProfileProvider... startupProfileProviders) {
      return addStartupProfileProviders(Arrays.asList(startupProfileProviders));
    }

    B addStartupProfileProviders(Collection<StartupProfileProvider> startupProfileProviders) {
      this.startupProfileProviders.addAll(startupProfileProviders);
      return self();
    }

    List<StartupProfileProvider> getStartupProfileProviders() {
      return startupProfileProviders;
    }

    /**
     * Set a cancellation checker.
     *
     * <p>The cancellation checker will be periodically called to check if the compilation should be
     * cancelled before completion.
     */
    public B setCancelCompilationChecker(CancelCompilationChecker checker) {
      this.cancelCompilationChecker = checker;
      return self();
    }

    public CancelCompilationChecker getCancelCompilationChecker() {
      return cancelCompilationChecker;
    }

    /**
     * Allow to skip to dump into file and dump into directory instruction, this is primarily used
     * for chained compilation in L8 so there are no duplicated dumps.
     */
    B skipDump() {
      dumpInputFlags = DumpInputFlags.noDump();
      return self();
    }

    B dumpInputToFile(Path file) {
      dumpInputFlags = DumpInputFlags.dumpToFile(file);
      return self();
    }

    B dumpInputToDirectory(Path directory) {
      dumpInputFlags = DumpInputFlags.dumpToDirectory(directory);
      return self();
    }

    DumpInputFlags getDumpInputFlags() {
      return dumpInputFlags;
    }

    @Override
    void validate() {
      Reporter reporter = getReporter();
      if (mode == null) {
        reporter.error("Expected valid compilation mode, was null");
      }
      FileUtils.validateOutputFile(outputPath, reporter);
      if (getProgramConsumer() == null) {
        // This is never the case for a command-line parse, so we report using API references.
        reporter.error("A ProgramConsumer or Output is required for compilation");
      }
      List<Class> programConsumerClasses = new ArrayList<>(3);
      if (programConsumer instanceof DexIndexedConsumer) {
        programConsumerClasses.add(DexIndexedConsumer.class);
      }
      if (programConsumer instanceof DexFilePerClassFileConsumer) {
        programConsumerClasses.add(DexFilePerClassFileConsumer.class);
      }
      if (programConsumer instanceof ClassFileConsumer) {
        programConsumerClasses.add(ClassFileConsumer.class);
      }
      if (programConsumerClasses.size() > 1) {
        StringBuilder builder = new StringBuilder()
            .append("Invalid program consumer.")
            .append(" A program consumer can implement at most one consumer type but ")
            .append(programConsumer.getClass().getName())
            .append(" implements types:");
        for (Class clazz : programConsumerClasses) {
          builder.append(" ").append(clazz.getName());
        }
        reporter.error(builder.toString());
      }
      if (getMinApiLevel() > AndroidApiLevel.LATEST.getLevel()) {
        if (getMinApiLevel() != AndroidApiLevel.ANDROID_PLATFORM_CONSTANT) {
          reporter.warning(
              "An API level of "
                  + getMinApiLevel()
                  + " is not supported by this compiler. Please use an API level of "
                  + AndroidApiLevel.LATEST.getLevel()
                  + " or earlier");
        }
      }
      if (hasDesugaredLibraryConfiguration() && getAndroidPlatformBuild()) {
        reporter.error("Android platform builds cannot use desugared library");
      }
      super.validate();
    }

    /**
     * Add an inspection of the output program.
     *
     * <p>On a successful compilation the inspection is guaranteed to be called with inspectors that
     * combined cover all of the output program. The inspections may be called multiple times with
     * inspectors that have overlapping content (eg, classes synthesized based on multiple inputs
     * can lead to this). Any overlapping content will be consistent, e.g., the inspection of type T
     * will be the same (equality, not identify) as any other inspection of type T.
     *
     * <p>There is no guarantee of the order inspections are called or on which thread they are
     * called.
     *
     * <p>The validity of an {@code Inspector} and all of its sub-inspectors, eg,
     * {@MethodInspector}, is that of the callback. If any inspector object escapes the scope of the
     * callback, the behavior of that inspector is undefined.
     *
     * @param inspection Inspection callback receiving inspectors denoting parts of the output.
     */
    public B addOutputInspection(Consumer<Inspector> inspection) {
      outputInspections.add(inspection);
      return self();
    }

    List<Consumer<Inspector>> getOutputInspections() {
      return outputInspections;
    }

    /**
     * Set a conflict resolver to determine which class definition to use in case of duplicates.
     *
     * <p>If no resolver is set, the compiler will fail compilation in case of duplicates.
     *
     * @param resolver Resolver for choosing between duplicate classes.
     */
    public B setClassConflictResolver(ClassConflictResolver resolver) {
      this.classConflictResolver = resolver;
      return self();
    }

    ClassConflictResolver getClassConflictResolver() {
      return classConflictResolver;
    }
  }
}
