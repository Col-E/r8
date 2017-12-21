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
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.Resource;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
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

/**
 * Collection of program files needed for processing.
 *
 * <p>This abstraction is the main input and output container for a given application.
 */
public class AndroidApp {

  private final ImmutableList<ProgramResourceProvider> programResourceProviders;
  private final ImmutableMap<Resource, String> programResourcesMainDescriptor;
  private final ImmutableList<ClassFileResourceProvider> classpathResourceProviders;
  private final ImmutableList<ClassFileResourceProvider> libraryResourceProviders;

  private final StringResource proguardMap;
  private final List<StringResource> mainDexListResources;
  private final List<String> mainDexClasses;

  // See factory methods and AndroidApp.Builder below.
  private AndroidApp(
      ImmutableList<ProgramResourceProvider> programResourceProviders,
      ImmutableMap<Resource, String> programResourcesMainDescriptor,
      ImmutableList<ClassFileResourceProvider> classpathResourceProviders,
      ImmutableList<ClassFileResourceProvider> libraryResourceProviders,
      StringResource proguardMap,
      List<StringResource> mainDexListResources,
      List<String> mainDexClasses) {
    this.programResourceProviders = programResourceProviders;
    this.programResourcesMainDescriptor = programResourcesMainDescriptor;
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
    return builder().addProgramFiles(files).build();
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
    try {
      return filter(programResourceProviders, Kind.DEX);
    } catch (ResourceException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        throw new InternalCompilerError("Unexpected resource error", e);
      }
    }
  }

  /** Get input streams for all Java-bytecode program resources. */
  public List<ProgramResource> getClassProgramResources() throws IOException {
    try {
      return filter(programResourceProviders, Kind.CF);
    } catch (ResourceException e) {
      if (e.getCause() instanceof IOException) {
        throw (IOException) e.getCause();
      } else {
        throw new InternalCompilerError("Unexpected resource error", e);
      }
    }
  }

  /** Get program resource providers. */
  public List<ProgramResourceProvider> getProgramResourceProviders() {
    return programResourceProviders;
  }

  /** Get classpath resource providers. */
  public List<ClassFileResourceProvider> getClasspathResourceProviders() {
    return classpathResourceProviders;
  }

  /** Get library resource providers. */
  public List<ClassFileResourceProvider> getLibraryResourceProviders() {
    return libraryResourceProviders;
  }

  private List<ProgramResource> filter(List<ProgramResourceProvider> providers, Kind kind)
      throws ResourceException {
    List<ProgramResource> out = new ArrayList<>();
    for (ProgramResourceProvider provider : providers) {
      for (ProgramResource code : provider.getProgramResources()) {
        if (code.getKind() == kind) {
          out.add(code);
        }
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

    private final List<ProgramResourceProvider> programResourceProviders = new ArrayList<>();
    private final List<ProgramResource> programResources = new ArrayList<>();
    private final Map<ProgramResource, String> programResourcesMainDescriptor = new HashMap<>();
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
      programResourceProviders.addAll(app.programResourceProviders);
      classpathResourceProviders.addAll(app.classpathResourceProviders);
      libraryResourceProviders.addAll(app.libraryResourceProviders);
      proguardMap = app.proguardMap;
      mainDexListResources = app.mainDexListResources;
      mainDexListClasses = app.mainDexClasses;
    }

    /** Add program file resources. */
    public Builder addProgramFiles(Path... files) throws NoSuchFileException {
      return addProgramFiles(Arrays.asList(files));
    }

    /** Add program file resources. */
    public Builder addProgramFiles(Collection<Path> files) throws NoSuchFileException {
      for (Path file : files) {
        addProgramFile(file);
      }
      return this;
    }

    /** Add filtered archives of program resources. */
    public Builder addFilteredProgramArchives(Collection<FilteredClassPath> filteredArchives)
        throws NoSuchFileException {
      for (FilteredClassPath archive : filteredArchives) {
        assert isArchive(archive.getPath());
        addProgramResourceProvider(
            new FilteredArchiveProgramResourceProvider(archive, ignoreDexInArchive));
      }
      return this;
    }

    public Builder addProgramResourceProvider(ProgramResourceProvider provider) {
      assert provider != null;
      programResourceProviders.add(provider);
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
      addClasspathOrLibraryProvider(file, classpathResourceProviders);
      return this;
    }

    /**
     * Add classpath resource provider.
     */
    public Builder addClasspathResourceProvider(ClassFileResourceProvider provider) {
      classpathResourceProviders.add(provider);
      return this;
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Path... files) throws IOException {
      return addLibraryFiles(Arrays.asList(files));
    }

    /** Add library file resources. */
    public Builder addLibraryFiles(Collection<Path> files) throws IOException {
      for (Path file : files) {
        addClasspathOrLibraryProvider(file, libraryResourceProviders);
      }
      return this;
    }

    /** Add library file resource. */
    public Builder addLibraryFile(Path file) throws IOException {
      addClasspathOrLibraryProvider(file, libraryResourceProviders);
      return this;
    }

    /** Add library file resources. */
    public Builder addFilteredLibraryArchives(Collection<FilteredClassPath> filteredArchives)
        throws IOException {
      for (FilteredClassPath archive : filteredArchives) {
        assert isArchive(archive.getPath());
        libraryResourceProviders.add(new FilteredArchiveClassFileProvider(archive));
      }
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
      if (!programResources.isEmpty()) {
        // If there are individual program resources move them to a dedicated provider.
        final List<ProgramResource> resources = ImmutableList.copyOf(programResources);
        programResourceProviders.add(
            new ProgramResourceProvider() {
              @Override
              public Collection<ProgramResource> getProgramResources() throws ResourceException {
                return resources;
              }
            });
        programResources.clear();
      }
      return new AndroidApp(
          ImmutableList.copyOf(programResourceProviders),
          ImmutableMap.copyOf(programResourcesMainDescriptor),
          ImmutableList.copyOf(classpathResourceProviders),
          ImmutableList.copyOf(libraryResourceProviders),
          proguardMap,
          mainDexListResources,
          mainDexListClasses);
    }

    public void addProgramFile(Path file) throws NoSuchFileException {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
      if (isDexFile(file)) {
        addProgramResources(ProgramResource.fromFile(Kind.DEX, file));
      } else if (isClassFile(file)) {
        addProgramResources(ProgramResource.fromFile(Kind.CF, file));
      } else if (isArchive(file)) {
        addProgramResourceProvider(new ArchiveProgramResourceProvider(file, ignoreDexInArchive));
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

    private void addClasspathOrLibraryProvider(
        Path file, List<ClassFileResourceProvider> providerList) throws IOException {
      if (!Files.exists(file)) {
        throw new NoSuchFileException(file.toString());
      }
      if (isArchive(file)) {
        providerList.add(new ArchiveClassFileProvider(file));
      } else if (Files.isDirectory(file) ) {
        providerList.add(DirectoryClassFileProvider.fromDirectory(file));
      } else {
        throw new CompilationError("Unsupported source file type", new PathOrigin(file));
      }
    }
  }
}
