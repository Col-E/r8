// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DeviceRunner.DeviceRunnerConfigurationException;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import joptsimple.internal.Strings;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class ToolHelper {

  public static final String BUILD_DIR = "build/";
  public static final String EXAMPLES_DIR = "src/test/examples/";
  public static final String EXAMPLES_ANDROID_O_DIR = "src/test/examplesAndroidO/";
  public static final String EXAMPLES_BUILD_DIR = BUILD_DIR + "test/examples/";
  public static final String EXAMPLES_ANDROID_N_BUILD_DIR = BUILD_DIR + "test/examplesAndroidN/";
  public static final String EXAMPLES_ANDROID_O_BUILD_DIR = BUILD_DIR + "test/examplesAndroidO/";
  public static final String SMALI_BUILD_DIR = BUILD_DIR + "test/smali/";

  public static final String LINE_SEPARATOR = StringUtils.LINE_SEPARATOR;
  public final static String PATH_SEPARATOR = File.pathSeparator;

  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";
  private static final int DEFAULT_MIN_SDK = AndroidApiLevel.I.getLevel();

  public enum DexVm {
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
        new ImmutableMap.Builder<String, DexVm>()
            .putAll(
                Arrays.stream(DexVm.values()).collect(
                    Collectors.toMap(a -> a.toString(), a -> a)))
            .build();

    public enum Version {
      V4_4_4("4.4.4"),
      V5_1_1("5.1.1"),
      V6_0_1("6.0.1"),
      V7_0_0("7.0.0"),
      DEFAULT("default");

      Version (String shortName) { this.shortName = shortName; }

      public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
      }

      public boolean isOlderThanOrEqual(Version other) {
        return compareTo(other) <= 0;
      }

      public String toString() {
        return shortName;
      }

      private String shortName;
    }

    public enum Kind {
      HOST("host"),
      TARGET("target");

      Kind (String shortName) { this.shortName = shortName; }

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

    public Version getVersion() { return version; }
    public Kind getKind() { return kind; }

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
      } else if (isLinux())  {
        result.add("/bin/bash");
      } else {
        assert isWindows();
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
        result.add(Strings.join(classpaths, ":"));
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
      return DX;
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
              .put(DexVm.ART_4_4_4_HOST, "dalvik").build();
  private static final Map<DexVm, String> ART_BINARY_VERSIONS =
      ImmutableMap.<DexVm, String>builder()
              .put(DexVm.ART_DEFAULT, "bin/art")
              .put(DexVm.ART_7_0_0_HOST, "bin/art")
              .put(DexVm.ART_6_0_1_HOST, "bin/art")
              .put(DexVm.ART_5_1_1_HOST, "bin/art")
              .put(DexVm.ART_4_4_4_HOST, "bin/dalvik").build();

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

  private static final Map<DexVm, List<String>> BOOT_LIBS =
      ImmutableMap.of(
          DexVm.ART_DEFAULT, ART_BOOT_LIBS,
          DexVm.ART_7_0_0_HOST, ART_BOOT_LIBS,
          DexVm.ART_6_0_1_HOST, ART_BOOT_LIBS,
          DexVm.ART_5_1_1_HOST, ART_BOOT_LIBS,
          DexVm.ART_4_4_4_HOST, DALVIK_BOOT_LIBS);

  private static final String LIB_PATH = TOOLS + "/linux/art/lib";
  private static final String DX = getDxExecutablePath();
  private static final String DEX2OAT = TOOLS + "/linux/art/bin/dex2oat";
  private static final String ANGLER_DIR = TOOLS + "/linux/art/product/angler";
  private static final String ANGLER_BOOT_IMAGE = ANGLER_DIR + "/system/framework/boot.art";

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

  private static String getDxExecutablePath() {
    String toolsDir = toolsDir();
    String executableName = toolsDir.equals("windows") ? "dx.bat" : "dx";
    return TOOLS + "/" + toolsDir() + "/dx/bin/" + executableName;
  }

  public static String getArtBinary(DexVm version) {
    String binary = ART_BINARY_VERSIONS.get(version);
    if (binary == null) {
      throw new IllegalStateException("Does not support running with dex vm: " + version);
    }
    return getArtDir(version) + "/" + binary;
  }

  public static String getDefaultAndroidJar() {
    return getAndroidJar(AndroidApiLevel.getDefault().getLevel());
  }

  public static String getAndroidJar(int minSdkVersion) {
    return String.format(
        ANDROID_JAR_PATTERN,
        minSdkVersion == AndroidApiLevel.getDefault().getLevel() ? DEFAULT_MIN_SDK : minSdkVersion);
  }

  public static Path getJdwpTestsJarPath(int minSdk) {
    if (minSdk >= AndroidApiLevel.N.getLevel()) {
      return Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar");
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "libs", "jdwp-tests-preN.jar");
    }
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
        throw new RuntimeException("You need to specify a runtime with 'dex_vm' property");
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
      if (isWindows()) {
        throw new RuntimeException(
            "Default Art version is not supported on Windows. Please specify a non-host runtime "
                + "with property 'dex_vm'");
      }
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

  public static int getMinApiLevelForDexVm(DexVm dexVm) {
    switch (dexVm.version) {
      case DEFAULT:
        return AndroidApiLevel.O.getLevel();
      case V7_0_0:
        return AndroidApiLevel.N.getLevel();
      default:
        return AndroidApiLevel.getDefault().getLevel();
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

  public static boolean artSupported() {
    if (!isLinux() && !isMac()  && !isWindows()) {
      System.err.println("Testing on your platform is not fully supported. " +
          "Art does not work on on your platform.");
      return false;
    }
    return true;
  }

  public static Path getClassPathForTests() {
    return Paths.get(BUILD_DIR, "classes", "test");
  }

  public static Path getClassFileForTestClass(Class clazz) {
    List<String> parts = Lists.newArrayList(clazz.getCanonicalName().split("\\."));
    Class enclosing = clazz;
    while (enclosing.getEnclosingClass() != null) {
      parts.set(parts.size() - 2, parts.get(parts.size() - 2) + "$" + parts.get(parts.size() - 1));
      parts.remove(parts.size() - 1);
      enclosing = clazz.getEnclosingClass();
    }
    parts.set(parts.size() - 1, parts.get(parts.size() - 1) + ".class");
    return getClassPathForTests().resolve(
        Paths.get("", parts.toArray(new String[parts.size() - 1])));
  }

  public static DexApplication buildApplication(List<String> fileNames)
      throws IOException, ExecutionException {
    return buildApplicationWithAndroidJar(fileNames, getDefaultAndroidJar());
  }

  public static DexApplication buildApplicationWithAndroidJar(
      List<String> fileNames, String androidJar)
      throws IOException, ExecutionException {
    AndroidApp input = AndroidApp.builder()
        .addProgramFiles(ListUtils.map(fileNames, FilteredClassPath::unfiltered))
        .addLibraryFiles(FilteredClassPath.unfiltered(androidJar))
        .build();
    return new ApplicationReader(
        input, new InternalOptions(), new Timing("ToolHelper buildApplication"))
        .read().toDirect();
  }

  public static ProguardConfiguration loadProguardConfiguration(
      DexItemFactory factory, List<Path> configPaths)
      throws IOException, ProguardRuleParserException {
    if (configPaths.isEmpty()) {
      return ProguardConfiguration.defaultConfiguration(factory);
    }
    ProguardConfigurationParser parser = new ProguardConfigurationParser(factory);
    for (Path configPath : configPaths) {
      parser.parse(configPath);
    }
    return parser.getConfig();
  }

  public static D8Command.Builder prepareD8CommandBuilder(AndroidApp app) {
    return D8Command.builder(app);
  }

  public static R8Command.Builder prepareR8CommandBuilder(AndroidApp app) {
    return R8Command.builder(app);
  }

  public static AndroidApp runR8(AndroidApp app)
      throws ExecutionException, IOException, ProguardRuleParserException, CompilationException {
    return runR8(R8Command.builder(app).build());
  }

  public static AndroidApp runR8(AndroidApp app, Path output)
      throws ExecutionException, IOException, ProguardRuleParserException, CompilationException {
    assert output != null;
    return runR8(R8Command.builder(app).setOutputPath(output).build());
  }

  public static AndroidApp runR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws ProguardRuleParserException, ExecutionException, IOException, CompilationException {
    return runR8(R8Command.builder(app).build(), optionsConsumer);
  }

  public static AndroidApp runR8(R8Command command)
      throws ProguardRuleParserException, ExecutionException, IOException, CompilationException {
    return runR8(command, null);
  }

  public static AndroidApp runR8(R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws ProguardRuleParserException, ExecutionException, IOException, CompilationException {
    return runR8WithFullResult(command, optionsConsumer).androidApp;
  }

  public static CompilationResult runR8WithFullResult(
      R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws ProguardRuleParserException, ExecutionException, IOException, CompilationException {
    // TODO(zerny): Should we really be adding the android library in ToolHelper?
    AndroidApp app = command.getInputApp();
    if (app.getLibraryResourceProviders().isEmpty()) {
      app =
          AndroidApp.builder(app)
              .addLibraryFiles(
                  FilteredClassPath.unfiltered(getAndroidJar(command.getMinApiLevel())))
              .build();
    }
    InternalOptions options = command.getInternalOptions();
    // TODO(zerny): Should we really be setting this in ToolHelper?
    options.ignoreMissingClasses = true;
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    CompilationResult result = R8.runForTesting(app, options);
    R8.writeOutputs(command, options, result.androidApp);
    return result;
  }

  public static AndroidApp runR8(String fileName, String out)
      throws IOException, ProguardRuleParserException, ExecutionException, CompilationException {
    return runR8(Collections.singletonList(fileName), out);
  }

  public static AndroidApp runR8(Collection<String> fileNames, String out)
      throws IOException, ProguardRuleParserException, ExecutionException, CompilationException {
    return R8.run(
        R8Command.builder()
            .addProgramFiles(ListUtils.map(fileNames, Paths::get))
            .setOutputPath(Paths.get(out))
            .setIgnoreMissingClasses(true)
            .build());
  }

  public static DexApplication optimizeWithR8(
      DexApplication application,
      InternalOptions options)
      throws CompilationException, ExecutionException, IOException {
    application = application.toDirect();
    return R8.optimize(application, new AppInfoWithSubtyping(application), options);
  }

  public static AndroidApp runD8(AndroidApp app) throws CompilationException, IOException {
    return runD8(app, null);
  }

  public static AndroidApp runD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, IOException {
    return runD8(D8Command.builder(app).build(), optionsConsumer);
  }

  public static AndroidApp runD8(D8Command command) throws IOException, CompilationException {
    return runD8(command, null);
  }

  public static AndroidApp runD8(D8Command command, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationException {
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    AndroidApp result = D8.runForTesting(command.getInputApp(), options).androidApp;
    if (command.getOutputPath() != null) {
      result.write(command.getOutputPath(), command.getOutputMode());
    }
    return result;
  }

  public static AndroidApp runDexer(String fileName, String outDir, String... extraArgs)
      throws IOException {
    List<String> args = new ArrayList<>();
    Collections.addAll(args, extraArgs);
    Collections.addAll(args, "--output=" + outDir + "/classes.dex", fileName);
    int result = runDX(args.toArray(new String[args.size()])).exitCode;
    return result != 0 ? null : AndroidApp.fromProgramDirectory(Paths.get(outDir));
  }

  public static ProcessResult runDX(String[] args) throws IOException {
    Assume.assumeTrue(ToolHelper.artSupported());
    DXCommandBuilder builder = new DXCommandBuilder();
    for (String arg : args) {
      builder.appendProgramArgument(arg);
    }
    return runProcess(builder.asProcessBuilder());
  }

  public static ProcessResult runJava(Class clazz) throws Exception {
    String main = clazz.getCanonicalName();
    Path path = getClassPathForTests();
    return runJava(ImmutableList.of(path.toString()), main);
  }

  public static ProcessResult runJava(List<String> classpath, String mainClass) throws IOException {
    ProcessBuilder builder = new ProcessBuilder(
        getJavaExecutable(), "-cp", String.join(PATH_SEPARATOR, classpath), mainClass);
    return runProcess(builder);
  }

  public static ProcessResult forkD8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, D8.class, args);
  }

  public static ProcessResult forkR8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, R8.class, ImmutableList.builder()
        .addAll(Arrays.asList(args))
        .add("--ignore-missing-classes")
        .build()
        .toArray(new String[0]));
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

  public static String runArtNoVerificationErrors(String file, String mainClass)
      throws IOException {
    return runArtNoVerificationErrors(Collections.singletonList(file), mainClass, null);
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
    ArtCommandBuilder builder =
        version != null ? new ArtCommandBuilder(version) : new ArtCommandBuilder();
    files.forEach(builder::appendClasspath);
    builder.setMainClass(mainClass);
    if (extras != null) {
      extras.accept(builder);
    }
    return runArtNoVerificationErrors(builder);
  }

  public static String runArtNoVerificationErrors(ArtCommandBuilder builder) throws IOException {
    ProcessResult result = runArtProcess(builder);
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
    return result.stdout;
  }

  private static ProcessResult runArtProcess(ArtCommandBuilder builder) throws IOException {
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
    if (result.exitCode != 0) {
      fail("Unexpected art failure: '" + result.stderr + "'\n" + result.stdout);
    }
    return result;
  }

  public static String runArt(ArtCommandBuilder builder) throws IOException {
    ProcessResult result = runArtProcess(builder);
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
    Assume.assumeTrue(ToolHelper.artSupported());
    // TODO(jmhenaff): find a way to run this on windows (push dex and run on device/emulator?)
    Assume.assumeTrue(!ToolHelper.isWindows());
    assert Files.exists(file);
    assert ByteStreams.toByteArray(Files.newInputStream(file)).length > 0;
    List<String> command = new ArrayList<>();
    command.add(DEX2OAT);
    command.add("--android-root=" + ANGLER_DIR);
    command.add("--runtime-arg");
    command.add("-Xnorelocate");
    command.add("--boot-image=" + ANGLER_BOOT_IMAGE);
    command.add("--dex-file=" + file.toAbsolutePath());
    command.add("--oat-file=" + outFile.toAbsolutePath());
    command.add("--instruction-set=arm64");
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("LD_LIBRARY_PATH", LIB_PATH);
    ProcessResult result = runProcess(builder);
    if (result.exitCode != 0) {
      fail("dex2oat failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
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

  public static AndroidApp getApp(BaseCommand command) {
    return command.getInputApp();
  }
}
