// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.graph.ClassKind.CLASSPATH;
import static com.android.tools.r8.graph.ClassKind.LIBRARY;
import static com.android.tools.r8.graph.ClassKind.PROGRAM;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.dump.DumpOptions;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.UnsupportedMainDexListUsageDiagnostic;
import com.android.tools.r8.graph.ApplicationReaderMap;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplicationReadFlags;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.graph.LazyLoadedDexApplication;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ClassProvider;
import com.android.tools.r8.utils.ClasspathClassCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.DumpInputFlags;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LibraryClassCollection;
import com.android.tools.r8.utils.MainDexListParser;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class ApplicationReader {

  private final InternalOptions options;
  private final DexItemFactory itemFactory;
  private final Timing timing;
  private final AndroidApp inputApp;

  private DexApplicationReadFlags flags;

  public interface ProgramClassConflictResolver {
    DexProgramClass resolveClassConflict(DexProgramClass a, DexProgramClass b);
  }

  public ApplicationReader(AndroidApp inputApp, InternalOptions options, Timing timing) {
    this.options = options;
    itemFactory = options.itemFactory;
    this.timing = timing;
    this.inputApp = inputApp;
  }

  public LazyLoadedDexApplication read() throws IOException {
    return read((StringResource) null);
  }

  public LazyLoadedDexApplication read(
      StringResource proguardMap)
      throws IOException {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      return read(proguardMap, executor);
    } finally {
      executor.shutdown();
    }
  }

  public final LazyLoadedDexApplication read(ExecutorService executorService) throws IOException {
    return read(inputApp.getProguardMapInputData(), executorService);
  }

  public final LazyLoadedDexApplication readWithoutDumping(ExecutorService executorService)
      throws IOException {
    return read(inputApp.getProguardMapInputData(), executorService, DumpInputFlags.noDump());
  }

  public final LazyLoadedDexApplication read(
      StringResource proguardMap,
      ExecutorService executorService)
      throws IOException {
    return read(proguardMap, executorService, options.getDumpInputFlags());
  }

  public final LazyLoadedDexApplication read(
      StringResource proguardMap,
      ExecutorService executorService,
      DumpInputFlags dumpInputFlags)
      throws IOException {
    assert verifyMainDexOptionsCompatible(inputApp, options);
    dumpApplication(dumpInputFlags);

    if (options.testing.verifyInputs) {
      inputApp.validateInputs();
    }

    timing.begin("DexApplication.read");
    final LazyLoadedDexApplication.Builder builder = DexApplication.builder(options, timing);
    TaskCollection<?> tasks = new TaskCollection<>(options, executorService);
    try {
      // Still preload some of the classes, primarily for two reasons:
      // (a) class lazy loading is not supported for DEX files
      //     now and current implementation of parallel DEX file
      //     loading will be lost with on-demand class loading.
      // (b) some of the class file resources don't provide information
      //     about class descriptor.
      // TODO: try and preload less classes.
      readProguardMap(proguardMap, builder, tasks);
      ClassReader classReader = new ClassReader(tasks);
      classReader.readSources();
      tasks.await();
      flags = classReader.getDexApplicationReadFlags();
      builder.setFlags(flags);
      classReader.initializeLazyClassCollection(builder);
      for (ProgramResourceProvider provider : inputApp.getProgramResourceProviders()) {
        DataResourceProvider dataResourceProvider = provider.getDataResourceProvider();
        if (dataResourceProvider != null) {
          builder.addDataResourceProvider(dataResourceProvider);
        }
      }
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } catch (ResourceException e) {
      throw options.reporter.fatalError(new StringDiagnostic(e.getMessage(), e.getOrigin()));
    } finally {
      timing.end();
    }
    return builder.build();
  }

  private void dumpApplication(DumpInputFlags dumpInputFlags) {
    DumpOptions dumpOptions = options.dumpOptions;
    if (dumpOptions == null || !dumpInputFlags.shouldDump(dumpOptions)) {
      return;
    }
    Path dumpOutput = dumpInputFlags.getDumpPath();
    timing.begin("ApplicationReader.dump");
    inputApp.dump(dumpOutput, dumpOptions, options);
    timing.end();
    Diagnostic message = new StringDiagnostic("Dumped compilation inputs to: " + dumpOutput);
    if (dumpInputFlags.shouldFailCompilation()) {
      throw options.reporter.fatalError(message);
    } else {
      options.reporter.info(message);
    }
  }

  public MainDexInfo readMainDexClasses(DexApplication app) {
    return readMainDexClasses(app, flags.hasReadProgramClassFromCf());
  }

  public MainDexInfo readMainDexClassesForR8(DexApplication app) {
    // Officially R8 only support reading CF program inputs, thus we always generate a deprecated
    // diagnostic if main-dex list is used.
    return readMainDexClasses(app, true);
  }

  private MainDexInfo readMainDexClasses(DexApplication app, boolean emitDeprecatedDiagnostics) {
    MainDexInfo.Builder builder = MainDexInfo.none().builder();
    if (inputApp.hasMainDexList()) {
      for (StringResource resource : inputApp.getMainDexListResources()) {
        if (emitDeprecatedDiagnostics) {
          options.reporter.warning(new UnsupportedMainDexListUsageDiagnostic(resource.getOrigin()));
        }
        addToMainDexClasses(app, builder, MainDexListParser.parseList(resource, itemFactory));
      }
      if (!inputApp.getMainDexClasses().isEmpty()) {
        if (emitDeprecatedDiagnostics) {
          options.reporter.warning(new UnsupportedMainDexListUsageDiagnostic(Origin.unknown()));
        }
        addToMainDexClasses(
            app,
            builder,
            inputApp.getMainDexClasses().stream()
                .map(clazz -> itemFactory.createType(DescriptorUtils.javaTypeToDescriptor(clazz)))
                .collect(Collectors.toList()));
      }
    }
    return builder.buildList();
  }

  private void addToMainDexClasses(
      DexApplication app, MainDexInfo.Builder builder, Iterable<DexType> types) {
    for (DexType type : types) {
      DexProgramClass clazz = app.programDefinitionFor(type);
      if (clazz != null) {
        builder.addList(clazz);
      } else if (!options.ignoreMainDexMissingClasses) {
        options.reporter.warning(
            new StringDiagnostic(
                "Application does not contain `"
                    + type.toSourceString()
                    + "` as referenced in main-dex-list."));
      }
    }
  }

  private static boolean verifyMainDexOptionsCompatible(
      AndroidApp inputApp, InternalOptions options) {
    if (!options.isGeneratingDex()) {
      return true;
    }
    AndroidApiLevel nativeMultiDex = AndroidApiLevel.L;
    if (options.getMinApiLevel().isLessThan(nativeMultiDex)) {
      return true;
    }
    assert options.mainDexKeepRules.isEmpty();
    assert options.mainDexListConsumer == null;
    assert !inputApp.hasMainDexList();
    return true;
  }

  private AndroidApiLevel validateOrComputeMinApiLevel(
      AndroidApiLevel computedMinApiLevel, DexReader dexReader) {
    DexVersion version = dexReader.getDexVersion();
    if (!options.testing.dexContainerExperiment
        && version.getIntValue() == InternalOptions.EXPERIMENTAL_DEX_VERSION) {
      throwIncompatibleDexVersionAndMinApi(version);
    }
    if (options.getMinApiLevel() == AndroidApiLevel.getDefault()) {
      computedMinApiLevel = computedMinApiLevel.max(AndroidApiLevel.getMinAndroidApiLevel(version));
    } else if (!version.matchesApiLevel(options.getMinApiLevel())) {
      throwIncompatibleDexVersionAndMinApi(version);
    }
    return computedMinApiLevel;
  }

  private void throwIncompatibleDexVersionAndMinApi(DexVersion version) {
    throw new CompilationError(
        "Dex file with version '"
            + version.getIntValue()
            + "' cannot be used with min sdk level '"
            + options.getMinApiLevel()
            + "'.");
  }

  private void readProguardMap(
      StringResource map, DexApplication.Builder<?> builder, TaskCollection<?> tasks)
      throws ExecutionException {
    // Read the Proguard mapping file in parallel with DexCode and DexProgramClass items.
    if (map == null) {
      return;
    }
    tasks.submit(
        () -> {
          try {
            builder.setProguardMap(
                ClassNameMapper.mapperFromString(
                    map.getString(),
                    options.reporter,
                    options.mappingComposeOptions().allowEmptyMappedRanges,
                    options.testing.enableExperimentalMapFileVersion,
                    true));
          } catch (IOException | ResourceException e) {
            throw new CompilationError("Failure to read proguard map file", e, map.getOrigin());
          }
        });
  }

  private final class ClassReader {
    private final TaskCollection<?> tasks;

    // We use concurrent queues to collect classes
    // since the classes can be collected concurrently.
    private final Queue<DexProgramClass> programClasses = new ConcurrentLinkedQueue<>();
    private final Queue<DexClasspathClass> classpathClasses = new ConcurrentLinkedQueue<>();
    private final Queue<DexLibraryClass> libraryClasses = new ConcurrentLinkedQueue<>();
    // Jar application reader to share across all class readers.
    private final DexApplicationReadFlags.Builder readFlagsBuilder =
        DexApplicationReadFlags.builder();
    private final JarApplicationReader application =
        new JarApplicationReader(options, readFlagsBuilder);

    // Flag of which input resource types have flown into the program classes.
    // Note that this is just at the level of the resources having been given.
    // It is possible to have, e.g., an empty dex file, so no classes, but this will still be true
    // as there was a dex resource.
    private boolean hasReadProgramResourceFromCf = false;
    private boolean hasReadProgramResourceFromDex = false;

    ClassReader(TaskCollection<?> tasks) {
      this.tasks = tasks;
    }

    public DexApplicationReadFlags getDexApplicationReadFlags() {
      return readFlagsBuilder
          .setHasReadProgramClassFromDex(hasReadProgramResourceFromDex)
          .setHasReadProgramClassFromCf(hasReadProgramResourceFromCf)
          .build();
    }

    private void readDexSources(List<ProgramResource> dexSources, Queue<DexProgramClass> classes)
        throws IOException, ResourceException, ExecutionException {
      if (dexSources.isEmpty()) {
        return;
      }
      hasReadProgramResourceFromDex = true;
      List<DexParser<DexProgramClass>> dexParsers = new ArrayList<>(dexSources.size());
      AndroidApiLevel computedMinApiLevel = options.getMinApiLevel();
      for (ProgramResource input : dexSources) {
        DexReader dexReader = new DexReader(input);
        if (options.passthroughDexCode) {
          if (!options.testing.dexContainerExperiment) {
            computedMinApiLevel = validateOrComputeMinApiLevel(computedMinApiLevel, dexReader);
          } else {
            assert dexReader.getDexVersion() == DexVersion.V41;
          }
        }
        if (!options.testing.dexContainerExperiment) {
          if (dexReader.getDexVersion().isContainerDex()) {
            throw new ResourceException(
                input.getOrigin(),
                "Experimental container DEX version "
                    + dexReader.getDexVersion()
                    + " is not supported");
          }
          dexParsers.add(new DexParser<>(dexReader, PROGRAM, options));
        } else {
          addDexParsersForContainer(dexParsers, dexReader);
        }
      }

      options.setMinApiLevel(computedMinApiLevel);
      for (DexParser<DexProgramClass> dexParser : dexParsers) {
        dexParser.populateIndexTables();
      }
      // Read the DexCode items and DexProgramClass items in parallel.
      if (!options.skipReadingDexCode) {
        ApplicationReaderMap applicationReaderMap = ApplicationReaderMap.getInstance(options);
        if (!options.testing.dexContainerExperiment) {
          for (DexParser<DexProgramClass> dexParser : dexParsers) {
            tasks.submit(
                () -> {
                  dexParser.addClassDefsTo(
                      classes::add, applicationReaderMap); // Depends on Methods, Code items etc.
                });
          }
        } else {
          // All Dex parsers use the same DEX reader, so don't process in parallel.
          for (int i = 0; i < dexParsers.size(); i++) {
            dexParsers.get(i).addClassDefsTo(classes::add, applicationReaderMap);
          }
        }
      }
    }

    private void addDexParsersForContainer(
        List<DexParser<DexProgramClass>> dexParsers, DexReader dexReader) {
      // Find the start offsets of each dex section.
      IntList offsets = new IntArrayList();
      dexReader.setByteOrder();
      int offset = 0;
      while (offset < dexReader.end()) {
        offsets.add(offset);
        DexReader tmp = new DexReader(Origin.unknown(), dexReader.buffer.array(), offset);
        assert tmp.getDexVersion() == DexVersion.V41;
        assert dexReader.getUint(offset + Constants.HEADER_SIZE_OFFSET)
            == Constants.TYPE_HEADER_ITEM_SIZE_V41;
        assert dexReader.getUint(offset + Constants.CONTAINER_OFF_OFFSET) == offset;
        int dataSize = dexReader.getUint(offset + Constants.DATA_SIZE_OFFSET);
        int dataOffset = dexReader.getUint(offset + Constants.DATA_OFF_OFFSET);
        int file_size = dexReader.getUint(offset + Constants.FILE_SIZE_OFFSET);
        assert dataOffset == 0;
        assert dataSize == 0;
        offset += file_size;
      }
      assert offset == dexReader.end();
      // Create a parser for the last section with string data.
      DexParser<DexProgramClass> last =
          new DexParser<>(dexReader, PROGRAM, options, offsets.getInt(offsets.size() - 1), null);
      // Create a parsers for the remaining sections with reference to the string data.
      for (int i = 0; i < offsets.size() - 1; i++) {
        dexParsers.add(new DexParser<>(dexReader, PROGRAM, options, offsets.getInt(i), last));
      }
      dexParsers.add(last);
    }

    private boolean includeAnnotationClass(DexProgramClass clazz) {
      if (!options.pruneNonVissibleAnnotationClasses) {
        return true;
      }
      DexAnnotation retentionAnnotation =
          clazz.annotations().getFirstMatching(itemFactory.retentionType);
      // Default is CLASS retention, read if retained.
      if (retentionAnnotation == null) {
        return DexAnnotation.retainCompileTimeAnnotation(clazz.getType(), application.options);
      }
      // Otherwise only read runtime visible annotations.
      return retentionAnnotation.annotation.toString().contains("RUNTIME");
    }

    private void readClassSources(
        List<ProgramResource> classSources, Queue<DexProgramClass> classes)
        throws ExecutionException {
      if (classSources.isEmpty()) {
        return;
      }
      hasReadProgramResourceFromCf = true;
      JarClassFileReader<DexProgramClass> reader =
          new JarClassFileReader<>(
              application,
              clazz -> {
                if (clazz.isAnnotation() && !includeAnnotationClass(clazz)) {
                  return;
                }
                classes.add(clazz);
              },
              PROGRAM);
      // Read classes in parallel.
      for (ProgramResource input : classSources) {
        tasks.submit(() -> reader.read(input));
      }
    }

    void readSources() throws IOException, ResourceException, ExecutionException {
      Collection<ProgramResource> resources = inputApp.computeAllProgramResources();
      List<ProgramResource> dexResources = new ArrayList<>(resources.size());
      List<ProgramResource> cfResources = new ArrayList<>(resources.size());
      for (ProgramResource resource : resources) {
        if (resource.getKind() == Kind.DEX) {
          dexResources.add(resource);
        } else {
          assert resource.getKind() == Kind.CF;
          cfResources.add(resource);
        }
      }
      readDexSources(dexResources, programClasses);
      readClassSources(cfResources, programClasses);
    }

    private <T extends DexClass> ClassProvider<T> buildClassProvider(
        ClassKind<T> classKind,
        Queue<T> preloadedClasses,
        List<ClassFileResourceProvider> resourceProviders,
        JarApplicationReader reader) {
      List<ClassProvider<T>> providers = new ArrayList<>();

      // Preloaded classes.
      if (!preloadedClasses.isEmpty()) {
        providers.add(ClassProvider.forPreloadedClasses(classKind, preloadedClasses));
      }

      // Class file resource providers.
      for (ClassFileResourceProvider provider : resourceProviders) {
        providers.add(ClassProvider.forClassFileResources(classKind, provider, reader));
      }

      // Combine if needed.
      if (providers.isEmpty()) {
        return null;
      }
      return providers.size() == 1 ? providers.get(0)
          : ClassProvider.combine(classKind, providers);
    }

    void initializeLazyClassCollection(LazyLoadedDexApplication.Builder builder) {
      // Add all program classes to the builder.
      for (DexProgramClass clazz : programClasses) {
        builder.addProgramClass(clazz.asProgramClass());
      }

      // Create classpath class collection if needed.
      ClassProvider<DexClasspathClass> classpathClassProvider = buildClassProvider(CLASSPATH,
          classpathClasses, inputApp.getClasspathResourceProviders(), application);
      if (classpathClassProvider != null) {
        builder.setClasspathClassCollection(new ClasspathClassCollection(classpathClassProvider));
      }

      // Create library class collection if needed.
      ClassProvider<DexLibraryClass> libraryClassProvider = buildClassProvider(LIBRARY,
          libraryClasses, inputApp.getLibraryResourceProviders(), application);
      if (libraryClassProvider != null) {
        builder.setLibraryClassCollection(new LibraryClassCollection(libraryClassProvider));
      }
    }
  }
}
