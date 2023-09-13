// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MAX_SUPPORTED_VERSION;
import static com.android.tools.r8.ToolHelper.isWindows;
import static com.google.common.io.Files.getNameWithoutExtension;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.CacheLookupKey;
import com.android.tools.r8.ToolHelper.CommandResultCache;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.hash.Hasher;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.junit.rules.TemporaryFolder;

public class KotlinCompilerTool {

  public enum KotlinTargetVersion {
    NONE(""),
    JAVA_6("JAVA_6"),
    JAVA_8("JAVA_8");

    private final String folderName;

    KotlinTargetVersion(String folderName) {
      this.folderName = folderName;
    }

    public String getFolderName() {
      return folderName;
    }

    public String getJvmTargetString() {
      switch (this) {
        case JAVA_6:
          return "1.6";
        case JAVA_8:
          return "1.8";
        default:
          throw new Unimplemented("JvmTarget not specified for " + this);
      }
    }
  }

  public enum KotlinCompilerVersion implements Ordered<KotlinCompilerVersion> {
    KOTLINC_1_3_72("kotlin-compiler-1.3.72"),
    KOTLINC_1_4_20("kotlin-compiler-1.4.20"),
    KOTLINC_1_5_0("kotlin-compiler-1.5.0"),
    KOTLINC_1_6_0("kotlin-compiler-1.6.0"),
    KOTLINC_1_7_0("kotlin-compiler-1.7.0"),
    KOTLINC_1_8_0("kotlin-compiler-1.8.0"),
    KOTLIN_DEV("kotlin-compiler-dev");

    public static final KotlinCompilerVersion MIN_SUPPORTED_VERSION = KOTLINC_1_6_0;
    public static final KotlinCompilerVersion MAX_SUPPORTED_VERSION = KOTLINC_1_8_0;

    private final String folder;

    KotlinCompilerVersion(String folder) {
      this.folder = folder;
    }

    public static KotlinCompilerVersion latest() {
      return ArrayUtils.last(values());
    }

    public KotlinCompiler getCompiler() {
      return new KotlinCompiler(this);
    }

    public static List<KotlinCompilerVersion> getSupported() {
      return Arrays.stream(KotlinCompilerVersion.values())
          .filter(
              compiler ->
                  compiler.isGreaterThanOrEqualTo(MIN_SUPPORTED_VERSION)
                      && compiler.isLessThanOrEqualTo(MAX_SUPPORTED_VERSION))
          .collect(Collectors.toList());
    }
  }

  public static final class KotlinCompiler {

    private final String name;
    private final Path lib;
    private final Path compiler;
    private final KotlinCompilerVersion compilerVersion;

    public KotlinCompiler(KotlinCompilerVersion compilerVersion) {
      this.lib =
          Paths.get(ToolHelper.THIRD_PARTY_DIR, "kotlin", compilerVersion.folder, "kotlinc", "lib");
      this.compiler = lib.resolve("kotlin-compiler.jar");
      this.compilerVersion = compilerVersion;
      this.name = compilerVersion.name();
    }

    public KotlinCompiler(String name, Path compiler, KotlinCompilerVersion compilerVersion) {
      this.compiler = compiler;
      this.lib = null;
      this.compilerVersion = compilerVersion;
      this.name = name;
    }

    public static KotlinCompiler latest() {
      return MAX_SUPPORTED_VERSION.getCompiler();
    }

    public Path getCompiler() {
      return compiler;
    }

    public Path getFolder() {
      return lib;
    }

    public boolean is(KotlinCompilerVersion version) {
      return compilerVersion == version;
    }

    public boolean isOneOf(KotlinCompilerVersion... versions) {
      return Arrays.stream(versions).anyMatch(this::is);
    }

    public boolean isNot(KotlinCompilerVersion version) {
      return !is(version);
    }

    public KotlinCompilerVersion getCompilerVersion() {
      return compilerVersion;
    }

    public Path getKotlinStdlibJar() {
      Path stdLib = getFolder().resolve("kotlin-stdlib.jar");
      assert Files.exists(stdLib) : "Expected kotlin stdlib jar";
      return stdLib;
    }

    public Path getKotlinReflectJar() {
      Path reflectJar = getFolder().resolve("kotlin-reflect.jar");
      assert Files.exists(reflectJar) : "Expected kotlin reflect jar";
      return reflectJar;
    }

    public Path getKotlinScriptRuntime() {
      Path reflectJar = getFolder().resolve("kotlin-script-runtime.jar");
      assert Files.exists(reflectJar) : "Expected kotlin script runtime jar";
      return reflectJar;
    }

    public Path getKotlinAnnotationJar() {
      Path annotationJar = getFolder().resolve("annotations-13.0.jar");
      assert Files.exists(annotationJar) : "Expected annotation jar";
      return annotationJar;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  private final CfRuntime jdk;
  private final TestState state;
  private final KotlinCompiler compiler;
  private final KotlinTargetVersion targetVersion;
  private final List<Path> sources = new ArrayList<>();
  private final List<Path> classpath = new ArrayList<>();
  private final List<String> additionalArguments = new ArrayList<>();
  private boolean useJvmAssertions;
  // TODO(b/211590675): We should enable assertions by default.
  private boolean enableAssertions = true;
  private Path output = null;

  private KotlinCompilerTool(
      CfRuntime jdk, TestState state, KotlinCompiler compiler, KotlinTargetVersion targetVersion) {
    this.jdk = jdk;
    this.state = state;
    this.compiler = compiler;
    this.targetVersion = targetVersion;
  }

  public KotlinCompiler getCompiler() {
    return compiler;
  }

  public KotlinTargetVersion getTargetVersion() {
    return targetVersion;
  }

  public static KotlinCompilerTool create(
      CfRuntime jdk,
      TemporaryFolder temp,
      KotlinCompiler kotlinCompiler,
      KotlinTargetVersion kotlinTargetVersion) {
    return create(jdk, new TestState(temp), kotlinCompiler, kotlinTargetVersion);
  }

  public static KotlinCompilerTool create(
      CfRuntime jdk,
      TestState state,
      KotlinCompiler kotlinCompiler,
      KotlinTargetVersion kotlinTargetVersion) {
    return new KotlinCompilerTool(jdk, state, kotlinCompiler, kotlinTargetVersion);
  }

  public KotlinCompilerTool addArguments(String... arguments) {
    Collections.addAll(additionalArguments, arguments);
    return this;
  }

  public KotlinCompilerTool enableExperimentalContextReceivers() {
    return addArguments("-Xcontext-receivers");
  }

  public KotlinCompilerTool addSourceFiles(Path... files) {
    return addSourceFiles(Arrays.asList(files));
  }

  public KotlinCompilerTool addSourceFiles(Collection<Path> files) {
    sources.addAll(files);
    return this;
  }

  public KotlinCompilerTool addSourceFilesWithNonKtExtension(TemporaryFolder temp, Path... files) {
    return addSourceFilesWithNonKtExtension(temp, Arrays.asList(files));
  }

  public KotlinCompilerTool includeRuntime() {
    assert !additionalArguments.contains("-include-runtime");
    addArguments("-include-runtime");
    return this;
  }

  public KotlinCompilerTool noReflect() {
    assert !additionalArguments.contains("-no-reflect");
    addArguments("-no-reflect");
    return this;
  }

  public KotlinCompilerTool noStdLib() {
    assert !additionalArguments.contains("-no-stdlib");
    addArguments("-no-stdlib");
    return this;
  }

  public KotlinCompilerTool disableAssertions() {
    this.enableAssertions = false;
    return this;
  }

  public KotlinCompilerTool addSourceFilesWithNonKtExtension(
      TemporaryFolder temp, Collection<Path> files) {
    return addSourceFiles(
        files.stream()
            .map(
                fileNotNamedKt -> {
                  try {
                    // The Kotlin compiler does not require particular naming of files except for
                    // the extension, so just create a file called source.kt in a new directory.
                    String newFileName = getNameWithoutExtension(fileNotNamedKt.toString()) + ".kt";
                    Path fileNamedKt = temp.newFolder().toPath().resolve(newFileName);
                    Files.copy(fileNotNamedKt, fileNamedKt);
                    return fileNamedKt;
                  } catch (IOException e) {
                    throw new RuntimeException(e);
                  }
                })
            .collect(Collectors.toList()));
  }

  public KotlinCompilerTool addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public KotlinCompilerTool addClasspathFiles(Collection<Path> files) {
    classpath.addAll(files);
    return this;
  }

  public KotlinCompilerTool setOutputPath(Path file) {
    assertTrue("Output path must be an existing directory or a non-existing jar file",
        (!Files.exists(file) && FileUtils.isJarFile(file) && Files.exists(file.getParent()))
            || (Files.exists(file) && Files.isDirectory(file)));
    this.output = file;
    return this;
  }

  public KotlinCompilerTool setUseJvmAssertions(boolean useJvmAssertions) {
    this.useJvmAssertions = useJvmAssertions;
    return this;
  }

  public KotlinCompilerTool apply(Consumer<KotlinCompilerTool> consumer) {
    consumer.accept(this);
    return this;
  }

  private Path getOrCreateOutputPath() throws IOException {
    return output != null ? output : state.getNewTempFolder().resolve("out.jar");
  }

  /** Compile and return the compilations process result object. */
  public ProcessResult compileRaw() throws IOException {
    assertNotNull("An output path must be specified prior to compilation.", output);
    return compileInternal(output);
  }

  /** Compile asserting success and return the output path. */
  public Path compile() throws IOException {
    return compile(false);
  }

  public Path compile(boolean expectingFailure) throws IOException {
    Path output = getOrCreateOutputPath();
    ProcessResult result = compileInternal(output);
    if (expectingFailure) {
      assertNotEquals(result.toString(), result.exitCode, 0);
    } else {
      assertEquals(result.toString(), result.exitCode, 0);
    }
    return output;
  }

  private ProcessResult compileInternal(Path output) throws IOException {
    CommandLineAndHasherConsumers commandLineAndHasherConsumers =
        buildCommandLineAndHasherConsumers(output);
    CacheLookupKey cacheLookupKey = null;
    if (CommandResultCache.isEnabled()) {
      cacheLookupKey =
          new CacheLookupKey(
              hasher ->
                  commandLineAndHasherConsumers.hasherConsumers.forEach(
                      hasherConsumer -> hasherConsumer.acceptWithRuntimeException(hasher)));
      Pair<ProcessResult, Path> lookupResult =
          CommandResultCache.getInstance().lookup(cacheLookupKey);
      if (lookupResult != null
          && lookupResult.getFirst().exitCode == 0
          && lookupResult.getSecond() != null) {
        Files.copy(lookupResult.getSecond(), output);
        return lookupResult.getFirst();
      }
    }
    ProcessBuilder builder = new ProcessBuilder(commandLineAndHasherConsumers.cmdline);
    if (ToolHelper.isNewGradleSetup()) {
      builder.directory(new File(ToolHelper.getProjectRoot()));
    }
    ProcessResult processResult = ToolHelper.runProcess(builder);
    if (CommandResultCache.isEnabled() && output.toFile().isFile()) {
      CommandResultCache.getInstance().putResult(processResult, cacheLookupKey, output);
    }
    return processResult;
  }

  public static class CommandLineAndHasherConsumers {
    final List<String> cmdline = new ArrayList<>();
    final List<ThrowingConsumer<Hasher, IOException>> hasherConsumers = new ArrayList<>();
  }

  private CommandLineAndHasherConsumers buildCommandLineAndHasherConsumers(Path output)
      throws IOException {
    CommandLineAndHasherConsumers commandLineAndHasherConsumers =
        new CommandLineAndHasherConsumers();
    List<String> cmdline = commandLineAndHasherConsumers.cmdline;
    cmdline.add(jdk.getJavaExecutable().toString());
    if (enableAssertions) {
      cmdline.add("-ea");
    }
    cmdline.add("-cp");
    cmdline.add(compiler.getCompiler().toString());
    cmdline.add(ToolHelper.K2JVMCompiler);
    if (useJvmAssertions) {
      cmdline.add("-Xassertions=jvm");
    }
    cmdline.add("-jdk-home");
    cmdline.add(jdk.getJavaHome().toString());
    cmdline.add("-jvm-target");
    cmdline.add(targetVersion.getJvmTargetString());
    // Until now this is just command line files, no inputs, hash existing command
    String noneFileCommandLineArguments = StringUtils.join("", cmdline);
    commandLineAndHasherConsumers.hasherConsumers.add(
        hasher -> hasher.putString(noneFileCommandLineArguments, StandardCharsets.UTF_8));

    for (Path source : sources) {
      cmdline.add(source.toString());
      commandLineAndHasherConsumers.hasherConsumers.add(
          hasher -> hasher.putBytes(Files.readAllBytes(source)));
    }
    cmdline.add("-d");
    cmdline.add(output.toString());
    if (!classpath.isEmpty()) {
      cmdline.add("-cp");
      cmdline.add(classpath
          .stream()
          .map(Path::toString)
          .collect(Collectors.joining(isWindows() ? ";" : ":")));
      for (Path path : classpath) {
        commandLineAndHasherConsumers.hasherConsumers.add(
            hasher -> {
              hasher.putString("--cp", StandardCharsets.UTF_8);
              hasher.putBytes(Files.readAllBytes(path));
            });
      }
    }
    cmdline.addAll(additionalArguments);
    commandLineAndHasherConsumers.hasherConsumers.add(
        hasher -> additionalArguments.forEach(s -> hasher.putString(s, StandardCharsets.UTF_8)));
    return commandLineAndHasherConsumers;
  }


}
