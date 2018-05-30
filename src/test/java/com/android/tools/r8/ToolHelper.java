// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.isDexFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DeviceRunner.DeviceRunnerConfigurationException;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DefaultDiagnosticsHandler;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class ToolHelper {

  public static final String BUILD_DIR = "build/";
  public static final String TESTS_DIR = "src/test/";
  public static final String EXAMPLES_DIR = TESTS_DIR + "examples/";
  public static final String EXAMPLES_ANDROID_O_DIR = TESTS_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_DIR = TESTS_DIR + "examplesAndroidP/";
  public static final String EXAMPLES_KOTLIN_DIR = TESTS_DIR + "examplesKotlin/";
  public static final String TESTS_BUILD_DIR = BUILD_DIR + "test/";
  public static final String EXAMPLES_BUILD_DIR = TESTS_BUILD_DIR + "examples/";
  public static final String EXAMPLES_KOTLIN_BUILD_DIR = TESTS_BUILD_DIR + "examplesKotlin/";
  public static final String EXAMPLES_ANDROID_N_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidN/";
  public static final String EXAMPLES_ANDROID_O_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidP/";
  public static final String EXAMPLES_JAVA9_BUILD_DIR = TESTS_BUILD_DIR + "examplesJava9/";
  public static final String SMALI_DIR = TESTS_DIR + "smali/";
  public static final String SMALI_BUILD_DIR = TESTS_BUILD_DIR + "smali/";

  public static final String LINE_SEPARATOR = StringUtils.LINE_SEPARATOR;
  public final static String PATH_SEPARATOR = File.pathSeparator;
  public static final String DEFAULT_DEX_FILENAME = "classes.dex";
  public static final String DEFAULT_PROGUARD_MAP_FILE = "proguard.map";

  public static final String JAVA_8_RUNTIME = "third_party/openjdk/openjdk-rt-1.8/rt.jar";
  public static final String KT_RUNTIME = "third_party/kotlin/kotlinc/lib/kotlin-runtime.jar";
  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";
  private static final AndroidApiLevel DEFAULT_MIN_SDK = AndroidApiLevel.I;

  private static final String PROGUARD5_2_1 = "third_party/proguard/proguard5.2.1/bin/proguard";
  private static final String PROGUARD6_0_1 = "third_party/proguard/proguard6.0.1/bin/proguard";
  private static final String PROGUARD = PROGUARD5_2_1;

  public enum DexVm {
    ART_4_0_4_TARGET(Version.V4_0_4, Kind.TARGET),
    ART_4_0_4_HOST(Version.V4_0_4, Kind.HOST),
    ART_4_4_4_TARGET(Version.V4_4_4, Kind.TARGET),
    ART_4_4_4_HOST(Version.V4_4_4, Kind.HOST),
    ART_5_1_1_TARGET(Version.V5_1_1, Kind.TARGET),
    ART_5_1_1_HOST(Version.V5_1_1, Kind.HOST),
    ART_6_0_1_TARGET(Version.V6_0_1, Kind.TARGET),
    ART_6_0_1_HOST(Version.V6_0_1, Kind.HOST),
    ART_7_0_0_TARGET(Version.V7_0_0, Kind.TARGET),
    ART_7_0_0_HOST(Version.V7_0_0, Kind.HOST),
    ART_DEFAULT(Version.DEFAULT, Kind.HOST);

    private static final ImmutableMap<String, DexVm> SHORT_NAME_MAP =
        Arrays.stream(DexVm.values()).collect(ImmutableMap.toImmutableMap(
            DexVm::toString, Function.identity()));


    public enum Version {
      V4_0_4("4.0.4"),
      V4_4_4("4.4.4"),
      V5_1_1("5.1.1"),
      V6_0_1("6.0.1"),
      V7_0_0("7.0.0"),
      DEFAULT("default");

      Version(String shortName) {
        this.shortName = shortName;
      }

      public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
      }

      public boolean isAtLeast(Version other) {
        return compareTo(other) >= 0;
      }

      public boolean isOlderThanOrEqual(Version other) {
        return compareTo(other) <= 0;
      }

      public String toString() {
        return shortName;
      }

      private String shortName;

      public static Version first() {
        return V4_0_4;
      }

      public static Version last() {
        return DEFAULT;
      }

      static {
        // Ensure first is always first and last is always last.
        assert Arrays.stream(values()).allMatch(v -> v == first() || v.compareTo(first()) > 0);
        assert Arrays.stream(values()).allMatch(v -> v == last() || v.compareTo(last()) < 0);
      }
    }

    public enum Kind {
      HOST("host"),
      TARGET("target");

      Kind(String shortName) {
        this.shortName = shortName;
      }

      public String toString() {
        return shortName;
      }

      private String shortName;
    }

    public String toString() {
      return version.shortName + '_' + kind.shortName;
    }

    public static DexVm fromShortName(String shortName) {
      return SHORT_NAME_MAP.get(shortName);
    }

    public boolean isNewerThan(DexVm other) {
      return version.isNewerThan(other.version);
    }

    public boolean isOlderThanOrEqual(DexVm other) {
      return version.isOlderThanOrEqual(other.version);
    }

    DexVm(Version version, Kind kind) {
      this.version = version;
      this.kind = kind;
    }

    public Version getVersion() {
      return version;
    }

    public Kind getKind() {
      return kind;
    }

    private final Version version;
    private final Kind kind;
  }


  public abstract static class CommandBuilder {

    protected List<String> options = new ArrayList<>();
    protected Map<String, String> systemProperties = new LinkedHashMap<>();
    protected List<String> classpaths = new ArrayList<>();
    protected String mainClass;
    protected List<String> programArguments = new ArrayList<>();
    protected List<String> bootClassPaths = new ArrayList<>();

    public CommandBuilder appendArtOption(String option) {
      options.add(option);
      return this;
    }

    public CommandBuilder appendArtSystemProperty(String key, String value) {
      systemProperties.put(key, value);
      return this;
    }

    public CommandBuilder appendClasspath(String classpath) {
      classpaths.add(classpath);
      return this;
    }

    public CommandBuilder setMainClass(String className) {
      this.mainClass = className;
      return this;
    }

    public CommandBuilder appendProgramArgument(String option) {
      programArguments.add(option);
      return this;
    }

    public CommandBuilder appendBootClassPath(String lib) {
      bootClassPaths.add(lib);
      return this;
    }

    private List<String> command() {
      List<String> result = new ArrayList<>();
      // The art script _must_ be run with bash, bots default to /bin/dash for /bin/sh, so
      // explicitly set it;
      if (shouldUseDocker()) {
        result.add("tools/docker/run.sh");
      } else if (isLinux()) {
        result.add("/bin/bash");
      }
      result.add(getExecutable());
      for (String option : options) {
        result.add(option);
      }
      for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
        StringBuilder builder = new StringBuilder("-D");
        builder.append(entry.getKey());
        builder.append("=");
        builder.append(entry.getValue());
        result.add(builder.toString());
      }
      if (!classpaths.isEmpty()) {
        result.add("-cp");
        result.add(String.join(":", classpaths));
      }
      if (!bootClassPaths.isEmpty()) {
        result.add("-Xbootclasspath:" + String.join(":", bootClassPaths));
      }
      if (mainClass != null) {
        result.add(mainClass);
      }
      for (String argument : programArguments) {
        result.add(argument);
      }
      return result;
    }

    public ProcessBuilder asProcessBuilder() {
      return new ProcessBuilder(command());
    }

    public String build() {
      return String.join(" ", command());
    }

    protected abstract boolean shouldUseDocker();

    protected abstract String getExecutable();
  }

  public static class ArtCommandBuilder extends CommandBuilder {

    private DexVm version;

    public ArtCommandBuilder() {
      this.version = getDexVm();
    }

    public ArtCommandBuilder(DexVm version) {
      if (version.getKind() == Kind.HOST) {
        assert ART_BINARY_VERSIONS.containsKey(version);
      }
      this.version = version;
    }

    @Override
    protected boolean shouldUseDocker() {
      return isMac();
    }

    @Override
    protected String getExecutable() {
      return version != null ? getArtBinary(version) : getArtBinary();
    }

    public boolean isForDevice() {
      return version.getKind() == Kind.TARGET;
    }

    public ArtCommandBuilder addToJavaLibraryPath(File file) {
      Assume.assumeTrue("JNI tests are not yet supported on devices", !isForDevice());
      appendArtSystemProperty("java.library.path", file.getAbsolutePath());
      return this;
    }

    public DeviceRunner asDeviceRunner() {
      return new DeviceRunner()
          .setVmOptions(options)
          .setSystemProperties(systemProperties)
          .setClasspath(toFileList(classpaths))
          .setBootClasspath(toFileList(bootClassPaths))
          .setMainClass(mainClass)
          .setProgramArguments(programArguments);
    }
  }

  private static List<File> toFileList(List<String> filePathList) {
    return filePathList.stream().map(path -> new File(path)).collect(Collectors.toList());
  }

  public static class DXCommandBuilder extends CommandBuilder {

    public DXCommandBuilder() {
      appendProgramArgument("--dex");
    }

    @Override
    protected boolean shouldUseDocker() {
      return false;
    }

    @Override
    protected String getExecutable() {
      return DX.toAbsolutePath().toString();
    }
  }

  private static class StreamReader implements Runnable {

    private InputStream stream;
    private String result;

    public StreamReader(InputStream stream) {
      this.stream = stream;
    }

    public String getResult() {
      return result;
    }

    @Override
    public void run() {
      try {
        result = CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));
        stream.close();
      } catch (IOException e) {
        result = "Failed reading result for stream " + stream;
      }
    }
  }

  private static final String TOOLS = "tools";

  private static final Map<DexVm, String> ART_DIRS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "art")
          .put(DexVm.ART_7_0_0_HOST, "art-7.0.0")
          .put(DexVm.ART_6_0_1_HOST, "art-6.0.1")
          .put(DexVm.ART_5_1_1_HOST, "art-5.1.1")
          .put(DexVm.ART_4_4_4_HOST, "dalvik")
          .put(DexVm.ART_4_0_4_HOST, "dalvik-4.0.4").build();
  private static final Map<DexVm, String> ART_BINARY_VERSIONS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "bin/art")
          .put(DexVm.ART_7_0_0_HOST, "bin/art")
          .put(DexVm.ART_6_0_1_HOST, "bin/art")
          .put(DexVm.ART_5_1_1_HOST, "bin/art")
          .put(DexVm.ART_4_4_4_HOST, "bin/dalvik")
          .put(DexVm.ART_4_0_4_HOST, "bin/dalvik").build();

  private static final Map<DexVm, String> ART_BINARY_VERSIONS_X64 =
      ImmutableMap.of(
          DexVm.ART_DEFAULT, "bin/art",
          DexVm.ART_7_0_0_HOST, "bin/art",
          DexVm.ART_6_0_1_HOST, "bin/art");

  private static final List<String> DALVIK_BOOT_LIBS =
      ImmutableList.of(
          "core-libart-hostdex.jar",
          "core-hostdex.jar",
          "apache-xml-hostdex.jar");

  private static final List<String> ART_BOOT_LIBS =
      ImmutableList.of(
          "core-libart-hostdex.jar",
          "core-oj-hostdex.jar",
          "apache-xml-hostdex.jar");

  private static final Map<DexVm, List<String>> BOOT_LIBS;

  static {
    ImmutableMap.Builder<DexVm, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.ART_DEFAULT, ART_BOOT_LIBS)
        .put(DexVm.ART_7_0_0_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_6_0_1_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_5_1_1_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_4_4_4_HOST, DALVIK_BOOT_LIBS)
        .put(DexVm.ART_4_0_4_HOST, DALVIK_BOOT_LIBS);
    BOOT_LIBS = builder.build();
  }

  private static final Path DX = getDxExecutablePath();

  private static Path getDexVmPath(DexVm vm) {
    return Paths.get(
        TOOLS,
        "linux",
        vm.getVersion() == DexVm.Version.DEFAULT
            ? "art"
            : "art-" + vm.getVersion());
  }

  private static Path getDexVmLibPath(DexVm vm) {
    return getDexVmPath(vm).resolve("lib");
  }

  private static Path getDex2OatPath(DexVm vm) {
    return getDexVmPath(vm).resolve("bin").resolve("dex2oat");
  }

  private static Path getAnglerPath(DexVm vm) {
    return getDexVmPath(vm).resolve("product").resolve("angler");
  }

  private static Path getAnglerBootImagePath(DexVm vm) {
    return getAnglerPath(vm).resolve("system").resolve("framework").resolve("boot.art");
  }

  public static byte[] getClassAsBytes(Class clazz) throws IOException {
    String s = clazz.getSimpleName() + ".class";
    Class outer = clazz.getEnclosingClass();
    if (outer != null) {
      s = outer.getSimpleName() + '$' + s;
    }
    return ByteStreams.toByteArray(clazz.getResourceAsStream(s));
  }

  public static String getArtDir(DexVm version) {
    String dir = ART_DIRS.get(version);
    if (dir == null) {
      throw new IllegalStateException("Does not support dex vm: " + version);
    }
    if (isLinux() || isMac()) {
      // The Linux version is used on Mac, where it is run in a Docker container.
      return TOOLS + "/linux/" + dir;
    }
    fail("Unsupported platform, we currently only support mac and linux: " + getPlatform());
    return ""; //never here
  }

  public static String toolsDir() {
    String osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) {
      return "mac";
    } else if (osName.contains("Windows")) {
      return "windows";
    } else {
      return "linux";
    }
  }

  private static String getProguardScript() {
    if (isWindows()) {
      return PROGUARD + ".bat";
    }
    return PROGUARD + ".sh";
  }

  private static String getProguard6Script() {
    if (isWindows()) {
      return PROGUARD6_0_1 + ".bat";
    }
    return PROGUARD6_0_1 + ".sh";
  }

  private static Path getDxExecutablePath() {
    String toolsDir = toolsDir();
    String executableName = toolsDir.equals("windows") ? "dx.bat" : "dx";
    return Paths.get(TOOLS, toolsDir(), "dx", "bin", executableName);
  }

  public static String getArtBinary(DexVm version) {
    String binary = ART_BINARY_VERSIONS.get(version);
    if (binary == null) {
      throw new IllegalStateException("Does not support running with dex vm: " + version);
    }
    return getArtDir(version) + "/" + binary;
  }

  public static Path getDefaultAndroidJar() {
    return getAndroidJar(AndroidApiLevel.getDefault());
  }

  public static Path getAndroidJar(int apiLevel) {
    return getAndroidJar(AndroidApiLevel.getAndroidApiLevel(apiLevel));
  }

  public static Path getAndroidJar(AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.P) {
      // TODO(mikaelpeltier) Android P does not yet have his android.jar use the O version
      apiLevel = AndroidApiLevel.O;
    }
    String jar = String.format(
        ANDROID_JAR_PATTERN,
        (apiLevel == AndroidApiLevel.getDefault() ? DEFAULT_MIN_SDK : apiLevel).getLevel());
    Path path = Paths.get(jar);
    assert Files.exists(path)
        : "Expected android jar to exist for API level " + apiLevel;
    return path;
  }

  public static Path getKotlinRuntimeJar() {
    Path path = Paths.get(KT_RUNTIME);
    assert Files.exists(path) : "Expected kotlin runtime jar";
    return path;
  }

  public static Path getJdwpTestsCfJarPath(AndroidApiLevel minSdk) {
    if (minSdk.getLevel() >= AndroidApiLevel.N.getLevel()) {
      return Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar");
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "libs", "jdwp-tests-preN.jar");
    }
  }

  public static Path getJdwpTestsDexJarPath(AndroidApiLevel minSdk) {
    if (minSdk.getLevel() >= AndroidApiLevel.N.getLevel()) {
      return Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-hostdex.jar");
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "libs", "jdwp-tests-preN-dex.jar");
    }
  }

  /**
   * Get the junit jar bundled with the framework.
   */
  public static Path getFrameworkJunitJarPath(DexVm version) {
    return Paths.get(getArtDir(version), "framework", "junit.jar");
  }

  static class RetainedTemporaryFolder extends TemporaryFolder {

    RetainedTemporaryFolder(java.io.File parentFolder) {
      super(parentFolder);
    }

    protected void after() {
    } // instead of remove, do nothing
  }

  // For non-Linux platforms create the temporary directory in the repository root to simplify
  // running Art in a docker container
  public static TemporaryFolder getTemporaryFolderForTest() {
    String tmpDir = System.getProperty("test_dir");
    if (tmpDir == null) {
      return new TemporaryFolder(ToolHelper.isLinux() ? null : Paths.get("build", "tmp").toFile());
    } else {
      return new RetainedTemporaryFolder(new java.io.File(tmpDir));
    }
  }

  public static String getArtBinary() {
    return getArtBinary(getDexVm());
  }

  public static Set<DexVm> getArtVersions() {
    String artVersion = System.getProperty("dex_vm");
    if (artVersion != null) {
      DexVm artVersionEnum = getDexVm();
      if (artVersionEnum.getKind() == Kind.HOST
          && !ART_BINARY_VERSIONS.containsKey(artVersionEnum)) {
        throw new RuntimeException("Unsupported Art version " + artVersion);
      }
      return ImmutableSet.of(artVersionEnum);
    } else {
      if (isWindows()) {
        return Collections.emptySet();
      } else if (isLinux()) {
        return ART_BINARY_VERSIONS.keySet();
      } else {
        return ART_BINARY_VERSIONS_X64.keySet();
      }
    }
  }

  public static List<String> getBootLibs() {
    String prefix = getArtDir(getDexVm()) + "/";
    List<String> result = new ArrayList<>();
    BOOT_LIBS.get(getDexVm()).stream().forEach(x -> result.add(prefix + "framework/" + x));
    return result;
  }

  // Returns if the passed in vm to use is the default.
  public static boolean isDefaultDexVm(DexVm dexVm) {
    return dexVm == DexVm.ART_DEFAULT;
  }

  public static DexVm getDexVm() {
    String artVersion = System.getProperty("dex_vm");
    if (artVersion == null) {
      return DexVm.ART_DEFAULT;
    } else {
      DexVm artVersionEnum = DexVm.fromShortName(artVersion);
      if (artVersionEnum == null) {
        throw new RuntimeException("Unsupported Art version " + artVersion);
      } else {
        return artVersionEnum;
      }
    }
  }

  public static AndroidApiLevel getMinApiLevelForDexVm() {
    return getMinApiLevelForDexVm(ToolHelper.getDexVm());
  }

  public static AndroidApiLevel getMinApiLevelForDexVm(DexVm dexVm) {
    switch (dexVm.version) {
      case DEFAULT:
        return AndroidApiLevel.O;
      case V7_0_0:
        return AndroidApiLevel.N;
      case V6_0_1:
        return AndroidApiLevel.M;
      case V5_1_1:
        return AndroidApiLevel.L_MR1;
      case V4_4_4:
        return AndroidApiLevel.K;
      case V4_0_4:
        return AndroidApiLevel.I_MR1;
      default:
        throw new Unreachable("Missing min api level for dex vm " + dexVm);
    }
  }

  private static String getPlatform() {
    return System.getProperty("os.name");
  }

  public static boolean isLinux() {
    return getPlatform().startsWith("Linux");
  }

  public static boolean isMac() {
    return getPlatform().startsWith("Mac");
  }

  public static boolean isWindows() {
    return getPlatform().startsWith("Windows");
  }

  public static boolean isJava9Runtime() {
    return System.getProperty("java.specification.version").equals("9");
  }

  public static boolean artSupported() {
    if (!isLinux() && !isMac() && !isWindows()) {
      System.err.println("Testing on your platform is not fully supported. " +
          "Art does not work on on your platform.");
      return false;
    }
    if (isWindows() && getDexVm().getKind() == Kind.HOST) {
      System.err.println("Testing on host is not supported on Windows.");
      return false;
    }
    return true;
  }

  public static Path getClassPathForTests() {
    return Paths.get(BUILD_DIR, "classes", "test");
  }

  private static List<String> getNamePartsForTestPackage(Package pkg) {
    return Lists.newArrayList(pkg.getName().split("\\."));
  }

  public static Path getPackageDirectoryForTestPackage(Package pkg) {
    List<String> parts = getNamePartsForTestPackage(pkg);
    return getClassPathForTests().resolve(
        Paths.get("", parts.toArray(new String[parts.size() - 1])));
  }

  public static String getJarEntryForTestPackage(Package pkg) {
    List<String> parts = getNamePartsForTestPackage(pkg);
    return String.join("/", parts);
  }

  private static List<String> getNamePartsForTestClass(Class clazz) {
    List<String> parts = Lists.newArrayList(clazz.getCanonicalName().split("\\."));
    Class enclosing = clazz;
    while (enclosing.getEnclosingClass() != null) {
      parts.set(parts.size() - 2, parts.get(parts.size() - 2) + "$" + parts.get(parts.size() - 1));
      parts.remove(parts.size() - 1);
      enclosing = clazz.getEnclosingClass();
    }
    parts.set(parts.size() - 1, parts.get(parts.size() - 1) + ".class");
    return parts;
  }

  public static Path getPackageDirectoryForTestClass(Class clazz) {
    return getPackageDirectoryForTestPackage(clazz.getPackage());
  }

  public static List<Path> getClassFilesForTestPackage(Package pkg) throws IOException {
    Path dir = ToolHelper.getPackageDirectoryForTestPackage(pkg);
    return Files.walk(dir)
        .filter(path -> path.toString().endsWith(".class"))
        .collect(Collectors.toList());
  }

  public static Path getClassFileForTestClass(Class clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return getClassPathForTests().resolve(
        Paths.get("", parts.toArray(new String[parts.size() - 1])));
  }

  public static String getJarEntryForTestClass(Class clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return String.join("/", parts);
  }

  public static DexApplication buildApplication(List<String> fileNames)
      throws IOException, ExecutionException {
    return buildApplicationWithAndroidJar(fileNames, getDefaultAndroidJar());
  }

  public static DexApplication buildApplicationWithAndroidJar(
      List<String> fileNames, Path androidJar)
      throws IOException, ExecutionException {
    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(ListUtils.map(fileNames, Paths::get))
            .addLibraryFiles(androidJar)
            .build();
    return new ApplicationReader(
        input, new InternalOptions(), new Timing("ToolHelper buildApplication"))
        .read().toDirect();
  }

  public static ProguardConfiguration loadProguardConfiguration(
      DexItemFactory factory, List<Path> configPaths)
      throws IOException, ProguardRuleParserException {
    Reporter reporter = new Reporter(new DefaultDiagnosticsHandler());
    if (configPaths.isEmpty()) {
      return ProguardConfiguration.defaultConfiguration(factory, reporter);
    }
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(factory, reporter);
    for (Path configPath : configPaths) {
      parser.parse(configPath);
    }
    return parser.getConfig();
  }

  public static D8Command.Builder prepareD8CommandBuilder(AndroidApp app) {
    return D8Command.builder(app);
  }

  public static R8Command.Builder prepareR8CommandBuilder(AndroidApp app) {
    return R8Command.builder(app).setProgramConsumer(DexIndexedConsumer.emptyConsumer());
  }

  public static AndroidApp runR8(AndroidApp app) throws IOException {
    try {
      return runR8(prepareR8CommandBuilder(app).build());
    } catch (CompilationFailedException e) {
      throw new RuntimeException(e);
    }
  }

  public static AndroidApp runR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException {
    try {
      return runR8(prepareR8CommandBuilder(app).build(), optionsConsumer);
    } catch (CompilationFailedException e) {
      throw new RuntimeException(e);
    }
  }

  public static AndroidApp runR8(R8Command command) throws IOException {
    return runR8(command, null);
  }

  public static AndroidApp runR8(R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws IOException {
    return runR8WithFullResult(command, optionsConsumer);
  }

  public static AndroidApp runR8WithFullResult(
      R8Command command, Consumer<InternalOptions> optionsConsumer) throws IOException {
    // TODO(zerny): Should we really be adding the android library in ToolHelper?
    AndroidApp app = command.getInputApp();
    if (app.getLibraryResourceProviders().isEmpty()) {
      // Add the android library matching the minsdk. We filter out junit and testing classes
      // from the android jar to avoid duplicate classes in art and jctf tests.
      AndroidApp.Builder builder = AndroidApp.builder(app);
      addFilteredAndroidJar(builder, AndroidApiLevel.getAndroidApiLevel(command.getMinApiLevel()));
      app = builder.build();
    }
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    AndroidAppConsumers compatSink = new AndroidAppConsumers(options);
    R8.runForTesting(app, options);
    return compatSink.build();
  }

  public static void addFilteredAndroidJar(BaseCommand.Builder builder, AndroidApiLevel apiLevel)
      throws IOException {
    addFilteredAndroidJar(getAppBuilder(builder), apiLevel);
  }

  public static void addFilteredAndroidJar(AndroidApp.Builder builder, AndroidApiLevel apiLevel)
      throws IOException {
    builder.addFilteredLibraryArchives(Collections.singletonList(
        new FilteredClassPath(getAndroidJar(apiLevel),
            ImmutableList.of("!junit/**", "!android/test/**"))));
  }

  public static AndroidApp runD8(AndroidApp app) throws IOException {
    return runD8(app, null);
  }

  public static AndroidApp runD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException {
    try {
      return runD8(D8Command.builder(app), optionsConsumer);
    } catch (CompilationFailedException e) {
      throw new RuntimeException(e);
    }
  }

  public static AndroidApp runD8(D8Command.Builder builder)
      throws IOException, CompilationFailedException {
    return runD8(builder, null);
  }

  public static AndroidApp runD8(
      D8Command.Builder builder, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    AndroidAppConsumers compatSink = new AndroidAppConsumers(builder);
    D8Command command = builder.build();
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    D8.runForTesting(command.getInputApp(), options);
    return compatSink.build();
  }

  public static AndroidApp runDexer(String fileName, String outDir, String... extraArgs)
      throws IOException {
    List<String> args = new ArrayList<>();
    Collections.addAll(args, extraArgs);
    Collections.addAll(args, "--output=" + outDir + "/classes.dex", fileName);
    int result = runDX(args.toArray(new String[args.size()])).exitCode;
    return result != 0 ? null : builderFromProgramDirectory(Paths.get(outDir)).build();
  }

  public static ProcessResult runDX(String... args) throws IOException {
    return runDX(null, args);
  }

  public static ProcessResult runDX(Path workingDirectory, String... args) throws IOException {
    Assume.assumeTrue(ToolHelper.artSupported());
    DXCommandBuilder builder = new DXCommandBuilder();
    for (String arg : args) {
      builder.appendProgramArgument(arg);
    }
    ProcessBuilder pb = builder.asProcessBuilder();
    if (workingDirectory != null) {
      pb.directory(workingDirectory.toFile());
    }
    return runProcess(pb);
  }

  public static ProcessResult runJava(Class clazz) throws Exception {
    String main = clazz.getCanonicalName();
    Path path = getClassPathForTests();
    return runJava(path, main);
  }

  public static ProcessResult runJavaNoVerify(Class clazz) throws Exception {
    String main = clazz.getCanonicalName();
    Path path = getClassPathForTests();
    return runJavaNoVerify(path, main);
  }

  public static ProcessResult runJava(Path classpath, String... args) throws IOException {
    return runJava(ImmutableList.of(classpath), args);
  }

  public static ProcessResult runJava(List<Path> classpath, String... args) throws IOException {
    String cp = classpath.stream().map(Path::toString).collect(Collectors.joining(PATH_SEPARATOR));
    List<String> cmdline = new ArrayList<String>(Arrays.asList(getJavaExecutable(), "-cp", cp));
    cmdline.addAll(Arrays.asList(args));
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return runProcess(builder);
  }

  public static ProcessResult runJavaNoVerify(Path classpath, String mainClass)
      throws IOException {
    return runJavaNoVerify(ImmutableList.of(classpath), mainClass);
  }

  public static ProcessResult runJavaNoVerify(List<Path> classpath, String mainClass)
      throws IOException {
    String cp = classpath.stream().map(Path::toString).collect(Collectors.joining(PATH_SEPARATOR));
    ProcessBuilder builder = new ProcessBuilder(
        getJavaExecutable(), "-cp", cp, "-noverify", mainClass);
    return runProcess(builder);
  }

  public static ProcessResult forkD8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, D8.class, args);
  }

  public static ProcessResult forkR8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, R8.class, args);
  }

  public static ProcessResult forkGenerateMainDexList(Path dir, List<String> args1, String... args2)
      throws IOException, InterruptedException {
    List<String> args = new ArrayList<>();
    args.addAll(args1);
    args.addAll(Arrays.asList(args2));
    return forkJava(dir, GenerateMainDexList.class, args);
  }

  public static ProcessResult forkGenerateMainDexList(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, GenerateMainDexList.class, args);
  }

  private static ProcessResult forkJava(Path dir, Class clazz, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, clazz, Arrays.asList(args));
  }

  private static ProcessResult forkJava(Path dir, Class clazz, List<String> args)
      throws IOException, InterruptedException {
    List<String> command = new ImmutableList.Builder<String>()
        .add(getJavaExecutable())
        .add("-cp").add(System.getProperty("java.class.path"))
        .add(clazz.getCanonicalName())
        .addAll(args)
        .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }

  public static String getJavaExecutable() {
    return Paths.get(System.getProperty("java.home"), "bin", "java").toString();
  }

  public static ProcessResult runArtRaw(ArtCommandBuilder builder) throws IOException {
    return runArtProcessRaw(builder);
  }

  public static ProcessResult runArtRaw(String file, String mainClass)
      throws IOException {
    return runArtRaw(Collections.singletonList(file), mainClass, null);
  }

  public static ProcessResult runArtRaw(
      String file, String mainClass, Consumer<ArtCommandBuilder> extras) throws IOException {
    return runArtRaw(Collections.singletonList(file), mainClass, extras);
  }

  public static ProcessResult runArtRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtRaw(files, mainClass, extras, null);
  }

  public static ProcessResult runArtRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras, DexVm version)
      throws IOException {
    ArtCommandBuilder builder =
        version != null ? new ArtCommandBuilder(version) : new ArtCommandBuilder();
    files.forEach(builder::appendClasspath);
    builder.setMainClass(mainClass);
    if (extras != null) {
      extras.accept(builder);
    }
    return runArtProcessRaw(builder);
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(String file, String mainClass)
      throws IOException {
    return runArtNoVerificationErrorsRaw(Collections.singletonList(file), mainClass, null);
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtNoVerificationErrorsRaw(files, mainClass, extras, null);
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    ProcessResult result = runArtRaw(files, mainClass, extras, version);
    failOnProcessFailure(result);
    failOnVerificationErrors(result);
    return result;
  }

  public static String runArtNoVerificationErrors(String file, String mainClass)
      throws IOException {
    return runArtNoVerificationErrorsRaw(file, mainClass).stdout;
  }

  public static String runArtNoVerificationErrors(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtNoVerificationErrors(files, mainClass, extras, null);
  }

  public static String runArtNoVerificationErrors(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    return runArtNoVerificationErrorsRaw(files, mainClass, extras, version).stdout;
  }

  private static void failOnProcessFailure(ProcessResult result) {
    if (result.exitCode != 0) {
      fail("Unexpected art failure: '" + result.stderr + "'\n" + result.stdout);
    }
  }

  private static void failOnVerificationErrors(ProcessResult result) {
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  private static ProcessResult runArtProcessRaw(ArtCommandBuilder builder) throws IOException {
    Assume.assumeTrue(ToolHelper.artSupported());
    ProcessResult result;
    if (builder.isForDevice()) {
      try {
        result = builder.asDeviceRunner().run();
      } catch (DeviceRunnerConfigurationException e) {
        throw new RuntimeException(e);
      }
    } else {
      result = runProcess(builder.asProcessBuilder());
    }
    return result;
  }

  public static String runArt(ArtCommandBuilder builder) throws IOException {
    ProcessResult result = runArtProcessRaw(builder);
    failOnProcessFailure(result);
    return result.stdout;
  }

  public static String checkArtOutputIdentical(String file1, String file2, String mainClass,
      DexVm version)
      throws IOException {
    return checkArtOutputIdentical(Collections.singletonList(file1),
        Collections.singletonList(file2), mainClass, null, version);
  }

  public static String checkArtOutputIdentical(List<String> files1, List<String> files2,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    return checkArtOutputIdentical(
        version,
        mainClass,
        extras,
        ImmutableList.of(ListUtils.map(files1, Paths::get), ListUtils.map(files2, Paths::get)));
  }

  public static String checkArtOutputIdentical(
      DexVm version,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      Collection<Collection<Path>> programs)
      throws IOException {
    for (Collection<Path> program : programs) {
      for (Path path : program) {
        assertTrue("File " + path + " must exist", Files.exists(path));
      }
    }
    String output = null;
    for (Collection<Path> program : programs) {
      String result =
          ToolHelper.runArtNoVerificationErrors(
              ListUtils.map(program, Path::toString), mainClass, extras, version);
      if (output != null) {
        assertEquals(output, result);
      } else {
        output = result;
      }
    }
    return output;
  }

  public static void runDex2Oat(Path file, Path outFile) throws IOException {
    DexVm vm = getDexVm();
    if (vm.isOlderThanOrEqual(DexVm.ART_5_1_1_HOST)) {
      // TODO(b/79191363): Support running dex2oat for past android versions.
      // Run default dex2oat for tests on old runtimes.
      vm = DexVm.ART_DEFAULT;
    }
    runDex2Oat(file, outFile, vm);
  }

  public static void runDex2Oat(Path file, Path outFile, DexVm vm) throws IOException {
    Assume.assumeTrue(ToolHelper.artSupported());
    // TODO(jmhenaff): find a way to run this on windows (push dex and run on device/emulator?)
    Assume.assumeTrue(!ToolHelper.isWindows());
    assert Files.exists(file);
    assert ByteStreams.toByteArray(Files.newInputStream(file)).length > 0;
    List<String> command = new ArrayList<>();
    command.add(getDex2OatPath(vm).toString());
    command.add("--android-root=" + getAnglerPath(vm));
    command.add("--runtime-arg");
    command.add("-Xnorelocate");
    command.add("--boot-image=" + getAnglerBootImagePath(vm));
    command.add("--dex-file=" + file.toAbsolutePath());
    command.add("--oat-file=" + outFile.toAbsolutePath());
    command.add("--instruction-set=arm64");
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("LD_LIBRARY_PATH", getDexVmLibPath(vm).toString());
    ProcessResult result = runProcess(builder);
    if (result.exitCode != 0) {
      fail("dex2oat failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  public static ProcessResult runProguardRaw(
      String proguardScript, Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    return runProguardRaw(
        proguardScript, inJar, outJar, ToolHelper.getDefaultAndroidJar(), configs, map);
  }

  public static ProcessResult runProguardRaw(
      String proguardScript, Path inJar, Path outJar, Path lib, List<Path> configs, Path map)
      throws IOException {
    List<String> command = new ArrayList<>();
    command.add(proguardScript);
    command.add("-forceprocessing");  // Proguard just checks the creation time on the in/out jars.
    command.add("-injars");
    command.add(inJar.toString());
    command.add("-libraryjars");
    command.add(lib.toString());
    configs.forEach(config -> command.add("@" + config));
    command.add("-outjar");
    command.add(outJar.toString());
    command.add("-printmapping");
    if (map != null) {
      command.add(map.toString());
    }
    ProcessBuilder builder = new ProcessBuilder(command);
    return ToolHelper.runProcess(builder);
  }

  public static String runProguard(
      String proguardScript, Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    ToolHelper.ProcessResult result = runProguardRaw(proguardScript, inJar, outJar, configs, map);
    if (result.exitCode != 0) {
      fail("Proguard failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return result.stdout;
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguardRaw(getProguardScript(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguardRaw(getProguardScript(), inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguard(inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguard(getProguardScript(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguard6Raw(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, ImmutableList.of(config), map);
  }

  public static ProcessResult runProguard6Raw(
      Path inJar, Path outJar, List<Path> config, Path map) throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguard6Raw(
      Path inJar, Path outJar, Path lib, Path config, Path map) throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, lib, ImmutableList.of(config), map);
  }

  public static String runProguard6(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguard6(inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard6(Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    return runProguard(getProguard6Script(), inJar, outJar, configs, map);
  }

  public static class ProcessResult {

    public final int exitCode;
    public final String stdout;
    public final String stderr;

    ProcessResult(int exitCode, String stdout, String stderr) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("EXIT CODE: ");
      builder.append(exitCode);
      builder.append("\n");
      builder.append("STDOUT: ");
      builder.append("\n");
      builder.append(stdout);
      builder.append("\n");
      builder.append("STDERR: ");
      builder.append("\n");
      builder.append(stderr);
      builder.append("\n");
      return builder.toString();
    }
  }

  // Process.pid() is added in Java 9. Until we use Java 9 this can be used on Linux and Mac OS.
  // https://docs.oracle.com/javase/9/docs/api/java/lang/Process.html#pid--
  private static synchronized long getPidOfProcess(Process p) {
    long pid = -1;
    try {
      if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
        Field f = p.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        pid = f.getLong(p);
        f.setAccessible(false);
      }
    } catch (Exception e) {
      pid = -1;
    }
    return pid;
  }

  public static ProcessResult runProcess(ProcessBuilder builder) throws IOException {
    System.out.println(String.join(" ", builder.command()));
    Process p = builder.start();
    // Drain stdout and stderr so that the process does not block. Read stdout and stderr
    // in parallel to make sure that neither buffer can get filled up which will cause the
    // C program to block in a call to write.
    StreamReader stdoutReader = new StreamReader(p.getInputStream());
    StreamReader stderrReader = new StreamReader(p.getErrorStream());
    Thread stdoutThread = new Thread(stdoutReader);
    Thread stderrThread = new Thread(stderrReader);
    stdoutThread.start();
    stderrThread.start();
    try {
      p.waitFor();
      stdoutThread.join();
      stderrThread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Execution interrupted", e);
    }
    return new ProcessResult(p.exitValue(), stdoutReader.getResult(), stderrReader.getResult());
  }

  public static R8Command.Builder addProguardConfigurationConsumer(
      R8Command.Builder builder, Consumer<ProguardConfiguration.Builder> consumer) {
    builder.addProguardConfigurationConsumerForTesting(consumer);
    return builder;
  }

  public static R8Command.Builder allowPartiallyImplementedProguardOptions(
      R8Command.Builder builder) {
    builder.allowPartiallyImplementedProguardOptions();
    return builder;
  }

  public static AndroidApp getApp(BaseCommand command) {
    return command.getInputApp();
  }

  public static AndroidApp.Builder getAppBuilder(BaseCommand.Builder builder) {
    return builder.getAppBuilder();
  }

  public static AndroidApp.Builder builderFromProgramDirectory(Path directory) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (isDexFile(file)) {
              builder.addProgramFile(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return builder;
  }

  public static void writeApplication(DexApplication application, InternalOptions options)
      throws ExecutionException {
    R8.writeApplication(
        Executors.newSingleThreadExecutor(),
        application,
        null,
        NamingLens.getIdentityLens(),
        null,
        options,
        null);
  }

  public enum KotlinTargetVersion {
    JAVA_6("JAVA_6"),
    JAVA_8("JAVA_8");

    private final String folderName;

    KotlinTargetVersion(String folderName) {
      this.folderName = folderName;
    }

    public String getFolderName() {
      return folderName;
    }
  }
}
