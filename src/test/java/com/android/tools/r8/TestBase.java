// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.SmaliWriter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.ProguardRuleParserException;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.PreloadedClassFileProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class TestBase {

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  /**
   * Write lines of text to a temporary file.
   */
  protected Path writeTextToTempFile(String... lines) throws IOException {
    Path file = temp.newFile().toPath();
    FileUtils.writeTextFile(file, lines);
    return file;
  }

  /**
   * Build an AndroidApp with the specified test classes.
   */
  protected static AndroidApp readClasses(Class... classes) throws IOException {
    return readClasses(Arrays.asList(classes));
  }

  /**
   * Build an AndroidApp with the specified test classes.
   */
  protected static AndroidApp readClasses(List<Class> classes) throws IOException {
    return readClasses(classes, Collections.emptyList());
  }

  /**
   * Build an AndroidApp with the specified test classes.
   */
  protected static AndroidApp readClasses(List<Class> programClasses, List<Class> libraryClasses)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class clazz : programClasses) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    if (!libraryClasses.isEmpty()) {
      PreloadedClassFileProvider.Builder libraryBuilder = PreloadedClassFileProvider.builder();
      for (Class clazz : libraryClasses) {
        Path file = ToolHelper.getClassFileForTestClass(clazz);
        libraryBuilder.addResource(DescriptorUtils.javaTypeToDescriptor(clazz.getCanonicalName()),
            Files.readAllBytes(file));
      }
      builder.addLibraryResourceProvider(libraryBuilder.build());
    }
    return builder.build();
  }

  /**
   * Create a temporary JAR file containing the specified test classes.
   */
  protected Path jarTestClasses(Class... classes) throws IOException {
    Path jar = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      for (Class clazz : classes) {
        try (FileInputStream in =
            new FileInputStream(ToolHelper.getClassFileForTestClass(clazz).toFile())) {
          out.putNextEntry(new ZipEntry(ToolHelper.getJarEntryForTestClass(clazz)));
          ByteStreams.copy(in, out);
          out.closeEntry();
        }
      }
    }
    return jar;
  }

  /**
   * Create a temporary JAR file containing all test classes in a package.
   */
  protected Path jarTestClassesInPackage(Package pkg) throws IOException {
    Path jar = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    String zipEntryPrefix = ToolHelper.getJarEntryForTestPackage(pkg) + "/";
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      for (Path file : ToolHelper.getClassFilesForTestPackage(pkg)) {
        try (FileInputStream in = new FileInputStream(file.toFile())) {
          out.putNextEntry(new ZipEntry(zipEntryPrefix + file.getFileName()));
          ByteStreams.copy(in, out);
          out.closeEntry();
        }
      }
    }
    return jar;
  }

  /**
   * Create a temporary JAR file containing the specified test classes.
   */
  protected Path jarTestClasses(List<Class> classes) throws IOException {
    return jarTestClasses(classes.toArray(new Class[classes.size()]));
  }

  /**
   * Read the names of classes in a jar.
   */
  protected Set<String> readClassesInJar(Path jar) throws IOException {
    Set<String> result = new HashSet<>();
    try (ZipFile zipFile = new ZipFile(jar.toFile())) {
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        if (name.endsWith(".class")) {
          result.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
        }
      }
    }
    return result;
  }


  /**
   * Get the class name generated by javac.
   */
  protected String getJavacGeneratedClassName(Class clazz) {
    List<String> parts = Lists.newArrayList(clazz.getCanonicalName().split("\\."));
    Class enclosing = clazz;
    while (enclosing.getEnclosingClass() != null) {
      parts.set(parts.size() - 2, parts.get(parts.size() - 2) + "$" + parts.get(parts.size() - 1));
      parts.remove(parts.size() - 1);
      enclosing = clazz.getEnclosingClass();
    }
    return String.join(".", parts);
  }

  /**
   * Compile an application with D8.
   */
  protected AndroidApp compileWithD8(AndroidApp app)
      throws CompilationException, ExecutionException, IOException, CompilationFailedException {
    D8Command.Builder builder = ToolHelper.prepareD8CommandBuilder(app);
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    return appSink.build();
  }

  /**
   * Compile an application with D8.
   */
  protected AndroidApp compileWithD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ExecutionException, IOException, CompilationFailedException {
    return ToolHelper.runD8(app, optionsConsumer);
  }

  /**
   * Compile an application with R8.
   */
  protected AndroidApp compileWithR8(Class... classes)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    return ToolHelper.runR8(readClasses(classes));
  }

  /**
   * Compile an application with R8.
   */
  protected AndroidApp compileWithR8(List<Class> classes)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command);
  }

  /**
   * Compile an application with R8.
   */
  protected AndroidApp compileWithR8(List<Class> classes, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /**
   * Compile an application with R8.
   */
  protected AndroidApp compileWithR8(AndroidApp app)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app).build();
    return ToolHelper.runR8(command);
  }

  /**
   * Compile an application with R8.
   */
  protected AndroidApp compileWithR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app).build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(List<Class> classes, String proguardConfig)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(
      List<Class> classes, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig, optionsConsumer);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(List<Class> classes, Path proguardConfig)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(AndroidApp app, Path proguardConfig)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfigurationFiles(proguardConfig)
            .build();
    return ToolHelper.runR8(command);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(AndroidApp app, String proguardConfig)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    return compileWithR8(app, proguardConfig, null);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(
      AndroidApp app, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown())
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /**
   * Compile an application with R8 using the supplied proguard configuration.
   */
  protected AndroidApp compileWithR8(
      AndroidApp app, Path proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws CompilationException, ProguardRuleParserException, ExecutionException, IOException,
      CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfigurationFiles(proguardConfig)
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class.
   */
  public static String keepMainProguardConfiguration(Class clazz) {
    return keepMainProguardConfiguration(clazz.getCanonicalName());
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class.
   */
  public static String keepMainProguardConfiguration(String clazz) {
    return "-keep public class " + clazz + " {\n"
        + "  public static void main(java.lang.String[]);\n"
        + "}\n"
        + "-printmapping\n";
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class and specify if -allowaccessmodification and -dontobfuscate are added as
   * well.
   */
  public static String keepMainProguardConfiguration(
      Class clazz, boolean allowaccessmodification, boolean obfuscate) {
    return keepMainProguardConfiguration(clazz)
        + (allowaccessmodification ? "-allowaccessmodification\n" : "")
        + (obfuscate ? "-printmapping\n" : "-dontobfuscate\n");
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass) throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtRaw(ImmutableList.of(out.toString()), mainClass, null);
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, Class mainClass) throws IOException {
    return runOnArtRaw(app, mainClass.getCanonicalName());
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected String runOnArt(AndroidApp app, String mainClass) throws IOException {
    return runOnArtRaw(app, mainClass).stdout;
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected String runOnArt(AndroidApp app, Class mainClass) throws IOException {
    return runOnArtRaw(app, mainClass).stdout;
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, Class mainClass, String... args) throws IOException {
    return runOnArt(app, mainClass, Arrays.asList(args));
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, String mainClass, List<String> args) throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtNoVerificationErrors(
        ImmutableList.of(out.toString()), mainClass,
        builder -> {
          builder.appendArtOption("-ea");
          for (String arg : args) {
            builder.appendProgramArgument(arg);
          }
        });
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, Class mainClass, List<String> args) throws IOException {
    return runOnArt(app, mainClass.getCanonicalName(), args);
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, String mainClass, String... args) throws IOException {
    return runOnArt(app, mainClass, Arrays.asList(args));
  }

  /**
   * Run a single class application on Java.
   */
  protected String runOnJava(Class mainClass) throws Exception {
    ProcessResult result = ToolHelper.runJava(mainClass);
    if (result.exitCode != 0) {
      System.out.println("Std out:");
      System.out.println(result.stdout);
      System.out.println("Std err:");
      System.out.println(result.stderr);
      assertEquals(0, result.exitCode);
    }
    return result.stdout;
  }


  /**
   * Disassemble the content of an application. Only works for an application with only dex code.
   */
  protected void disassemble(AndroidApp app) throws Exception {
    InternalOptions options = new InternalOptions();
    System.out.println(SmaliWriter.smali(app, options));
  }

  protected DexEncodedMethod getMethod(
      DexInspector inspector,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    ClassSubject clazz = inspector.clazz(className);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(returnType, methodName, parameters);
    assertTrue(method.isPresent());
    return method.getMethod();
  }

  protected DexEncodedMethod getMethod(
      AndroidApp application,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    try {
      DexInspector inspector = new DexInspector(application);
      return getMethod(inspector, className, returnType, methodName, parameters);
    } catch (Exception e) {
      return null;
    }
  }

  public enum MinifyMode {
    NONE,
    JAVA,
    AGGRESSIVE;

    public boolean isMinify() {
      return this != NONE;
    }
  }
}
