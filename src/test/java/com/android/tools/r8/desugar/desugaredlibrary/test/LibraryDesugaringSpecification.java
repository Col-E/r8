// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary.test;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_11_LIB_JAR;
import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.ToolHelper.DESUGARED_LIB_RELEASES_DIR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LATEST;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LEGACY;

import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.jdk11.DesugaredLibraryJDK11Undesugarer;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.rules.TemporaryFolder;

public class LibraryDesugaringSpecification {

  public static Descriptor JDK8_DESCRIPTOR = new Descriptor(24, 26, -1, 26, 24);
  public static Descriptor JDK11_DESCRIPTOR = new Descriptor(24, 26, -1, 10000, -1);
  public static Descriptor EMPTY_DESCRIPTOR_24 = new Descriptor(-1, -1, -1, 24, -1);
  public static Descriptor JDK11_PATH_DESCRIPTOR = new Descriptor(24, 26, 26, 10000, -1);
  public static Descriptor JDK11_LEGACY_DESCRIPTOR = new Descriptor(24, 26, -1, 32, 24);

  private static class Descriptor {

    // Above this level emulated interface are not *entirely* desugared.
    private final int emulatedInterfaceDesugaring;
    // Above this level java.time is not *entirely* desugared.
    private final int timeDesugaring;
    // Above this level java.nio.file is not *entirely* desugared.
    private final int nioFileDesugaring;
    // Above this level no desugaring is required.
    private final int anyDesugaring;
    // Above this level java.function is used, below j$.function is used.
    private final int jDollarFunction;

    private Descriptor(
        int emulatedInterfaceDesugaring,
        int timeDesugaring,
        int nioFileDesugaring,
        int anyDesugaring,
        int jDollarFunction) {
      this.emulatedInterfaceDesugaring = emulatedInterfaceDesugaring;
      this.timeDesugaring = timeDesugaring;
      this.nioFileDesugaring = nioFileDesugaring;
      this.anyDesugaring = anyDesugaring;
      this.jDollarFunction = jDollarFunction;
    }

    public int getEmulatedInterfaceDesugaring() {
      return emulatedInterfaceDesugaring;
    }

    public int getTimeDesugaring() {
      return timeDesugaring;
    }

    public int getNioFileDesugaring() {
      return nioFileDesugaring;
    }

    public int getAnyDesugaring() {
      return anyDesugaring;
    }

    public int getJDollarFunction() {
      return jDollarFunction;
    }
  }

  public enum CustomConversionVersion {
    LEGACY,
    LATEST
  }

  private static final Path tempLibraryJDK11Undesugar = createUndesugaredJdk11LibJarForTesting();

  private static Path createUndesugaredJdk11LibJarForTesting() {
    try {
      TemporaryFolder staticTemp = ToolHelper.getTemporaryFolderForTest();
      staticTemp.create();
      Path jdklib_desugaring = staticTemp.newFolder("jdklib_desugaring").toPath();
      return DesugaredLibraryJDK11Undesugarer.undesugaredJarJDK11(
          jdklib_desugaring, DESUGARED_JDK_11_LIB_JAR);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Path getTempLibraryJDK11Undesugar() {
    return tempLibraryJDK11Undesugar;
  }

  // Main head specifications.
  public static LibraryDesugaringSpecification JDK8 =
      new LibraryDesugaringSpecification(
          "JDK8",
          DESUGARED_JDK_8_LIB_JAR,
          "desugar_jdk_libs.json",
          AndroidApiLevel.P,
          JDK8_DESCRIPTOR,
          LEGACY);
  public static LibraryDesugaringSpecification JDK11 =
      new LibraryDesugaringSpecification(
          "JDK11",
          tempLibraryJDK11Undesugar,
          "jdk11/desugar_jdk_libs.json",
          AndroidApiLevel.R,
          JDK11_DESCRIPTOR,
          LATEST);
  public static LibraryDesugaringSpecification JDK11_MINIMAL =
      new LibraryDesugaringSpecification(
          "JDK11_MINIMAL",
          tempLibraryJDK11Undesugar,
          "jdk11/desugar_jdk_libs_minimal.json",
          AndroidApiLevel.R,
          EMPTY_DESCRIPTOR_24,
          LATEST);
  public static LibraryDesugaringSpecification JDK11_PATH =
      new LibraryDesugaringSpecification(
          "JDK11_PATH",
          tempLibraryJDK11Undesugar,
          "jdk11/desugar_jdk_libs_nio.json",
          AndroidApiLevel.R,
          JDK11_PATH_DESCRIPTOR,
          LATEST);

  // Legacy specifications.
  public static LibraryDesugaringSpecification JDK11_LEGACY =
      new LibraryDesugaringSpecification(
          "JDK11_LEGACY",
          // The legacy specification is not using the undesugared JAR.
          DESUGARED_JDK_11_LIB_JAR,
          "jdk11/desugar_jdk_libs_legacy.json",
          AndroidApiLevel.T,
          JDK11_LEGACY_DESCRIPTOR,
          LEGACY);
  public static final LibraryDesugaringSpecification RELEASED_1_0_9 =
      new LibraryDesugaringSpecification("1.0.9", AndroidApiLevel.P);
  public static final LibraryDesugaringSpecification RELEASED_1_0_10 =
      new LibraryDesugaringSpecification("1.0.10", AndroidApiLevel.P);
  public static final LibraryDesugaringSpecification RELEASED_1_1_0 =
      new LibraryDesugaringSpecification("1.1.0", AndroidApiLevel.P);
  public static final LibraryDesugaringSpecification RELEASED_1_1_1 =
      new LibraryDesugaringSpecification("1.1.1", AndroidApiLevel.P);
  public static final LibraryDesugaringSpecification RELEASED_1_1_5 =
      new LibraryDesugaringSpecification("1.1.5", AndroidApiLevel.P);

  private final String name;
  private final Set<Path> desugarJdkLibs;
  private final Path specification;
  private final Set<Path> libraryFiles;
  private final Descriptor descriptor;
  private final String extraKeepRules;

  public LibraryDesugaringSpecification(
      String name,
      Path desugarJdkLibs,
      String specificationPath,
      AndroidApiLevel androidJarLevel,
      Descriptor descriptor,
      CustomConversionVersion legacy) {
    this(
        name,
        ImmutableSet.of(desugarJdkLibs, ToolHelper.getDesugarLibConversions(legacy)),
        Paths.get(ToolHelper.LIBRARY_DESUGAR_SOURCE_DIR + specificationPath),
        ImmutableSet.of(ToolHelper.getAndroidJar(androidJarLevel)),
        descriptor,
        "");
  }

  // This can be used to build custom specifications for testing purposes.
  public LibraryDesugaringSpecification(
      String name,
      Set<Path> desugarJdkLibs,
      Path specification,
      Set<Path> libraryFiles,
      Descriptor descriptor,
      String extraKeepRules) {
    this.name = name;
    this.desugarJdkLibs = desugarJdkLibs;
    this.specification = specification;
    this.libraryFiles = libraryFiles;
    this.descriptor = descriptor;
    this.extraKeepRules = extraKeepRules;
  }

  private LibraryDesugaringSpecification(String version, AndroidApiLevel androidJarLevel) {
    this(
        "RELEASED_" + version,
        ImmutableSet.of(
            Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar_jdk_libs.jar"),
            Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar_jdk_libs_configuration.jar")),
        Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar.json"),
        ImmutableSet.of(ToolHelper.getAndroidJar(androidJarLevel)),
        JDK8_DESCRIPTOR,
        "");
  }

  @Override
  public String toString() {
    return name;
  }

  public Set<Path> getDesugarJdkLibs() {
    return desugarJdkLibs;
  }

  public Path getSpecification() {
    return specification;
  }

  public Set<Path> getLibraryFiles() {
    return libraryFiles;
  }

  public Descriptor getDescriptor() {
    return descriptor;
  }

  public String getExtraKeepRules() {
    return extraKeepRules;
  }

  public void configureL8TestBuilder(L8TestBuilder l8TestBuilder) {
    configureL8TestBuilder(l8TestBuilder, false, "");
  }

  public void configureL8TestBuilder(
      L8TestBuilder l8TestBuilder, boolean l8Shrink, String keepRule) {
    l8TestBuilder
        .addProgramFiles(getDesugarJdkLibs())
        .addLibraryFiles(getLibraryFiles())
        .setDesugaredLibrarySpecification(getSpecification())
        .applyIf(
            l8Shrink,
            builder -> {
              assert keepRule != null;
              String totalKeepRules =
                  keepRule + (getExtraKeepRules().isEmpty() ? "" : ("\n" + getExtraKeepRules()));
              builder.addGeneratedKeepRules(totalKeepRules);
            },
            L8TestBuilder::setDebug);
  }

  public static List<LibraryDesugaringSpecification> getReleased() {
    return ImmutableList.of(
        RELEASED_1_0_9, RELEASED_1_0_10, RELEASED_1_1_0, RELEASED_1_1_1, RELEASED_1_1_5);
  }

  public static List<LibraryDesugaringSpecification> getJdk8Jdk11() {
    return ImmutableList.of(JDK8, JDK11);
  }

  public static List<LibraryDesugaringSpecification> getJdk8AndAll3Jdk11() {
    return ImmutableList.of(JDK8, JDK11, JDK11_MINIMAL, JDK11_PATH);
  }

  public DexApplication getAppForTesting(InternalOptions options, boolean libraryCompilation)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    if (libraryCompilation) {
      builder.addProgramFiles(getDesugarJdkLibs());
    }
    AndroidApp inputApp = builder.addLibraryFiles(getLibraryFiles()).build();
    return internalReadApp(inputApp, options);
  }

  private DexApplication internalReadApp(AndroidApp inputApp, InternalOptions options)
      throws IOException {
    ApplicationReader applicationReader = new ApplicationReader(inputApp, options, Timing.empty());
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    assert !options.ignoreJavaLibraryOverride;
    options.ignoreJavaLibraryOverride = true;
    DexApplication app = applicationReader.read(executorService);
    options.ignoreJavaLibraryOverride = false;
    return app;
  }

  public boolean hasEmulatedInterfaceDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < descriptor.getEmulatedInterfaceDesugaring();
  }

  public boolean hasTimeDesugaring(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < descriptor.getTimeDesugaring();
  }

  public boolean hasNioFileDesugaring(TestParameters parameters) {
    return hasNioFileDesugaring(parameters.getApiLevel());
  }

  public boolean hasNioFileDesugaring(AndroidApiLevel apiLevel) {
    return apiLevel.getLevel() < descriptor.getNioFileDesugaring();
  }

  public boolean hasNioChannelDesugaring(TestParameters parameters) {
    return hasNioFileDesugaring(parameters) && parameters.getApiLevel().getLevel() < 24;
  }

  public boolean usesPlatformFileSystem(TestParameters parameters) {
    return parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V8_1_0);
  }

  public boolean hasAnyDesugaring(TestParameters parameters) {
    return hasAnyDesugaring(parameters.getApiLevel());
  }

  public boolean hasAnyDesugaring(AndroidApiLevel apiLevel) {
    return apiLevel.getLevel() < descriptor.getAnyDesugaring();
  }

  public boolean hasJDollarFunction(TestParameters parameters) {
    return parameters.getApiLevel().getLevel() < descriptor.getJDollarFunction();
  }

  public String functionPrefix(TestParameters parameters) {
    return hasJDollarFunction(parameters) ? "j$" : "java";
  }
}
