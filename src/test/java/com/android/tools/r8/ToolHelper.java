// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.ToolHelper.TestDataSourceSet.computeLegacyOrGradleSpecifiedLocation;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAVA_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.isDexFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DeviceRunner.DeviceRunnerConfigurationException;
import com.android.tools.r8.ResourceShrinker.ReferenceChecker;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.benchmarks.BenchmarkResults;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.AssemblyWriter;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class ToolHelper {

  public static boolean isNewGradleSetup() {
    return "true".equals(System.getProperty("USE_NEW_GRADLE_SETUP"));
  }

  public static String getProjectRoot() {
    String current = System.getProperty("user.dir");
    if (!current.contains("d8_r8")) {
      return "";
    }
    while (current.contains("d8_r8")) {
      current = Paths.get(current).getParent().toString();
    }
    return current + "/";
  }

  public enum TestDataSourceSet {
    LEGACY(null),
    TESTS_JAVA_8("tests_java_8/build/classes/java/test"),
    TESTS_BOOTSTRAP("tests_bootstrap/build/classes/java/test"),
    SPECIFIED_BY_GRADLE_PROPERTY(null);

    private final String destination;

    TestDataSourceSet(String destination) {
      this.destination = destination;
    }

    public boolean isLegacy() {
      return this == LEGACY;
    }

    public boolean isSpecifiedByGradleProperty() {
      return this == SPECIFIED_BY_GRADLE_PROPERTY;
    }

    public Path getBuildDir() {
      if (isLegacy()) {
        return Paths.get(BUILD_DIR, "classes", "java", "test");
      } else if (isSpecifiedByGradleProperty()) {
        assert System.getProperty("TEST_DATA_LOCATION") != null;
        return Paths.get(System.getProperty("TEST_DATA_LOCATION"));
      } else {
        return Paths.get(getProjectRoot(), "d8_r8", "test_modules", destination);
      }
    }

    public static TestDataSourceSet computeLegacyOrGradleSpecifiedLocation() {
      return isNewGradleSetup()
          ? TestDataSourceSet.SPECIFIED_BY_GRADLE_PROPERTY
          : TestDataSourceSet.LEGACY;
    }
  }

  public static final String SOURCE_DIR = getProjectRoot() + "src/";
  public static final String MAIN_SOURCE_DIR = getProjectRoot() + "src/main/java/";
  public static final String LIBRARY_DESUGAR_SOURCE_DIR = getProjectRoot() + "src/library_desugar/";
  public static final String BUILD_DIR = getProjectRoot() + "build/";
  public static final String LIBS_DIR = BUILD_DIR + "libs/";
  public static final String THIRD_PARTY_DIR = getProjectRoot() + "third_party/";
  public static final String DEPENDENCIES = THIRD_PARTY_DIR + "dependencies/";
  public static final String TOOLS_DIR = getProjectRoot() + "tools/";
  public static final String TESTS_DIR = getProjectRoot() + "src/test/";
  public static final String ART_TESTS_ROOT = getProjectRoot() + "tests/";
  public static final String TESTS_SOURCE_DIR = TESTS_DIR + "java/";
  public static final String EXAMPLES_DIR = TESTS_DIR + "examples/";
  public static final String EXAMPLES_ANDROID_O_DIR = TESTS_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_DIR = TESTS_DIR + "examplesAndroidP/";
  public static final String EXAMPLES_BUILD_DIR = THIRD_PARTY_DIR + "examples/";
  public static final String EXAMPLES_CF_DIR = EXAMPLES_BUILD_DIR + "classes/";
  public static final String EXAMPLES_ANDROID_N_BUILD_DIR = THIRD_PARTY_DIR + "examplesAndroidN/";
  public static final String EXAMPLES_ANDROID_O_BUILD_DIR = THIRD_PARTY_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_BUILD_DIR = THIRD_PARTY_DIR + "examplesAndroidP/";
  public static final String TESTS_BUILD_DIR = BUILD_DIR + "test/";
  public static final String EXAMPLES_JAVA9_BUILD_DIR = TESTS_BUILD_DIR + "examplesJava9/";
  public static final String EXAMPLES_JAVA10_BUILD_DIR = TESTS_BUILD_DIR + "examplesJava10/";
  public static final String EXAMPLES_JAVA11_JAR_DIR = TESTS_BUILD_DIR + "examplesJava11/";
  public static final String SMALI_BUILD_DIR = THIRD_PARTY_DIR + "smali/";

  public static String getExamplesJava11BuildDir() {
    // TODO(b/270105162): This changes when new gradle setup is default.
    if (ToolHelper.isNewGradleSetup()) {
      assert System.getProperty("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR") != null;
      return System.getProperty("EXAMPLES_JAVA_11_JAVAC_BUILD_DIR");
    } else {
      return BUILD_DIR + "classes/java/examplesJava11/";
    }
  }

  public static Path getR8MainPath() {
    // TODO(b/270105162): This changes when new gradle setup is default.
    if (ToolHelper.isNewGradleSetup()) {
      assert System.getProperty("R8_RUNTIME_PATH") != null;
      return Paths.get(System.getProperty("R8_RUNTIME_PATH"));
    } else {
      return isTestingR8Lib() ? R8LIB_JAR : R8_JAR_OLD;
    }
  }

  public static Path getRetracePath() {
    // TODO(b/270105162): This changes when new gradle setup is default.
    if (ToolHelper.isNewGradleSetup()) {
      assert System.getProperty("RETRACE_RUNTIME_PATH") != null;
      return Paths.get(System.getProperty("RETRACE_RUNTIME_PATH"));
    } else {
      return isTestingR8Lib() ? ToolHelper.R8_RETRACE_JAR : ToolHelper.R8_JAR_OLD;
    }
  }

  public static final Path CHECKED_IN_R8_17_WITH_DEPS =
      Paths.get(THIRD_PARTY_DIR).resolve("r8").resolve("r8_with_deps_17.jar");

  public static final String R8_TEST_BUCKET = "r8-test-results";

  public static final String ASM_JAR = BUILD_DIR + "deps/asm-9.5.jar";
  public static final String ASM_UTIL_JAR = BUILD_DIR + "deps/asm-util-9.5.jar";

  public static final Path API_SAMPLE_JAR =
      Paths.get(getProjectRoot(), "tests", "r8_api_usage_sample.jar");

  public static final String LINE_SEPARATOR = StringUtils.LINE_SEPARATOR;
  public static final String CLASSPATH_SEPARATOR = File.pathSeparator;

  public static final String DEFAULT_DEX_FILENAME = "classes.dex";
  public static final String DEFAULT_PROGUARD_MAP_FILE = "proguard.map";

  public static final String CORE_LAMBDA_STUBS =
      THIRD_PARTY_DIR + "core-lambda-stubs/core-lambda-stubs.jar";
  public static final String JSR223_RI_JAR = THIRD_PARTY_DIR + "jsr223-api-1.0/jsr223-api-1.0.jar";
  public static final String RHINO_ANDROID_JAR =
      THIRD_PARTY_DIR + "rhino-android-1.1.1/rhino-android-1.1.1.jar";
  public static final String RHINO_JAR = THIRD_PARTY_DIR + "rhino-1.7.10/rhino-1.7.10.jar";
  public static final String K2JVMCompiler = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler";
  private static final String ANDROID_JAR_PATTERN =
      THIRD_PARTY_DIR + "android_jar/lib-v%d/android.jar";
  private static final String ANDROID_API_VERSIONS_XML_PATTERN =
      THIRD_PARTY_DIR + "android_jar/lib-v%d/api-versions.xml";
  private static final AndroidApiLevel DEFAULT_MIN_SDK = AndroidApiLevel.I;

  public static final String OPEN_JDK_DIR = THIRD_PARTY_DIR + "openjdk/";
  public static final String CUSTOM_CONVERSION_DIR = OPEN_JDK_DIR + "custom_conversion/";
  public static final String JAVA_8_RUNTIME = OPEN_JDK_DIR + "openjdk-rt-1.8/rt.jar";
  public static final String JDK_11_TESTS_DIR = OPEN_JDK_DIR + "jdk-11-test/";
  public static final String JDK_11_TIME_TESTS_DIR = JDK_11_TESTS_DIR + "java/time/";

  private static final String PROGUARD5_2_1 =
      THIRD_PARTY_DIR + "proguard/proguard5.2.1/bin/proguard";
  private static final String PROGUARD6_0_1 =
      THIRD_PARTY_DIR + "proguard/proguard6.0.1/bin/proguard";
  private static final String PROGUARD = PROGUARD5_2_1;
  public static final Path JACOCO_ROOT = Paths.get(THIRD_PARTY_DIR, "jacoco", "0.8.6");
  public static final Path JACOCO_AGENT = JACOCO_ROOT.resolve(Paths.get("lib", "jacocoagent.jar"));
  public static final Path JACOCO_CLI = JACOCO_ROOT.resolve(Paths.get("lib", "jacococli.jar"));
  public static final Path GSON =
      Paths.get(THIRD_PARTY_DIR, "gson", "gson-2.10.1", "gson-2.10.1.jar");
  // Currently Gson is still shipping without consumer keep rules.
  public static final Path GSON_KEEP_RULES =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "gson", "gson-2.10.1", "gson.pro");
  public static final Path GUAVA_JRE =
      Paths.get(THIRD_PARTY_DIR, "guava", "guava-32.1.2-jre", "guava-32.1.2-jre.jar");
  public static final String PROGUARD_SETTINGS_FOR_INTERNAL_APPS =
      THIRD_PARTY_DIR + "proguardsettings/";

  public static final Path RETRACE_MAPS_DIR = Paths.get(THIRD_PARTY_DIR, "r8mappings");

  // TODO(b/270105162): These should be removed when finished transitioning.
  public static final Path R8_JAR_OLD = Paths.get(LIBS_DIR, "r8.jar");
  public static final Path R8_WITH_RELOCATED_DEPS_17_JAR =
      Paths.get(LIBS_DIR, "r8_with_relocated_deps_17.jar");
  public static final Path R8LIB_JAR = Paths.get(LIBS_DIR, "r8lib.jar");
  public static final Path R8LIB_MAP = Paths.get(LIBS_DIR, "r8lib.jar.map");
  public static final Path R8LIB_MAP_PARTITIONED = Paths.get(LIBS_DIR, "r8lib.jar_map.zip");
  public static final Path R8LIB_EXCLUDE_DEPS_JAR = Paths.get(LIBS_DIR, "r8lib-exclude-deps.jar");
  public static final Path R8LIB_EXCLUDE_DEPS_MAP =
      Paths.get(LIBS_DIR, "r8lib-exclude-deps.jar.map");
  public static final Path R8_RETRACE_JAR = Paths.get(LIBS_DIR, "r8retrace.jar");

  public static Path getDeps() {
    if (isNewGradleSetup()) {
      return Paths.get(System.getProperty("R8_DEPS"));
    } else {
      return Paths.get(LIBS_DIR, "deps_all.jar");
    }
  }

  public static Path getR8WithRelocatedDeps() {
    if (isNewGradleSetup()) {
      return Paths.get(System.getProperty("R8_WITH_RELOCATED_DEPS"));
    } else {
      return Paths.get(LIBS_DIR, "r8_with_relocated_deps.jar");
    }
  }

  public static final String DESUGARED_LIB_RELEASES_DIR =
      OPEN_JDK_DIR + "desugar_jdk_libs_releases/";
  public static final Path DESUGARED_JDK_8_LIB_JAR =
      Paths.get(OPEN_JDK_DIR + "desugar_jdk_libs/desugar_jdk_libs.jar");
  public static final Path DESUGARED_JDK_11_LIB_JAR =
      Paths.get(OPEN_JDK_DIR + "desugar_jdk_libs_11/desugar_jdk_libs.jar");

  public static final Path AAPT2 = Paths.get(THIRD_PARTY_DIR, "aapt2", "aapt2");

  public static Path getDesugarLibConversions(CustomConversionVersion legacy) {
    return Paths.get(CUSTOM_CONVERSION_DIR, legacy.getFileName());
  }

  public static boolean isLocalDevelopment() {
    return System.getProperty("local_development", "0").equals("1");
  }

  public static boolean shouldRunSlowTests() {
    return System.getProperty("slow_tests", "0").equals("1");
  }

  public static boolean verifyValidOutputMode(Backend backend, OutputMode outputMode) {
    return (backend == Backend.CF && outputMode == OutputMode.ClassFile) || backend == Backend.DEX;
  }

  public static boolean isBot() {
    String swarming_bot_id = System.getenv("SWARMING_BOT_ID");
    return swarming_bot_id != null && !swarming_bot_id.isEmpty();
  }

  public static StringConsumer consumeString(Consumer<String> consumer) {
    return new StringConsumer() {

      private StringBuilder builder;

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(string);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        if (builder != null) {
          consumer.accept(builder.toString());
        }
      }
    };
  }

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
    ART_DEFAULT(Version.DEFAULT, Kind.HOST),
    ART_8_1_0_TARGET(Version.V8_1_0, Kind.TARGET),
    ART_8_1_0_HOST(Version.V8_1_0, Kind.HOST),
    ART_9_0_0_TARGET(Version.V9_0_0, Kind.TARGET),
    ART_9_0_0_HOST(Version.V9_0_0, Kind.HOST),
    ART_10_0_0_TARGET(Version.V10_0_0, Kind.TARGET),
    ART_10_0_0_HOST(Version.V10_0_0, Kind.HOST),
    ART_12_0_0_TARGET(Version.V12_0_0, Kind.TARGET),
    ART_12_0_0_HOST(Version.V12_0_0, Kind.HOST),
    ART_13_0_0_TARGET(Version.V13_0_0, Kind.TARGET),
    ART_13_0_0_HOST(Version.V13_0_0, Kind.HOST),
    ART_14_0_0_TARGET(Version.V14_0_0, Kind.TARGET),
    ART_14_0_0_HOST(Version.V14_0_0, Kind.HOST),
    ART_MASTER_TARGET(Version.MASTER, Kind.TARGET),
    ART_MASTER_HOST(Version.MASTER, Kind.HOST);

    private static final ImmutableMap<String, DexVm> SHORT_NAME_MAP =
        Arrays.stream(DexVm.values()).collect(ImmutableMap.toImmutableMap(
            DexVm::toString, Function.identity()));

    public enum Version {
      V4_0_4("4.0.4"),
      V4_4_4("4.4.4"),
      V5_1_1("5.1.1"),
      V6_0_1("6.0.1"),
      V7_0_0("7.0.0"),
      V8_1_0("8.1.0"),
      // TODO(b/204855476): Remove DEFAULT.
      DEFAULT("default"),
      V9_0_0("9.0.0"),
      V10_0_0("10.0.0"),
      V12_0_0("12.0.0"),
      V13_0_0("13.0.0"),
      V14_0_0("14.0.0"),
      MASTER("master");

      /** This should generally be the latest DEX VM fully supported. */
      // TODO(b/204855476): Rename to DEFAULT alias once the checked in VM is removed.
      public static final Version NEW_DEFAULT = DEFAULT;

      Version(String shortName) {
        this.shortName = shortName;
      }

      public boolean isDalvik() {
        return isOlderThanOrEqual(Version.V4_4_4);
      }

      public boolean isDefault() {
        return this == NEW_DEFAULT;
      }

      public boolean isLatest() {
        return this == last();
      }

      public boolean isEqualTo(Version other) {
        return compareTo(other) == 0;
      }

      public boolean isEqualToOneOf(Version... versions) {
        return Arrays.stream(versions).anyMatch(this::isEqualTo);
      }

      public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
      }

      public boolean isNewerThanOrEqual(Version other) {
        return compareTo(other) >= 0;
      }

      public boolean isOlderThan(Version other) {
        return compareTo(other) < 0;
      }

      public boolean isOlderThanOrEqual(Version other) {
        return compareTo(other) <= 0;
      }

      public boolean isInRangeInclusive(Version start, Version end) {
        assert start.isOlderThanOrEqual(end);
        return isNewerThanOrEqual(start) && isOlderThanOrEqual(end);
      }

      public boolean hasRecordsSupport() {
        return isNewerThanOrEqual(V14_0_0);
      }

      public String toString() {
        return shortName;
      }

      private final String shortName;

      public static Version first() {
        return V4_0_4;
      }

      public static Version last() {
        return V14_0_0;
      }

      public static Version master() {
        return MASTER;
      }

      static {
        // Ensure first is always first and last is always last except for master.
        assert Arrays.stream(values()).allMatch(v -> v == first() || v.compareTo(first()) > 0);
        assert Arrays.stream(values())
            .allMatch(v -> v == last() || v == master() || v.compareTo(last()) < 0);
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

    public static DexVm fromVersion(Version version) {
      return SHORT_NAME_MAP.get(version.shortName + "_" + Kind.HOST);
    }

    public boolean isEqualTo(DexVm other) {
      return version.isEqualTo(other.version);
    }

    public boolean isNewerThan(DexVm other) {
      return version.isNewerThan(other.version);
    }

    public boolean isNewerThanOrEqual(DexVm other) {
      return version.isNewerThanOrEqual(other.version);
    }

    public boolean isOlderThan(DexVm other) {
      return version.isOlderThan(other.version);
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
    protected List<String> bootClasspaths = new ArrayList<>();
    protected String executionDirectory;

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

    public CommandBuilder appendBootClasspath(String lib) {
      bootClasspaths.add(lib);
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
      if (!bootClasspaths.isEmpty()) {
        result.add("-Xbootclasspath:" + String.join(":", bootClasspaths));
      }
      if (!classpaths.isEmpty()) {
        result.add("-cp");
        result.add(String.join(":", classpaths));
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
      ProcessBuilder processBuilder = new ProcessBuilder(command());
      if (executionDirectory != null) {
        processBuilder.directory(new File(executionDirectory));
      }
      return processBuilder;
    }

    public String build() {
      return String.join(" ", command());
    }

    protected abstract boolean shouldUseDocker();

    protected abstract String getExecutable();
  }

  public static class ArtCommandBuilder extends CommandBuilder {

    private DexVm version;
    private boolean withArtFrameworks;
    private CacheLookupKey artResultCacheLookupKey;
    private boolean noCaching = false;

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

    public void setNoCaching(boolean noCaching) {
      this.noCaching = noCaching;
    }

    @Override
    protected String getExecutable() {
      if (withArtFrameworks && version.isNewerThan(DexVm.ART_4_4_4_HOST)) {
        // Run directly Art in its repository, which has been patched by gradle to match expected
        // path for the frameworks.
        executionDirectory = getArtDir(version);
        return getRawArtBinary(version);
      }
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
          .setBootClasspath(toFileList(bootClasspaths))
          .setMainClass(mainClass)
          .setProgramArguments(programArguments);
    }

    private boolean useCache() {
      return !noCaching && CommandResultCache.isEnabled();
    }

    public void cacheResult(ProcessResult result) {
      // Only cache succeding runs, otherwise a flaky or killed art run can
      // put invalid entries into the cache.
      if (useCache() && result.exitCode == 0) {
        assert artResultCacheLookupKey != null;
        CommandResultCache.getInstance().putResult(result, artResultCacheLookupKey, null);
      }
    }

    public ProcessResult getCachedResults() throws IOException {
      if (!useCache()) {
        return null;
      }
      assert artResultCacheLookupKey == null;
      // Reuse the key when storing results if this is not already cached.
      artResultCacheLookupKey = new CacheLookupKey(this::hashParts);
      Pair<ProcessResult, Path> lookup =
          CommandResultCache.getInstance().lookup(artResultCacheLookupKey);
      return lookup == null ? null : lookup.getFirst();
    }

    private void hashParts(Hasher hasher) {
      // Call getExecutable first, this will set executionDirectory if needed.
      hasher.putString(this.getExecutable(), StandardCharsets.UTF_8);
      if (this.executionDirectory != null) {
        hasher.putString(this.executionDirectory, StandardCharsets.UTF_8);
      }
      hasher.putString(this.mainClass, StandardCharsets.UTF_8);
      hasher.putBoolean(this.withArtFrameworks);
      hashFilesFromList(hasher, classpaths);
      hashFilesFromList(hasher, bootClasspaths);
      systemProperties.forEach(
          (s, t) ->
              hasher.putString(s, StandardCharsets.UTF_8).putString(t, StandardCharsets.UTF_8));
      programArguments.forEach(s -> hasher.putString(s, StandardCharsets.UTF_8));
      options.forEach(s -> hasher.putString(s, StandardCharsets.UTF_8));
    }

    private static void hashFilesFromList(Hasher hasher, List<String> files) {
      for (String file : files) {
        try {
          hasher.putBytes(Files.readAllBytes(Paths.get(file)));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  public static class CacheLookupKey {
    private final Consumer<Hasher> hasherConsumer;
    private String hash;

    public CacheLookupKey(Consumer<Hasher> hasherConsumer) {
      this.hasherConsumer = hasherConsumer;
    }

    public String getHash() {
      if (hash == null) {
        Hasher hasher = Hashing.sha256().newHasher();
        hasherConsumer.accept(hasher);
        hash = hasher.hash().toString();
      }
      return hash;
    }
  }

  public static class CommandCacheStatistics {

    public static CommandCacheStatistics INSTANCE = new CommandCacheStatistics();
    private final Path cachePutCounter;
    private final Path cacheMissCounter;
    private final Path cacheHitCounter;

    private CommandCacheStatistics() {
      String commandCacheStatsDir = System.getProperty("command_cache_stats_dir");
      if (commandCacheStatsDir != null) {
        String processSpecificUUID = UUID.randomUUID().toString();
        cachePutCounter = Paths.get(commandCacheStatsDir, processSpecificUUID + "CACHEPUT");
        cacheMissCounter = Paths.get(commandCacheStatsDir, processSpecificUUID + "CACHEFAIL");
        cacheHitCounter = Paths.get(commandCacheStatsDir, processSpecificUUID + "CACHEHIT");
        try {
          Files.createFile(cachePutCounter);
          Files.createFile(cacheMissCounter);
          Files.createFile(cacheHitCounter);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } else {
        cachePutCounter = null;
        cacheMissCounter = null;
        cacheHitCounter = null;
      }
    }

    private static void increaseCount(Path path) {
      // Not enabled
      if (path == null) {
        return;
      }
      synchronized (path) {
        try {
          Files.write(path, "X".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    public void addCachePut() {
      increaseCount(cachePutCounter);
    }

    public void addCacheHit() {
      increaseCount(cacheHitCounter);
    }

    public void addCacheMiss() {
      increaseCount(cacheMissCounter);
    }
  }

  public static class CommandResultCache {
    private static CommandResultCache INSTANCE =
        System.getProperty("command_cache_dir") != null
            ? new CommandResultCache(Paths.get(System.getProperty("command_cache_dir")))
            : null;

    private final Path path;

    public CommandResultCache(Path path) {
      this.path = path;
    }

    public static CommandResultCache getInstance() {
      return INSTANCE;
    }

    public static boolean isEnabled() {
      return getInstance() != null;
    }

    private Path getStdoutFile(CacheLookupKey cacheLookupKey) {
      return path.resolve(cacheLookupKey.getHash() + ".stdout");
    }

    private Path getStderrFile(CacheLookupKey cacheLookupKey) {
      return path.resolve(cacheLookupKey.getHash() + ".stderr");
    }

    private Path getOutputFile(CacheLookupKey cacheLookupKey) {
      return path.resolve(cacheLookupKey.getHash() + ".output");
    }

    private Path getExitCodeFile(CacheLookupKey cacheLookupKey) {
      return path.resolve(cacheLookupKey.getHash());
    }

    private Path getTempFile(Path path) {
      return Paths.get(path.toString() + ".temp" + UUID.randomUUID());
    }

    private String getStringContent(Path path) {
      assert path.toFile().exists() : path + " does not exist";
      if (path.toFile().length() > 0) {
        try {
          return FileUtils.readTextFile(path, Charsets.UTF_8);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
      return "";
    }

    public Pair<ProcessResult, Path> lookup(CacheLookupKey cacheLookupKey) {
      // TODO Add concurrency handling!
      Path exitCodeFile = getExitCodeFile(cacheLookupKey);
      if (exitCodeFile.toFile().exists()) {
        int exitCode = Integer.parseInt(getStringContent(exitCodeFile));
        // Because of the temp files and order of writing we should never get here with an
        // inconsistent state. It is possible, although unlikely, that the stdout/stderr
        // (and even exitcode if art is non deterministic) are from different, process ids etc,
        // but this should have no impact.

        Path outputFile = getOutputFile(cacheLookupKey);
        CommandCacheStatistics.INSTANCE.addCacheHit();
        return new Pair(
            new ProcessResult(
                exitCode,
                getStringContent(getStdoutFile(cacheLookupKey)),
                getStringContent(getStderrFile(cacheLookupKey))),
            outputFile.toFile().exists() ? outputFile : null);
      }
      CommandCacheStatistics.INSTANCE.addCacheMiss();
      return null;
    }

    public void putResult(ProcessResult result, CacheLookupKey cacheLookupKey, Path output) {
      Path exitCodeFile = getExitCodeFile(cacheLookupKey);
      Path exitCodeTempFile = getTempFile(exitCodeFile);
      Path stdoutFile = getStdoutFile(cacheLookupKey);
      Path stdoutTempFile = getTempFile(stdoutFile);
      Path stderrFile = getStderrFile(cacheLookupKey);
      Path stderrTempFile = getTempFile(stderrFile);
      Path outputFile = getOutputFile(cacheLookupKey);
      Path outputTempFile = getTempFile(outputFile);

      try {
        String exitCode = "" + result.exitCode;
        // We avoid race conditions of writing vs reading by first writing all 3 files to temp
        // files, then moving these to the result files, moving last the exitcode file (which is
        // what we use as cache present check)
        Files.write(exitCodeTempFile, exitCode.getBytes(StandardCharsets.UTF_8));
        Files.write(stdoutTempFile, result.stdout.getBytes(StandardCharsets.UTF_8));
        Files.write(stderrTempFile, result.stderr.getBytes(StandardCharsets.UTF_8));
        if (output != null) {
          Files.copy(output, outputTempFile);
        }
        // Order is important, move exitcode file last!
        Files.move(
            stdoutTempFile,
            stdoutFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        Files.move(
            stderrTempFile,
            stderrFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        if (output != null) {
          Files.move(
              outputTempFile,
              outputFile,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(
            exitCodeTempFile,
            exitCodeFile,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
        CommandCacheStatistics.INSTANCE.addCachePut();
      } catch (IOException e) {
        StringBuilder exceptionMessage = new StringBuilder();
        exceptionMessage.append(
            "Files.exists(exitCodeTempFile) = " + Files.exists(exitCodeTempFile));
        exceptionMessage.append("Files.exists(stdoutTempFile) = " + Files.exists(stdoutTempFile));
        exceptionMessage.append("Files.exists(stderrTempFile) = " + Files.exists(stderrTempFile));
        exceptionMessage.append("Files.exists(outputTempFile) = " + Files.exists(outputTempFile));
        throw new RuntimeException(exceptionMessage.toString(), e);
      }
    }
  }

  private static List<File> toFileList(List<String> filePathList) {
    return filePathList.stream().map(File::new).collect(Collectors.toList());
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

  private static final Map<DexVm, String> ART_DIRS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "art")
          .put(DexVm.ART_MASTER_HOST, "host/art-master")
          .put(DexVm.ART_14_0_0_HOST, "host/art-14.0.0-beta3")
          .put(DexVm.ART_13_0_0_HOST, "host/art-13.0.0")
          .put(DexVm.ART_12_0_0_HOST, "host/art-12.0.0-beta4")
          .put(DexVm.ART_10_0_0_HOST, "art-10.0.0")
          .put(DexVm.ART_9_0_0_HOST, "art-9.0.0")
          .put(DexVm.ART_8_1_0_HOST, "art-8.1.0")
          .put(DexVm.ART_7_0_0_HOST, "art-7.0.0")
          .put(DexVm.ART_6_0_1_HOST, "art-6.0.1")
          .put(DexVm.ART_5_1_1_HOST, "art-5.1.1")
          .put(DexVm.ART_4_4_4_HOST, "dalvik")
          .put(DexVm.ART_4_0_4_HOST, "dalvik-4.0.4")
          .build();
  private static final Map<DexVm, String> ART_BINARY_VERSIONS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "bin/art")
          .put(DexVm.ART_MASTER_HOST, "bin/art")
          .put(DexVm.ART_14_0_0_HOST, "bin/art")
          .put(DexVm.ART_13_0_0_HOST, "bin/art")
          .put(DexVm.ART_12_0_0_HOST, "bin/art")
          .put(DexVm.ART_10_0_0_HOST, "bin/art")
          .put(DexVm.ART_9_0_0_HOST, "bin/art")
          .put(DexVm.ART_8_1_0_HOST, "bin/art")
          .put(DexVm.ART_7_0_0_HOST, "bin/art")
          .put(DexVm.ART_6_0_1_HOST, "bin/art")
          .put(DexVm.ART_5_1_1_HOST, "bin/art")
          .put(DexVm.ART_4_4_4_HOST, "bin/dalvik")
          .put(DexVm.ART_4_0_4_HOST, "bin/dalvik")
          .build();

  private static final Map<DexVm, String> ART_BINARY_VERSIONS_X64 =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "bin/art")
          .put(DexVm.ART_14_0_0_HOST, "bin/art")
          .put(DexVm.ART_13_0_0_HOST, "bin/art")
          .put(DexVm.ART_12_0_0_HOST, "bin/art")
          .put(DexVm.ART_10_0_0_HOST, "bin/art")
          .put(DexVm.ART_9_0_0_HOST, "bin/art")
          .put(DexVm.ART_8_1_0_HOST, "bin/art")
          .put(DexVm.ART_7_0_0_HOST, "bin/art")
          .put(DexVm.ART_6_0_1_HOST, "bin/art")
          .build();

  private static final List<String> DALVIK_4_0_BOOT_LIBS =
      ImmutableList.of("core-hostdex.jar", "apache-xml-hostdex.jar");

  private static final List<String> DALVIK_4_4_BOOT_LIBS =
      ImmutableList.of("core-libart-hostdex.jar", "core-hostdex.jar", "apache-xml-hostdex.jar");

  private static final List<String> ART_5_TO_6_BOOT_LIBS =
      ImmutableList.of("core-libart-hostdex.jar");

  private static final List<String> ART_7_TO_10_BOOT_LIBS =
      ImmutableList.of("core-libart-hostdex.jar", "core-oj-hostdex.jar", "apache-xml-hostdex.jar");

  private static final List<String> ART_12_PLUS_BOOT_LIBS =
      ImmutableList.of(
          "core-libart-hostdex.jar",
          "core-oj-hostdex.jar",
          "core-icu4j-hostdex.jar",
          "apache-xml-hostdex.jar");

  private static final Map<DexVm, List<String>> BOOT_LIBS;

  static {
    ImmutableMap.Builder<DexVm, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.ART_DEFAULT, ART_7_TO_10_BOOT_LIBS)
        .put(DexVm.ART_14_0_0_HOST, ART_12_PLUS_BOOT_LIBS)
        .put(DexVm.ART_13_0_0_HOST, ART_12_PLUS_BOOT_LIBS)
        .put(DexVm.ART_12_0_0_HOST, ART_12_PLUS_BOOT_LIBS)
        .put(DexVm.ART_10_0_0_HOST, ART_7_TO_10_BOOT_LIBS)
        .put(DexVm.ART_9_0_0_HOST, ART_7_TO_10_BOOT_LIBS)
        .put(DexVm.ART_8_1_0_HOST, ART_7_TO_10_BOOT_LIBS)
        .put(DexVm.ART_7_0_0_HOST, ART_7_TO_10_BOOT_LIBS)
        .put(DexVm.ART_6_0_1_HOST, ART_5_TO_6_BOOT_LIBS)
        .put(DexVm.ART_5_1_1_HOST, ART_5_TO_6_BOOT_LIBS)
        .put(DexVm.ART_4_4_4_HOST, DALVIK_4_4_BOOT_LIBS)
        .put(DexVm.ART_4_0_4_HOST, DALVIK_4_0_BOOT_LIBS);
    BOOT_LIBS = builder.build();
  }

  private static final Map<DexVm, String> PRODUCT;

  static {
    ImmutableMap.Builder<DexVm, String> builder = ImmutableMap.builder();
    builder
        .put(DexVm.ART_DEFAULT, "angler")
        .put(DexVm.ART_14_0_0_HOST, "redfin")
        .put(DexVm.ART_13_0_0_HOST, "redfin")
        .put(DexVm.ART_12_0_0_HOST, "redfin")
        .put(DexVm.ART_10_0_0_HOST, "coral")
        .put(DexVm.ART_9_0_0_HOST, "marlin")
        .put(DexVm.ART_8_1_0_HOST, "marlin")
        .put(DexVm.ART_7_0_0_HOST, "angler")
        .put(DexVm.ART_6_0_1_HOST, "angler")
        .put(DexVm.ART_5_1_1_HOST, "mako")
        .put(DexVm.ART_4_4_4_HOST, "<missing>")
        .put(DexVm.ART_4_0_4_HOST, "<missing>");
    PRODUCT = builder.build();
  }

  private static Path getDexVmPath(DexVm vm) {
    DexVm.Version version = vm.getVersion();
    Path base = Paths.get(TOOLS_DIR, "linux");
    switch (version) {
      case DEFAULT:
        return base.resolve("art");
      case V4_0_4:
      case V4_4_4:
      case V5_1_1:
      case V6_0_1:
      case V7_0_0:
      case V8_1_0:
      case V9_0_0:
      case V10_0_0:
        return base.resolve("art-" + version);
      case V12_0_0:
        return base.resolve("host").resolve("art-12.0.0-beta4");
      case V13_0_0:
      case V14_0_0:
      case MASTER:
        return base.resolve("host").resolve("art-" + version);
      default:
        throw new Unreachable();
    }
  }

  private static Path getDexVmLibPath(DexVm vm) {
    return getDexVmPath(vm).resolve("lib");
  }

  private static Path getDex2OatPath(DexVm vm) {
    return getDexVmPath(vm).resolve("bin").resolve("dex2oat");
  }

  private static Path getProductPath(DexVm vm) {
    return getDexVmPath(vm).resolve("product").resolve(PRODUCT.get(vm));
  }

  private static String getArchString(DexVm vm) {
    switch (vm.getVersion()) {
      case V4_0_4:
      case V4_4_4:
      case V5_1_1:
        return "arm";
      case V6_0_1:
      case V7_0_0:
      case V8_1_0:
      case DEFAULT:
      case V9_0_0:
      case V10_0_0:
        return "arm64";
      case V12_0_0:
      case V13_0_0:
      case V14_0_0:
      case MASTER:
        return "x86_64";
      default:
        throw new Unimplemented();
    }
  }

  public static byte[] getClassAsBytes(Class<?> clazz) throws IOException {
    return Files.readAllBytes(getClassFileForTestClass(clazz));
  }

  public static byte[] getClassAsBytes(Class<?> clazz, TestDataSourceSet dataSourceSet)
      throws IOException {
    return Files.readAllBytes(getClassFileForTestClass(clazz, dataSourceSet));
  }

  public static long getClassByteCrc(Class<?> clazz) {
    byte[] bytes = null;
    try {
      bytes = getClassAsBytes(clazz);
    } catch (IOException ioe) {
      Assert.fail(ioe.toString());
    }
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length);
    return crc.getValue();
  }

  public static String getArtDir(DexVm version) {
    String dir = ART_DIRS.get(version);
    if (dir == null) {
      throw new IllegalStateException("Does not support dex vm: " + version);
    }
    if (isLinux() || isMac()) {
      // The Linux version is used on Mac, where it is run in a Docker container.
      return TOOLS_DIR + "linux/" + dir;
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

  public static String getProguard5Script() {
    if (isWindows()) {
      return PROGUARD + ".bat";
    }
    return PROGUARD + ".sh";
  }

  public static String getProguard6Script() {
    if (isWindows()) {
      return PROGUARD6_0_1 + ".bat";
    }
    return PROGUARD6_0_1 + ".sh";
  }

  public static Backend[] getBackends() {
    if (getDexVm() == DexVm.ART_DEFAULT) {
      return Backend.values();
    }
    return new Backend[]{Backend.DEX};
  }

  public static String getArtBinary(DexVm version) {
    return getArtDir(version) + "/" + getRawArtBinary(version);
  }

  public static String getRawArtBinary(DexVm version) {
    String binary = ART_BINARY_VERSIONS.get(version);
    if (binary == null) {
      throw new IllegalStateException("Does not support running with dex vm: " + version);
    }
    return binary;
  }

  public static Path getJava8RuntimeJar() {
    return Paths.get(JAVA_8_RUNTIME);
  }

  public static Path getCoreLambdaStubs() {
    return Paths.get(CORE_LAMBDA_STUBS);
  }

  @Deprecated
  // Use getFirstSupportedAndroidJar(AndroidApiLevel) to specify a specific Android jar.
  public static Path getDefaultAndroidJar() {
    return getAndroidJar(AndroidApiLevel.getDefault());
  }

  public static Path getFirstSupportedAndroidJar(AndroidApiLevel apiLevel) {
    // Fast path.
    if (hasAndroidJar(apiLevel)) {
      return getAndroidJar(apiLevel.getLevel());
    }
    // Search for an android jar.
    for (AndroidApiLevel level : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      if (level.getLevel() >= apiLevel.getLevel() && hasAndroidJar(level)) {
        return getAndroidJar(level.getLevel());
      }
    }
    return getAndroidJar(AndroidApiLevel.LATEST);
  }

  public static Path getAndroidJar(int apiLevel) {
    return getAndroidJar(AndroidApiLevel.getAndroidApiLevel(apiLevel));
  }

  public static Path getApiVersionsXmlFile(AndroidApiLevel apiLevel) {
    // We only store api-versions.xml from S and up.
    assert apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.S);
    return Paths.get(String.format(ANDROID_API_VERSIONS_XML_PATTERN, apiLevel.getLevel()));
  }

  private static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    if (apiLevel == AndroidApiLevel.MASTER) {
      return Paths.get(THIRD_PARTY_DIR + "android_jar/lib-master/android.jar");
    }
    String jar = String.format(
        ANDROID_JAR_PATTERN,
        (apiLevel == AndroidApiLevel.getDefault() ? DEFAULT_MIN_SDK : apiLevel).getLevel());
    return Paths.get(jar);
  }

  public static boolean hasAndroidJar(AndroidApiLevel apiLevel) {
    Path path = getAndroidJarPath(apiLevel);
    return Files.exists(path);
  }

  public static boolean shouldHaveAndroidJar(AndroidApiLevel apiLevel) {
    switch (apiLevel) {
      case B_1_1:
      case C:
      case D:
      case E:
      case E_0_1:
      case E_MR1:
      case F:
      case G:
      case G_MR1:
      case H:
      case H_MR1:
      case H_MR2:
      case J:
      case J_MR1:
      case J_MR2:
      case K_WATCH:
        return false;
      default:
        return true;
    }
  }

  public static Path getAndroidJar(AndroidApiLevel apiLevel) {
    Path path = getAndroidJarPath(apiLevel);
    assert Files.exists(path)
        : "Expected android jar to exist for API level " + apiLevel + " at " + path;
    return path;
  }

  public static Path getMostRecentAndroidJar() {
    List<AndroidApiLevel> apiLevels = AndroidApiLevel.getAndroidApiLevelsSorted();
    ListIterator<AndroidApiLevel> iterator = apiLevels.listIterator(apiLevels.size());
    while (iterator.hasPrevious()) {
      AndroidApiLevel apiLevel = iterator.previous();
      if (hasAndroidJar(apiLevel)) {
        return getAndroidJar(apiLevel);
      }
    }
    throw new Unreachable("Unable to find a most recent android.jar");
  }

  public static Path getJdwpTestsCfJarPath(AndroidApiLevel minSdk) {
    String jar =
        minSdk.isLessThan(AndroidApiLevel.N)
            ? "apache-harmony-jdwp-tests-host-preN.jar"
            : "apache-harmony-jdwp-tests-host.jar";
    return Paths.get(ToolHelper.THIRD_PARTY_DIR, "jdwp-tests", jar);
  }

  public static Path getJunitFromDeps() {
    return Paths.get(DEPENDENCIES, "junit", "junit", "4.13-beta-2", "junit-4.13-beta-2.jar");
  }

  public static Path getHamcrestFromDeps() {
    return Paths.get(
        DEPENDENCIES, "org", "hamcrest", "hamcrest-core", "1.3", "hamcrest-core-1.3.jar");
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

    @Override
    protected void after() {} // instead of remove, do nothing
  }

  // For non-Linux platforms create the temporary directory in the repository root to simplify
  // running Art in a docker container
  public static TemporaryFolder getTemporaryFolderForTest() {
    String tmpDir = System.getProperty("test_dir");
    if (tmpDir == null) {
      return new TemporaryFolder();
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

  public static List<String> getBootLibs(DexVm dexVm) {
    String prefix = getArtDir(dexVm) + "/";
    List<String> result = new ArrayList<>();
    BOOT_LIBS.get(dexVm).stream().forEach(x -> result.add(prefix + "framework/" + x));
    return result;
  }

  // Returns if the passed in vm to use is the default.
  public static boolean isDefaultDexVm(DexVm dexVm) {
    return dexVm == DexVm.ART_DEFAULT;
  }

  @Deprecated
  public static DexVm getDexVm() {
    String artVersion = System.getProperty("dex_vm");
    if (artVersion == null) {
      return DexVm.ART_DEFAULT;
    } else {
      DexVm artVersionEnum = DexVm.fromShortName(artVersion);
      if (artVersionEnum == null
          && !artVersion.endsWith(Kind.HOST.toString())
          && !artVersion.endsWith(Kind.TARGET.toString())) {
        // Default to host Art/Dalvik when not specified.
        artVersionEnum = DexVm.fromShortName(artVersion + '_' + Kind.HOST.toString());
      }
      if (artVersionEnum == null) {
        throw new RuntimeException("Unsupported Art version " + artVersion);
      } else {
        return artVersionEnum;
      }
    }
  }

  public static AndroidApiLevel getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel threshold) {
    AndroidApiLevel minApiLevelForDexVm = getMinApiLevelForDexVm();
    return minApiLevelForDexVm.getLevel() < threshold.getLevel() ? minApiLevelForDexVm : threshold;
  }

  public static AndroidApiLevel getMinApiLevelForDexVm() {
    return getMinApiLevelForDexVm(ToolHelper.getDexVm());
  }

  public static AndroidApiLevel getMinApiLevelForDexVm(DexVm dexVm) {
    switch (dexVm.version) {
      case MASTER:
        return AndroidApiLevel.MASTER;
      case V14_0_0:
        return AndroidApiLevel.U;
      case V13_0_0:
        return AndroidApiLevel.T;
      case V12_0_0:
        return AndroidApiLevel.S;
      case V10_0_0:
        return AndroidApiLevel.Q;
      case V9_0_0:
        return AndroidApiLevel.P;
      case V8_1_0:
        return AndroidApiLevel.O_MR1;
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

  public static DexVm.Version getDexVersionForApiLevel(AndroidApiLevel apiLevel) {
    switch (apiLevel) {
      case MASTER:
        return DexVm.Version.MASTER;
      case U:
        return DexVm.Version.V14_0_0;
      case T:
        return DexVm.Version.V13_0_0;
      case Sv2:
      case S:
        return DexVm.Version.V12_0_0;
      case R:
        throw new Unreachable("No Android 11 VM");
      case Q:
        return DexVm.Version.V10_0_0;
      case P:
        return DexVm.Version.V9_0_0;
      case O_MR1:
      case O:
        // Currently no Android 8 VM, so return 8.1 for both O and O_MR1.
        return DexVm.Version.V8_1_0;
      case N:
        return DexVm.Version.V7_0_0;
      case M:
        return DexVm.Version.V6_0_1;
      case L_MR1:
        return DexVm.Version.V5_1_1;
      case K:
        return DexVm.Version.V4_4_4;
      case I_MR1:
        return DexVm.Version.V4_0_4;
      default:
        throw new Unreachable("No Android VM for API level " + apiLevel.getLevel());
    }
  }

  public static DexVersion getDexFileVersionForVm(DexVm vm) {
    return DexVersion.getDexVersion(getMinApiLevelForDexVm(vm));
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

  public static boolean isJava8Runtime() {
    return System.getProperty("java.specification.version").equals("8");
  }

  public static boolean isJava9Runtime() {
    return System.getProperty("java.specification.version").equals("9");
  }

  public static boolean isTestingR8Lib() {
    return System.getProperty("java.class.path").contains("r8lib.jar");
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

  public static boolean isDex2OatSupported() {
    return !isWindows();
  }

  public static Path getClassPathForTests() {
    return getClassPathForTestDataSourceSet(computeLegacyOrGradleSpecifiedLocation());
  }

  public static Path getClassPathForTestDataSourceSet(TestDataSourceSet sourceSet) {
    return sourceSet.getBuildDir();
  }

  private static List<String> getNamePartsForTestPackage(Package pkg) {
    return Lists.newArrayList(pkg.getName().split("\\."));
  }

  public static Path getPackageDirectoryForTestPackage(Package pkg) {
    List<String> parts = getNamePartsForTestPackage(pkg);
    return getClassPathForTests().resolve(Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY)));
  }

  private static List<String> getNamePartsForTestClass(Class<?> clazz) {
    List<String> parts = Lists.newArrayList(clazz.getTypeName().split("\\."));
    parts.set(parts.size() - 1, parts.get(parts.size() - 1) + ".class");
    return parts;
  }

  public static List<Path> getClassFilesForTestPackage(Package pkg) throws IOException {
    return getClassFilesForTestDirectory(ToolHelper.getPackageDirectoryForTestPackage(pkg));
  }

  public static List<Path> getClassFilesForTestDirectory(Path directory) throws IOException {
    return getClassFilesForTestDirectory(directory, null);
  }

  public static List<Path> getClassFilesForTestDirectory(
      Path directory, Predicate<Path> filter) throws IOException {
    return Files.walk(directory)
        .filter(path -> path.toString().endsWith(".class") && (filter == null || filter.test(path)))
        .collect(Collectors.toList());
  }

  public static Path getSourceFileForTestClass(Class<?> clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    String last = parts.get(parts.size() - 1);
    assert last.endsWith(CLASS_EXTENSION);
    parts.set(
        parts.size() - 1,
        last.substring(0, last.length() - CLASS_EXTENSION.length()) + JAVA_EXTENSION);
    return Paths.get(TESTS_SOURCE_DIR)
        .resolve(Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY)));
  }

  public static Path getClassFileForTestClass(Class<?> clazz) {
    return getClassFileForTestClass(clazz, computeLegacyOrGradleSpecifiedLocation());
  }

  public static Path getClassFileForTestClass(Class<?> clazz, TestDataSourceSet sourceSet) {
    List<String> parts = getNamePartsForTestClass(clazz);
    Path resolve =
        getClassPathForTestDataSourceSet(sourceSet)
            .resolve(Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY)));
    if (!Files.exists(resolve)) {
      throw new RuntimeException("Could not find: " + resolve.toString());
    }
    return resolve;
  }

  public static Collection<Path> getClassFilesForInnerClasses(Collection<Class<?>> classes)
      throws IOException {
    return getClassFilesForInnerClasses(computeLegacyOrGradleSpecifiedLocation(), classes);
  }

  public static Collection<Path> getClassFilesForInnerClasses(Class<?>... classes)
      throws IOException {
    return getClassFilesForInnerClasses(
        computeLegacyOrGradleSpecifiedLocation(), Arrays.asList(classes));
  }

  public static Collection<Path> getClassFilesForInnerClasses(
      TestDataSourceSet sourceSet, Class<?>... classes) throws IOException {
    return getClassFilesForInnerClasses(sourceSet, Arrays.asList(classes));
  }

  public static Collection<Path> getClassFilesForInnerClasses(
      TestDataSourceSet sourceSet, Collection<Class<?>> classes) throws IOException {
    Set<Path> paths = new HashSet<>();
    for (Class<?> clazz : classes) {
      Path path = ToolHelper.getClassFileForTestClass(clazz, sourceSet);
      String prefix = path.toString().replace(CLASS_EXTENSION, "$");
      paths.addAll(
          ToolHelper.getClassFilesForTestDirectory(
              path.getParent(), p -> p.toString().startsWith(prefix)));
    }
    return paths;
  }

  public static Path getFileNameForTestClass(Class<?> clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY));
  }

  public static String getJarEntryForTestClass(Class<?> clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return String.join("/", parts);
  }

  public static DirectMappedDexApplication buildApplication(List<String> fileNames)
      throws IOException, ExecutionException {
    return buildApplicationWithAndroidJar(fileNames, getDefaultAndroidJar());
  }

  public static DirectMappedDexApplication buildApplicationWithAndroidJar(
      List<String> fileNames, Path androidJar) throws IOException {
    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(ListUtils.map(fileNames, Paths::get))
            .addLibraryFiles(androidJar)
            .build();
    return new ApplicationReader(input, new InternalOptions(), Timing.empty()).read().toDirect();
  }

  public static ProguardConfiguration loadProguardConfiguration(
      DexItemFactory factory, List<Path> configPaths) {
    Reporter reporter = new Reporter();
    if (configPaths.isEmpty()) {
      return ProguardConfiguration.builder(factory, reporter)
          .disableShrinking()
          .disableObfuscation()
          .disableOptimization()
          .addKeepAttributePatterns(ImmutableList.of("*"))
          .build();
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
    return prepareR8CommandBuilder(app, DexIndexedConsumer.emptyConsumer());
  }

  public static R8Command.Builder prepareR8CommandBuilder(
      AndroidApp app, ProgramConsumer programConsumer) {
    return R8Command.builder(app)
        .setProgramConsumer(programConsumer)
        .setProguardMapConsumer(StringConsumer.emptyConsumer());
  }

  public static R8Command.Builder prepareR8CommandBuilder(
      AndroidApp app, ProgramConsumer programConsumer, DiagnosticsHandler diagnosticsHandler) {
    return R8Command.builder(app, diagnosticsHandler)
        .setProgramConsumer(programConsumer)
        .setProguardMapConsumer(StringConsumer.emptyConsumer());
  }

  public static AndroidApp runR8(AndroidApp app) throws CompilationFailedException {
    return runR8WithProgramConsumer(app, DexIndexedConsumer.emptyConsumer());
  }

  public static AndroidApp runR8WithProgramConsumer(AndroidApp app, ProgramConsumer programConsumer)
      throws CompilationFailedException {
    return runR8(prepareR8CommandBuilder(app, programConsumer).build());
  }

  public static AndroidApp runR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    R8Command command = prepareR8CommandBuilder(app)
        .setDisableTreeShaking(true)
        .setDisableMinification(true)
        .build();
    return runR8(command, optionsConsumer);
  }

  public static AndroidApp runR8(R8Command command) throws CompilationFailedException {
    return runR8(command, null);
  }

  public static AndroidApp runR8(R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return runR8WithFullResult(command, optionsConsumer);
  }

  public static void runR8WithOptionsModificationOnly(
      R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    optionsConsumer.accept(options);
    R8.runForTesting(app, options);
  }

  public static void runAndBenchmarkR8WithoutResult(
      R8Command.Builder commandBuilder,
      Consumer<InternalOptions> optionsConsumer,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    long start = 0;
    if (benchmarkResults != null) {
      start = System.nanoTime();
    }
    R8Command command = commandBuilder.build();
    InternalOptions internalOptions = command.getInternalOptions();
    optionsConsumer.accept(internalOptions);
    R8.runForTesting(command.getInputApp(), internalOptions);
    if (benchmarkResults != null) {
      long end = System.nanoTime();
      benchmarkResults.addRuntimeResult(end - start);
    }
  }

  public static AndroidApp runR8WithFullResult(
      R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    // TODO(zerny): Should we really be adding the android library in ToolHelper?
    AndroidApp app = command.getInputApp();
    if (app.getLibraryResourceProviders().isEmpty()) {
      // Add the android library matching the minsdk. We filter out junit and testing classes
      // from the android jar to avoid duplicate classes in art tests.
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

  public static void runL8(L8Command command) throws CompilationFailedException {
    runL8(command, options -> {});
  }

  public static void runL8(L8Command command, Consumer<InternalOptions> optionsModifier)
      throws CompilationFailedException {
    InternalOptions internalOptions = command.getInternalOptions();
    optionsModifier.accept(internalOptions);
    L8.runForTesting(
        command.getInputApp(),
        internalOptions,
        command.isShrinking(),
        command.getD8Command(),
        command.getR8Command());
  }

  public static void addFilteredAndroidJar(BaseCommand.Builder builder, AndroidApiLevel apiLevel) {
    addFilteredAndroidJar(getAppBuilder(builder), apiLevel);
  }

  public static void addFilteredAndroidJar(AndroidApp.Builder builder, AndroidApiLevel apiLevel) {
    builder.addFilteredLibraryArchives(
        Collections.singletonList(
            new FilteredClassPath(
                getAndroidJar(apiLevel),
                ImmutableList.of("!junit/**", "!android/test/**"),
                Origin.unknown(),
                Position.UNKNOWN)));
  }

  public static AndroidApp runD8(AndroidApp app) throws CompilationFailedException {
    return runD8(app, null);
  }

  public static AndroidApp runD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return runD8(D8Command.builder(app), optionsConsumer);
  }

  public static AndroidApp runD8(D8Command.Builder builder) throws CompilationFailedException {
    return runD8(builder, null);
  }

  public static AndroidApp runD8(
      D8Command.Builder builder, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return runAndBenchmarkD8(builder, optionsConsumer, null);
  }

  public static AndroidApp runAndBenchmarkD8(
      D8Command.Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      BenchmarkResults benchmarkResults)
      throws CompilationFailedException {
    AndroidAppConsumers compatSink = new AndroidAppConsumers(builder);
    long start = 0;
    if (benchmarkResults != null) {
      start = System.nanoTime();
    }
    D8Command command = builder.build();
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      ExceptionUtils.withD8CompilationHandler(
          options.reporter, () -> optionsConsumer.accept(options));
    }
    D8.runForTesting(command.getInputApp(), options);
    if (benchmarkResults != null) {
      long end = System.nanoTime();
      benchmarkResults.addRuntimeResult(end - start);
    }
    return compatSink.build();
  }

  public static void runLegacyResourceShrinker(
      ResourceShrinker.Builder builder,
      Consumer<InternalOptions> optionsConsumer,
      ReferenceChecker callback)
      throws IOException, CompilationFailedException, ExecutionException {
    ResourceShrinker.Command command = builder.build();
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      ExceptionUtils.withD8CompilationHandler(
          options.reporter, () -> optionsConsumer.accept(options));
    }
    ResourceShrinker.runForTesting(command.getInputApp(), options, callback);
  }

  @Deprecated
  public static ProcessResult runJava(Class clazz) throws Exception {
    String main = clazz.getTypeName();
    Path path = getClassPathForTests();
    return runJava(path, main);
  }

  @Deprecated
  public static ProcessResult runJava(Path classpath, String... args) throws IOException {
    return runJava(ImmutableList.of(classpath), args);
  }

  @Deprecated
  public static ProcessResult runJava(List<Path> classpath, String... args) throws IOException {
    return runJava(ImmutableList.of(), classpath, args);
  }

  private static ProcessResult runJava(List<String> vmArgs, List<Path> classpath, String... args)
      throws IOException {
    return runJava(TestRuntime.getSystemRuntime().asCf(), vmArgs, classpath, args);
  }

  public static ProcessResult runJava(CfRuntime runtime, List<Path> classpath, String... args)
      throws IOException {
    return runJava(runtime, ImmutableList.of(), classpath, args);
  }

  public static ProcessResult runJava(
      CfRuntime runtime, List<String> vmArgs, List<Path> classpath, String... args)
      throws IOException {
    return runJava(runtime, vmArgs, ImmutableList.of(), classpath, args);
  }

  public static ProcessResult runJava(
      CfRuntime runtime,
      List<String> vmArgs,
      List<Path> bootClasspaths,
      List<Path> classpath,
      String... args)
      throws IOException {
    List<String> cmdline = new ArrayList<>(Arrays.asList(runtime.getJavaExecutable().toString()));
    cmdline.addAll(vmArgs);
    if (!bootClasspaths.isEmpty()) {
      cmdline.add(
          "-Xbootclasspath/a:"
              + bootClasspaths.stream()
                  .map(Path::toString)
                  .collect(Collectors.joining(CLASSPATH_SEPARATOR)));
    }
    cmdline.add("-cp");
    cmdline.add(
        classpath.stream().map(Path::toString).collect(Collectors.joining(CLASSPATH_SEPARATOR)));
    cmdline.addAll(Arrays.asList(args));
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return runProcess(builder);
  }

  public static ProcessResult runJavaNoVerify(
      Path classpath, String mainClass, String... args) throws IOException {
    return runJavaNoVerify(
        Collections.singletonList(classpath), mainClass, Lists.newArrayList(args));
  }

  public static ProcessResult runJavaNoVerify(
      List<Path> classpath, String mainClass, String... args) throws IOException {
    return runJavaNoVerify(classpath, mainClass, Lists.newArrayList(args));
  }

  public static ProcessResult runJavaNoVerify(
      List<Path> classpath, String mainClass, List<String> args) throws IOException {
    String cp =
        classpath.stream().map(Path::toString).collect(Collectors.joining(CLASSPATH_SEPARATOR));
    ArrayList<String> cmdline = Lists.newArrayList(
        getJavaExecutable(), "-cp", cp, "-noverify", mainClass);
    cmdline.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return runProcess(builder);
  }

  public static ProcessResult runAapt2(String... args) throws IOException {
    ArrayList<String> cmd = Lists.newArrayList(AAPT2.toString());
    cmd.addAll(Lists.newArrayList(args));
    ProcessBuilder builder = new ProcessBuilder(cmd);
    return runProcess(builder);
  }

  public static ProcessResult forkD8(Path dir, String... args) throws IOException {
    return forkJava(dir, D8.class, args);
  }

  public static ProcessResult forkR8(Path dir, String... args) throws IOException {
    return forkJava(dir, R8.class, args);
  }

  public static ProcessResult forkGenerateMainDexList(Path dir, List<String> args1, String... args2)
      throws IOException {
    List<String> args = new ArrayList<>();
    args.addAll(args1);
    args.addAll(Arrays.asList(args2));
    return forkJava(dir, GenerateMainDexList.class, args);
  }

  private static ProcessResult forkJava(Path dir, Class clazz, String... args) throws IOException {
    return forkJava(dir, clazz, Arrays.asList(args));
  }

  private static ProcessResult forkJavaWithJar(Path dir, String jarPath, List<String> args)
      throws IOException {
    return forkJavaWithJarAndJavaOptions(dir, ImmutableList.of(), jarPath, args);
  }

  private static ProcessResult forkJavaWithJarAndJavaOptions(
      Path dir, List<String> javaOptions, String jarPath, List<String> args) throws IOException {
    List<String> command =
        new ImmutableList.Builder<String>()
            .add(getJavaExecutable())
            .addAll(javaOptions)
            .add("-jar")
            .add(jarPath)
            .addAll(args)
            .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }

  public static ProcessResult forkJavaWithJavaOptions(
      Path dir, List<String> javaOptions, Class clazz, List<String> args) throws IOException {
    List<String> command =
        new ImmutableList.Builder<String>()
            .add(getJavaExecutable())
            .addAll(javaOptions)
            .add("-cp")
            .add(System.getProperty("java.class.path"))
            .add(clazz.getCanonicalName())
            .addAll(args)
            .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }

  private static ProcessResult forkJava(Path dir, Class clazz, List<String> args)
      throws IOException {
    List<String> command = new ImmutableList.Builder<String>()
        .add(getJavaExecutable())
        .add("-cp").add(System.getProperty("java.class.path"))
        .add(clazz.getCanonicalName())
        .addAll(args)
        .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }

  @Deprecated
  // Use CfRuntime.getJavaExecutable() for a specific JDK or getSystemJavaExecutable
  public static String getJavaExecutable() {
    return getSystemJavaExecutable();
  }

  public static String getSystemJavaExecutable() {
    return TestRuntime.getSystemRuntime().asCf().getJavaExecutable().toString();
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
    return runArtRaw(files, mainClass, extras, null, false);
  }

  // Index used to name directory aimed at storing dex files and process result
  // for one invokation of runArtRaw() in order to avoid conflicts in case of
  // multiple calls within the same test.
  private static int testOutputPathIndex = 0;

  public static ProcessResult runArtRaw(
      List<String> files,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version,
      boolean withArtFrameworks,
      String... args)
      throws IOException {
    ArtCommandBuilder builder =
        version != null ? new ArtCommandBuilder(version) : new ArtCommandBuilder();
    builder.withArtFrameworks = withArtFrameworks;
    files.forEach(builder::appendClasspath);
    builder.setMainClass(mainClass);
    if (extras != null) {
      extras.accept(builder);
    }
    for (String arg : args) {
      builder.appendProgramArgument(arg);
    }
    ProcessResult processResult = null;

    // Whenever we start a new test method we reset the index count.
    String reset_output_index = System.getProperty("reset_output_index");
    if (reset_output_index != null) {
      System.clearProperty("reset_output_index");
      testOutputPathIndex = 0;
    } else {
      assert testOutputPathIndex >= 0;
      testOutputPathIndex++;
    }

    String goldenFilesDirInProp = System.getProperty("use_golden_files_in");
    if (goldenFilesDirInProp != null) {
      File goldenFileDir = new File(goldenFilesDirInProp);
      assert goldenFileDir.isDirectory();
      processResult =
          compareAgainstGoldenFiles(
              files.stream().map(File::new).collect(Collectors.toList()), goldenFileDir);
      if (processResult.exitCode == 0) {
        processResult = readProcessResult(goldenFileDir);
      }
    } else {
      processResult = runArtProcessRaw(builder);
    }

    String goldenFilesDirToProp = System.getProperty("generate_golden_files_to");
    if (goldenFilesDirToProp != null) {
      File goldenFileDir = new File(goldenFilesDirToProp);
      assert goldenFileDir.isDirectory();
      storeAsGoldenFiles(files.stream().map(File::new).collect(Collectors.toList()), goldenFileDir);
      storeProcessResult(processResult, goldenFileDir);
    }

    return processResult;
  }

  public static ProcessResult runJaCoCoInstrument(Path sourceClassFiles, Path outputDirectory)
      throws IOException {
    List<String> cmdline = new ArrayList<>();
    cmdline.add(TestRuntime.getSystemRuntime().asCf().getJavaExecutable().toString());
    cmdline.add("-jar");
    cmdline.add(ToolHelper.JACOCO_CLI.toString());
    cmdline.add("instrument");
    cmdline.add(sourceClassFiles.toString());
    cmdline.add("--dest");
    cmdline.add(outputDirectory.toString());
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }

  public static ProcessResult runJaCoCoReport(Path classfiles, Path jacocoExec, Path reportFile)
      throws IOException {
    List<String> cmdline = new ArrayList<>();
    cmdline.add(TestRuntime.getSystemRuntime().asCf().getJavaExecutable().toString());
    cmdline.add("-jar");
    cmdline.add(ToolHelper.JACOCO_CLI.toString());
    cmdline.add("report");
    cmdline.add(jacocoExec.toString());
    cmdline.add("--classfiles");
    cmdline.add(classfiles.toString());
    cmdline.add("--csv");
    cmdline.add(reportFile.toString());
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }

  private static Path findNonConflictingDestinationFilePath(Path testOutputPath) {
    int index = 0;
    Path destFilePath;
    do {
      destFilePath = Paths.get(testOutputPath.toString(),
          "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION);
      index++;
    } while (destFilePath.toFile().exists());

    return destFilePath;
  }

  private static Path getTestOutputPath(File destDir) throws IOException {
    assert destDir.exists();
    assert destDir.isDirectory();

    String testClassName = System.getProperty("test_class_name");
    String testName = System.getProperty("test_name");
    String headSha1 = System.getProperty("test_git_HEAD_sha1");

    assert testClassName != null;
    assert testName != null;
    assert headSha1 != null;

    return Files.createDirectories(
        Paths.get(
            destDir.getAbsolutePath(),
            headSha1,
            testClassName,
            testName + "-" + String.format("%03d", testOutputPathIndex)));
  }

  private static List<File> unzipDexFilesArchive(File zipFile) throws IOException {
    File tmpDir = Files.createTempDirectory("r8-test-").toFile();
    tmpDir.deleteOnExit();
    return ZipUtils.unzip(zipFile.getAbsolutePath(), tmpDir);
  }

  private static void storeAsGoldenFiles(List<File> files, File destDir) throws IOException {
    Path testOutputPath = getTestOutputPath(destDir);

    for (File f : files) {
      Path filePath = f.toPath();
      // TODO(jmhenaff): Check it's been produced by D8/R8?
      List<File> testFiles = Collections.singletonList(f);
      if (FileUtils.isArchive(filePath)) {
        testFiles = unzipDexFilesArchive(f);
      }
      for (File testFile : testFiles) {
        Path testFilePath = testFile.toPath();
        if (FileUtils.isDexFile(testFilePath)) {
          Path destFile = findNonConflictingDestinationFilePath(testOutputPath);
          FileUtils.writeToFile(destFile, null, Files.readAllBytes(testFilePath));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void storeProcessResult(ProcessResult processResult, File dest)
      throws IOException {
    Gson gson = new Gson();
    Path testOutputPath = getTestOutputPath(dest);
    try (FileWriter fw = new FileWriter(new File(testOutputPath.toFile(), "processResult.json"))) {
      gson.toJson(processResult, ProcessResult.class, fw);
    }
  }

  private static ProcessResult readProcessResult(File dest) throws IOException {
    File processResultFile = new File(getTestOutputPath(dest).toFile(), "processResult.json");
    Gson gson = new Gson();
    try (FileReader fr = new FileReader(processResultFile)) {
      return gson.fromJson(fr, ProcessResult.class);
    }
  }

  private static ProcessResult compareAgainstGoldenFiles(List<File> files, File destDir)
      throws IOException {
    Path testOutputPath = getTestOutputPath(destDir);

    int index = 0;
    String stdErr = "";
    boolean passed = true;
    for (File f : files) {
      Path filePath = f.toPath();

      List<File> testFiles = Collections.singletonList(f);
      if (FileUtils.isArchive(filePath)) {
        testFiles = unzipDexFilesArchive(f);
      }

      for (File testFile : testFiles) {
        Path testFilePath = testFile.toPath();
        // TODO(jmhenaff): Check it's been produced by D8/R8?
        if (FileUtils.isDexFile(testFilePath)) {
          File goldenFile = Paths.get(testOutputPath.toString(),
              "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION).toFile();
          if (!goldenFile.exists()) {
            String fileDesc = "'" + testFile.getAbsolutePath() + "'";
            if (FileUtils.isZipFile(filePath)) {
              fileDesc += " (extracted from '" + f.getAbsolutePath() + "')";
            }
            stdErr += "Cannot find golden file '" + goldenFile.getAbsolutePath()
                + "' to compare against test file " + fileDesc + "\n";
            passed = false;
          } else if (!com.google.common.io.Files.equal(testFile, goldenFile)) {
            String fileDesc = "'" + testFile.getAbsolutePath() + "'";
            if (FileUtils.isZipFile(filePath)) {
              fileDesc += " (extracted from '" + f.getAbsolutePath() + "')";
            }
            stdErr +=
                "File " + fileDesc + " differs from golden file '" + goldenFile.getAbsolutePath()
                    + "'\n";
            passed = false;
          }
          index++;
        }
      }
    }
    // Ensure we processed as many files as there are golden files
    File goldenFile = Paths.get(testOutputPath.toString(),
        "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION).toFile();
    if (goldenFile.exists()) {
      stdErr += "Less dex files have been produced: there is at least one more golden file ('"
          + goldenFile.getAbsolutePath() + "'\n";
      passed = false;
    }
    return new ProcessResult(passed ? 0 : -1, "", stdErr);
  }

  public static boolean dealsWithGoldenFiles() {
    return compareAgaintsGoldenFiles() || generateGoldenFiles();
  }

  public static boolean compareAgaintsGoldenFiles() {
    return System.getProperty("use_golden_files_in") != null;
  }

  public static boolean generateGoldenFiles() {
    return System.getProperty("generate_golden_files_to") != null;
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
    ProcessResult result = runArtRaw(files, mainClass, extras, version, false);
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

  protected static void failOnProcessFailure(ProcessResult result) {
    if (result.exitCode != 0) {
      fail("Unexpected failure: '" + result.stderr + "'\n" + result.stdout);
    }
  }

  protected static void failOnVerificationErrors(ProcessResult result) {
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  private static ProcessResult runArtProcessRaw(ArtCommandBuilder builder) throws IOException {
    Assume.assumeTrue(artSupported() || dealsWithGoldenFiles());
    ProcessResult cachedResult = builder.getCachedResults();
    if (cachedResult != null) {
      return cachedResult;
    }
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
    builder.cacheResult(result);
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
    runDex2Oat(file, outFile, getDexVm());
  }

  public static void runDex2Oat(Path file, Path outFile, DexVm vm) throws IOException {
    ProcessResult result = runDex2OatRaw(file, outFile, vm);
    if (result.exitCode != 0) {
      fail("dex2oat failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    if (result.stderr != null && result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  // Checked in VMs for which dex2oat should work specified in decreasing order.
  private static List<DexVm> SUPPORTED_DEX2OAT_VMS =
      ImmutableList.of(DexVm.ART_12_0_0_HOST, DexVm.ART_6_0_1_HOST);

  public static ProcessResult runDex2OatRaw(Path file, Path outFile, DexVm targetVm)
      throws IOException {
    Assume.assumeTrue(ToolHelper.isDex2OatSupported());
    assert Files.exists(file);
    assert ByteStreams.toByteArray(Files.newInputStream(file)).length > 0;
    assert SUPPORTED_DEX2OAT_VMS.stream()
        .sorted(Comparator.comparing(DexVm::getVersion).reversed())
        .collect(Collectors.toList())
        .equals(SUPPORTED_DEX2OAT_VMS);
    DexVersion requiredDexFileVersion = getDexFileVersionForVm(targetVm);
    DexVm vm = null;
    for (DexVm supported : SUPPORTED_DEX2OAT_VMS) {
      DexVersion supportedDexFileVersion = getDexFileVersionForVm(supported);
      // This and remaining VMs can't compile code at the required DEX version.
      if (supportedDexFileVersion.isLessThan(requiredDexFileVersion)) {
        break;
      }
      // Find the "oldest" supported VM. Only consider VMs older than targetVm if no VM matched.
      if (supported.isNewerThanOrEqual(targetVm) || vm == null) {
        vm = supported;
      }
    }
    if (vm == null) {
      throw new Unimplemented("Unable to find a supported dex2oat for VM " + vm);
    }
    List<String> command = new ArrayList<>();
    command.add(getDex2OatPath(vm).toAbsolutePath().toString());
    command.add("--android-root=" + getProductPath(vm).toAbsolutePath() + "/system");
    command.add("--runtime-arg");
    command.add("-verbose:verifier");
    command.add("--runtime-arg");
    command.add("-Xnorelocate");
    command.add("--dex-file=" + file.toAbsolutePath());
    command.add("--oat-file=" + outFile.toAbsolutePath());
    command.add("--instruction-set=" + getArchString(vm));
    if (vm.version.equals(DexVm.Version.V12_0_0)) {
      command.add(
          "--boot-image="
              + getDexVmPath(vm).toAbsolutePath()
              + "/apex/art_boot_images/javalib/boot.art");
    }
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.directory(getDexVmPath(vm).toFile());
    builder.environment().put("LD_LIBRARY_PATH", getDexVmLibPath(vm).toString());
    ProcessResult processResult = runProcess(builder);
    return processResult;
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

  public static ProcessResult runProguardRaw(
      Path inJar, Path outJar, Path lib, Path config, Path map) throws IOException {
    return runProguardRaw(getProguard5Script(), inJar, outJar, lib, ImmutableList.of(config), map);
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguardRaw(getProguard5Script(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguardRaw(getProguard5Script(), inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguard(inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguard(getProguard5Script(), inJar, outJar, config, map);
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

  public static ProcessResult runRetraceRaw(Path retracePath, Path map, Path stackTrace)
      throws IOException {
    List<String> command = new ArrayList<>();
    command.add(retracePath.toString());
    command.add(map.toString());
    command.add(stackTrace.toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    return ToolHelper.runProcess(builder);
  }

  public static String runRetrace(ProguardVersion pgRetracer, Path map, Path stackTrace)
      throws IOException {
    ProcessResult result = runRetraceRaw(pgRetracer.getRetraceScript(), map, stackTrace);
    if (result.exitCode != 0) {
      fail("Retrace failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return result.stdout;
  }

  public static class ProcessResult {

    public final int exitCode;
    public String stdout;
    public final String stderr;
    public final String command;

    public ProcessResult(int exitCode, String stdout, String stderr, String command) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
      this.command = command;
    }

    ProcessResult(int exitCode, String stdout, String stderr) {
      this(exitCode, stdout, stderr, null);
    }

    public void setStdout(String stdout) {
      this.stdout = stdout;
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
    return runProcess(builder, System.out);
  }

  public static ProcessResult runProcess(ProcessBuilder builder, PrintStream out)
      throws IOException {
    boolean printCwd = builder.directory() != null;
    if (printCwd) {
      out.println("(cd " + builder.directory() + "; ");
    }
    String command = String.join(" ", builder.command());
    out.println(command);
    if (printCwd) {
      out.println(")");
    }
    return drainProcessOutputStreams(builder.start(), command);
  }

  public static ProcessResult drainProcessOutputStreams(Process process, String command) {
    // Drain stdout and stderr so that the process does not block. Read stdout and stderr
    // in parallel to make sure that neither buffer can get filled up which will cause the
    // C program to block in a call to write.
    StreamReader stdoutReader = new StreamReader(process.getInputStream());
    StreamReader stderrReader = new StreamReader(process.getErrorStream());
    Thread stdoutThread = new Thread(stdoutReader);
    Thread stderrThread = new Thread(stderrReader);
    stdoutThread.start();
    stderrThread.start();
    try {
      process.waitFor();
      stdoutThread.join();
      stderrThread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Execution interrupted", e);
    }
    return new ProcessResult(
        process.exitValue(), stdoutReader.getResult(), stderrReader.getResult(), command);
  }

  public static R8Command.Builder addProguardConfigurationConsumer(
      R8Command.Builder builder, Consumer<ProguardConfiguration.Builder> consumer) {
    builder.addProguardConfigurationConsumerForTesting(consumer);
    return builder;
  }

  public static R8Command.Builder addSyntheticProguardRulesConsumerForTesting(
      R8Command.Builder builder, Consumer<List<ProguardConfigurationRule>> consumer) {
    builder.addSyntheticProguardRulesConsumerForTesting(consumer);
    return builder;
  }

  public static R8Command.Builder allowTestProguardOptions(R8Command.Builder builder) {
    builder.setEnableTestProguardOptions();
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
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
            if (isDexFile(file)) {
              builder.addProgramFile(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return builder;
  }

  public static void writeApplication(AppView<?> appView) throws ExecutionException {
    appView.options().tool = Tool.R8;
    R8.writeApplication(appView, null, Executors.newSingleThreadExecutor());
  }

  public static void disassemble(AndroidApp app, PrintStream ps) throws IOException {
    DexApplication application =
        new ApplicationReader(app, new InternalOptions(), Timing.empty()).read().toDirect();
    new AssemblyWriter(application, new InternalOptions(), true, false, true).write(ps);
  }

  public static Path getTestFolderForClass(Class<?> clazz) {
    return Paths.get(ToolHelper.TESTS_DIR)
        .resolve("java")
        .resolve(ToolHelper.getFileNameForTestClass(clazz))
        .getParent();
  }

  public static Collection<Path> getFilesInTestFolderRelativeToClass(
      Class<?> clazz, String folderName, String endsWith) throws IOException {
    Path subFolder = getTestFolderForClass(clazz).resolve(folderName);
    assert Files.isDirectory(subFolder);
    try (Stream<Path> walker = Files.walk(subFolder)) {
      return walker.filter(path -> path.toString().endsWith(endsWith)).collect(Collectors.toList());
    }
  }

  /** This code only works if run with depot_tools on the path */
  public static String uploadFileToGoogleCloudStorage(String bucket, Path file) throws IOException {
    ImmutableList.Builder<String> command =
        new ImmutableList.Builder<String>()
            .add("upload_to_google_storage.py")
            .add("-f")
            .add("--bucket")
            .add(bucket)
            .add(file.toAbsolutePath().toString());
    ProcessResult result = ToolHelper.runProcess(new ProcessBuilder(command.build()));
    if (result.exitCode != 0) {
      throw new RuntimeException(
          "Could not upload "
              + file
              + " to cloud storage:\n"
              + result.stdout
              + "\n"
              + result.stderr);
    }
    // Upload will add a sha1 file at the same location.
    Path sha1file = file.resolveSibling(file.getFileName() + ".sha1");
    assert Files.exists(sha1file) : sha1file.toString();
    List<String> strings = Files.readAllLines(sha1file);
    assert !strings.isEmpty();
    return strings.get(0);
  }
}
