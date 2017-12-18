// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import static com.android.tools.r8.utils.FileUtils.isArchive;
import static com.android.tools.r8.utils.FileUtils.isClassFile;
import static com.android.tools.r8.utils.FileUtils.isDexFile;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.Resource;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Collection of program files needed for processing.
 *
 * <p>This abstraction is the main input and output container for a given application.
 */
public class AndroidApp {

  public static final String DEFAULT_PROGUARD_MAP_FILE = "proguard.map";

  private final ImmutableList<ProgramResource> programResources;
  private final ImmutableMap<Resource, String> programResourcesMainDescriptor;
  private final ImmutableList<ClassFileResourceProvider> classpathResourceProviders;
  private final ImmutableList<ClassFileResourceProvider> libraryResourceProviders;

  private final ImmutableList<ProgramFileArchiveReader> programFileArchiveReaders;
  private final StringResource proguardMap;
  private final List<StringResource> mainDexListResources;
  private final List<String> mainDexClasses;

  // See factory methods and AndroidApp.Builder below.
  private AndroidApp(
      ImmutableList<ProgramResource> programResources,
      ImmutableMap<Resource, String> programResourcesMainDescriptor,
      ImmutableList<ProgramFileArchiveReader> programFileArchiveReaders,
      ImmutableList<ClassFileResourceProvider> classpathResourceProviders,
      ImmutableList<ClassFileResourceProvider> libraryResourceProviders,
      StringResource proguardMap,
      List<StringResource> mainDexListResources,
      List<String> mainDexClasses) {
    this.programResources = programResources;
    this.programResourcesMainDescriptor = programResourcesMainDescriptor;
    this.programFileArchiveReaders = programFileArchiveReaders;
    this.classpathResourceProviders = classpathResourceProviders;
    this.libraryResourceProviders = libraryResourceProviders;
    this.proguardMap = proguardMap;
    this.mainDexListResources = mainDexListResources;
    this.mainDexClasses = mainDexClasses;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Create a new builder initialized with the resources from @code{app}.
   */
  public static Builder builder(AndroidApp app) {
    return new Builder(app);
  }

  /**
   * Create an app from program files @code{files}. See also Builder::addProgramFiles.
   */
  public static AndroidApp fromProgramFiles(Path... files) throws IOException {
    return fromProgramFiles(Arrays.asList(files));
  }

  /**
   * Create an app from program files @code{files}. See also Builder::addProgramFiles.
   */
  public static AndroidApp fromProgramFiles(List<Path> files) throws IOException {
    return builder().addProgramFiles(ListUtils.map(files, FilteredClassPath::unfiltered)).build();
  }

  /**
   * Create an app from files found in @code{directory}. See also Builder::addProgramDirectory.
   */
  public static AndroidApp fromProgramDirectory(Path directory) throws IOException {
    return builder().addProgramDirectory(directory).build();
  }

  /**
   * Create an app from dex program data. See also Builder::addDexProgramData.
   */
  public static AndroidApp fromDexProgramData(byte[]... data) {
    return fromDexProgramData(Arrays.asList(data));
  }

  /**
   * Create an app from dex program data. See also Builder::addDexProgramData.
   */
  public static AndroidApp fromDexProgramData(List<byte[]> data) {
    return builder().addDexProgramData(data).build();
  }

  /**
   * Create an app from Java-bytecode program data. See also Builder::addClassProgramData.
   */
  public static AndroidApp fromClassProgramData(byte[]... data) {
    return fromClassProgramData(Arrays.asList(data));
  }

  /**
   * Create an app from Java-bytecode program data. See also Builder::addClassProgramData.
   */
  public static AndroidApp fromClassProgramData(List<byte[]> data) {
    return builder().addClassProgramData(data).build();
  }

  /** Get input streams for all dex program resources. */
  public List<ProgramResource> getDexProgramResources() throws IOException {
    List<ProgramResource> dexResources = filter(programResources, Kind.DEX);
    for (ProgramFileArchiveReader reader : programFileArchiveReaders) {
      dexResources.addAll(reader.getDexProgramResources());
    }
    return dexResources;
  }

  public List<ProgramResource> getDexProgramResourcesForOutput() {
    assert programFileArchiveReaders.isEmpty();
    return filter(programResources, Kind.DEX);
  }

  /** Get input streams for all Java-bytecode program resources. */
  public List<ProgramResource> getClassProgramResources() throws IOException {
    List<ProgramResource> classResources = filter(programResources, Kind.CF);
    for (ProgramFileArchiveReader reader : programFileArchiveReaders) {
      classResources.addAll(reader.getClassProgramResources());
    }
    return classResources;
  }

  /** Get classpath resource providers. */
  public List<ClassFileResourceProvider> getClasspathResourceProviders() {
    return classpathResourceProviders;
  }

  /** Get library resource providers. */
  public List<ClassFileResourceProvider> getLibraryResourceProviders() {
    return libraryResourceProviders;
  }

  public List<ProgramFileArchiveReader> getProgramFileArchiveReaders() {
    return programFileArchiveReaders;
  }

  private List<ProgramResource> filter(List<ProgramResource> resources, Kind kind) {
    List<ProgramResource> out = new ArrayList<>(resources.size());
    for (ProgramResource code : resources) {
      if (code.getKind() == kind) {
        out.add(code);
      }
    }
    return out;
  }

  /**
   * Get the input stream of the proguard-map resource if it exists.
   */
  public StringResource getProguardMap() {
    return proguardMap;
  }

  /**
   * True if the main dex list resources exists.
   */
  public boolean hasMainDexList() {
    return !(mainDexListResources.isEmpty() && mainDexClasses.isEmpty());
  }

  /**
   * True if the main dex list resources exists.
   */
  public boolean hasMainDexListResources() {
    return !mainDexListResources.isEmpty();
  }

  /**
   * Get the main dex list resources if any.
   */
  public List<StringResource> getMainDexListResources() {
    return mainDexListResources;
  }

  /**
   * Get the main dex classes if any.
   */
  public List<String> getMainDexClasses() {
    return mainDexClasses;
  }

  /**
   * Write the dex program resources and proguard resource to @code{output}.
   */
  public void write(Path output, OutputMode outputMode) throws IOException {
    if (isArchive(output)) {
      writeToZip(output, outputMode);
    } else {
      writeToDirectory(output, outputMode);
    }
  }

  /**
   * Write the dex program resources and proguard resource to @code{directory}.
   */
  public void writeToDirectory(Path directory, OutputMode outputMode) throws IOException {
    List<ProgramResource> dexProgramSources = getDexProgramResources();
    if (outputMode.isDexIndexed()) {
      DexIndexedConsumer.DirectoryConsumer.writeResources(directory, dexProgramSources);
    } else {
      DexFilePerClassFileConsumer.DirectoryConsumer.writeResources(
          directory, dexProgramSources, programResourcesMainDescriptor);
    }
  }

  public List<byte[]> writeToMemory() throws IOException {
    List<byte[]> dex = new ArrayList<>();
    try (Closer closer = Closer.create()) {
      List<ProgramResource> dexProgramSources = getDexProgramResources();
      for (int i = 0; i < dexProgramSources.size(); i++) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteStreams.copy(closer.register(dexProgramSources.get(i).getStream()), out);
        dex.add(out.toByteArray());
      }
      // TODO(sgjesse): Add Proguard map and seeds.
    }
    return dex;
  }

  /**
   * Write the dex program resources to @code{archive} and the proguard resource as its sibling.
   */
  public void writeToZip(Path archive, OutputMode outputMode) throws IOException {
    List<ProgramResource> resources = getDexProgramResources();
    if (outputMode.isDexIndexed()) {
      DexIndexedConsumer.ArchiveConsumer.writeResources(archive, resources);
    } else if (outputMode.isDexFilePerClassFile()) {
      DexFilePerClassFileConsumer.ArchiveConsumer.writeResources(
          archive, resources, programResourcesMainDescriptor);
    } else {
      throw new Unreachable("Unsupported output-mode for writing: " + outputMode);
    }
  }

  // Public for testing.
  public String getPrimaryClassDescriptor(Resource resource) {
    assert resource instanceof ProgramResource;
    return programResourcesMainDescriptor.get(resource);
  }

  /**
   * Builder interface for constructing an AndroidApp.
   */
  public static class Builder {

    private final List<ProgramResource> programResources = new ArrayList<>();
    private final Map<ProgramResource, String> programResourcesMainDescriptor = new HashMap<>();
    private final List<ProgramFileArchiveReader> programFileArchiveReaders = new ArrayList<>();
    private final List<ClassFileResourceProvider> classpathResourceProviders = new ArrayList<>();
    private final List<ClassFileResourceProvider> libraryResourceProviders = new ArrayList<>();
    private StringResource proguardMap;
    private List<StringResource> mainDexListResources = new ArrayList<>();
    private List<String> mainDexListClasses = new ArrayList<>();
    private boolean ignoreDexInArchive = false;

    // See AndroidApp::builder().
    private Builder() {
    }

    // See AndroidApp::builder(AndroidApp).
    private Builder(AndroidApp app) {
      programResources.addAll(app.programResources);
      programFileArchiveReaders.addAll(app.programFileArchiveReaders);
      classpathResourceProviders.addAll(app.classpathResourceProviders);
      libraryResourceProviders.addAll(app.libraryResourceProviders);
      proguardMap = app.proguardMap;
      mainDexListResources = app.mainDexListResources;
      mainDexListClasses = app.mainDexClasses;
    }

    /**
     * Add dex program files and proguard-map file located in @code{directory}.
     *
     * <p>The program files included are the top-level files ending in .dex and the proguard-map
     * file should it exist (see @code{DEFAULT_PROGUARD_MAP_FILE} for its assumed name).
     *
     * <p>This method is mostly a utility for reading in the file content produces by some external
     * tool, eg, dx.
     *
     * @param directory Directory containing dex program files and optional proguard-map file.
     */
    public Builder addProgramDirectory(Path directory) throws IOException {
      List<FilteredClassPath> resources =
          Arrays.asList(directory.toFile().listFiles(file -> isDexFile(file.toPath()))).stream()
              .map(file -> FilteredClassPath.unfiltered(file)).collect(Collectors.toList());
      addProgramFiles(resources);
      File mapFile = new File(directory.toFile(), DEFAULT_PROGUARD_MAP_FILE);
      if (mapFile.exists()) {
        setProguardMapFile(mapFile.toPath());
      }
      return this;
    }

    /**
     * Add program file resources.
     */
    public Builder addProgramFiles(FilteredClassPath... files) throws NoSuchFileException {
      return addProgramFiles(Arrays.asList(files));
    }

    /**
     * Add program file resources.
     */
    public Builder addProgramFiles(Collection<FilteredClassPath> files) throws NoSuchFileException {
      for (FilteredClassPath file : files) {
        addProgramFile(file);
      }
      return this;
    }

    /**
     * Add classpath file resources.
     */
    public Builder addClasspathFiles(Path... files) throws IOException {
      return addClasspathFiles(Arrays.asList(files));
    }

    /**
     * Add classpath file resources.
     */
    public Builder addClasspathFiles(Collection<Path> files) throws IOException {
      for (Path file : files) {
        addClasspathFile(file);
      }
      return this;
    }

    /**
     * Add classpath file resources.
     */
    public Builder addClasspathFile(Path file) throws IOException {
      addClassProvider(FilteredClassPath.unfiltered(file), classpathResourceProviders);
      return this;
    }

    /**
     * Add classpath resource provider.
     */
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      classpathResourceProviders.add(provider);
      return this;
    }

    /**
     * Add library file resources.
     */
    public Builder addLibraryFiles(FilteredClassPath... files) throws IOException {
      return addLibraryFiles(Arrays.asList(files));
    }

    /**
     * Add library file resources.
     */
    public Builder addLibraryFiles(Collection<FilteredClassPath> files) throws IOException {
      for (FilteredClassPath file : files) {
        addClassProvider(file, libraryResourceProviders);
      }
      return this;
    }

    /**
     * Add library file resource.
     */
    public Builder addLibraryFile(FilteredClassPath file) throws IOException {
      addClassProvider(file, libraryResourceProviders);
      return this;
    }

    /**
     * Add library resource provider.
     */
    public Builder addLibraryResourceProvider(ClassFileResourceProvider provider) {
      libraryResourceProviders.add(provider);
      return this;
    }

    /**
     * Add dex program-data with class descriptor.
     */
    public Builder addDexProgramData(byte[] data, Set<String> classDescriptors) {
      addProgramResources(
          ProgramResource.fromBytes(Origin.unknown(), Kind.DEX, data, classDescriptors));
      return this;
    }

    /**
     * Add dex program-data with class descriptor and primary class.
     */
    public Builder addDexProgramData(
        byte[] data,
        Set<String> classDescriptors,
        String primaryClassDescriptor) {
      ProgramResource resource = ProgramResource.fromBytes(
          Origin.unknown(), Kind.DEX, data, classDescriptors);
      programResources.add(resource);
      programResourcesMainDescriptor.put(resource, primaryClassDescriptor);
      return this;
    }

    /**
     * Add dex program-data.
     */
    public Builder addDexProgramData(byte[] data, Origin origin) {
      addProgramResources(ProgramResource.fromBytes(origin, Kind.DEX, data, null));
      return this;
    }

    /**
     * Add dex program-data.
     */
    public Builder addDexProgramData(Collection<byte[]> data) {
      for (byte[] datum : data) {
        addProgramResources(ProgramResource.fromBytes(Origin.unknown(), Kind.DEX, datum, null));
      }
      return this;
    }

    /**
     * Add Java-bytecode program data.
     */
    public Builder addClassProgramData(Collection<byte[]> data) {
      for (byte[] datum : data) {
        addClassProgramData(datum, Origin.unknown());
      }
      return this;
    }

    /**
     * Add Java-bytecode program data.
     */
    public Builder addClassProgramData(byte[] data, Origin origin) {
      return addClassProgramData(data, origin, null);
    }

    public Builder addClassProgramData(byte[] data, Origin origin, Set<String> classDescriptors) {
      addProgramResources(ProgramResource.fromBytes(origin, Kind.CF, data, classDescriptors));
      return this;
    }

    /**
     * Set proguard-map file.
     */
    public Builder setProguardMapFile(Path file) {
      proguardMap = file == null ? null : StringResource.fromFile(file);
      return this;
    }

    /**
     * Set proguard-map data.
     */
    public Builder setProguardMapData(String content) {
      proguardMap = content == null ? null : StringResource.fromString(content, Origin.unknown());
      return this;
    }

    /**
     * Add a main-dex list file.
     */
    public Builder addMainDexListFiles(Path... files) throws NoSuchFileException {
      return addMainDexListFiles(Arrays.asList(files));
    }

    public Builder addMainDexListFiles(Collection<Path> files) throws NoSuchFileException {
      for (Path file : files) {
        if (!Files.exists(file)) {
          throw new NoSuchFileException(file.toString());
        }
        // TODO(sgjesse): Should we just read the file here? This will sacrifice the parallelism
        // in ApplicationReader where all input resources are read in parallel.
        mainDexListResources.add(StringResource.fromFile(file));
      }
      return this;
    }

    /**
     * Add main-dex classes.
     */
    public Builder addMainDexClasses(String... classes) {
      return addMainDexClasses(Arrays.asList(classes));
    }

    /**
     * Add main-dex classes.
     */
    public Builder addMainDexClasses(Collection<String> classes) {
      mainDexListClasses.addAll(classes);
      return this;
    }

    public boolean hasMainDexList() {
      return !(mainDexListResources.isEmpty() && mainDexListClasses.isEmpty());
    }

    /**
     * Ignore dex resources in input archives.
     *
     * In some situations (e.g. AOSP framework build) the input archives include both class and
     * dex resources. Setting this flag ignores the dex resources and reads the class resources
     * only.
     */
    public Builder setIgnoreDexInArchive(boolean value) {
      ignoreDexInArchive = value;
      return this;
    }

    /**
     * Build final AndroidApp.
     */
    public AndroidApp build() {
      return new AndroidApp(
          ImmutableList.copyOf(programResources),
          ImmutableMap.copyOf(programResourcesMainDescriptor),
          ImmutableList.copyOf(programFileArchiveReaders),
          ImmutableList.copyOf(classpathResourceProviders),
          ImmutableList.copyOf(libraryResourceProviders),
          proguardMap,
          mainDexListResources,
          mainDexListClasses);
    }

    public void addProgramFile(FilteredClassPath filteredClassPath) throws NoSuchFileException {
      Path file = filteredClassPath.getPath();
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
      if (isDexFile(file)) {
        addProgramResources(ProgramResource.fromFile(Kind.DEX, file));
      } else if (isClassFile(file)) {
        addProgramResources(ProgramResource.fromFile(Kind.CF, file));
      } else if (isArchive(file)) {
        programFileArchiveReaders
            .add(new ProgramFileArchiveReader(filteredClassPath, ignoreDexInArchive));
      } else {
        throw new CompilationError("Unsupported source file type", new PathOrigin(file));
      }
    }

    private void addProgramResources(ProgramResource... resources) {
      addProgramResources(Arrays.asList(resources));
    }

    private void addProgramResources(Collection<ProgramResource> resources) {
      programResources.addAll(resources);
    }

    private void addClassProvider(FilteredClassPath classPath,
        List<ClassFileResourceProvider> providerList)
        throws IOException {
      Path file = classPath.getPath();
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
      if (isArchive(file)) {
        providerList.add(ArchiveClassFileProvider.fromArchive(classPath));
      } else if (Files.isDirectory(file) ) {
        // This is only used for D8 incremental compilation.
        assert classPath.isUnfiltered();
        providerList.add(DirectoryClassFileProvider.fromDirectory(file));
      } else {
        throw new CompilationError("Unsupported source file type", new PathOrigin(file));
      }
    }
  }
}
