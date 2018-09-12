// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.ASM6;

import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.SmaliWriter;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.PreloadedClassFileProvider;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class TestBase {
  public enum Backend {
    CF,
    DEX
  };

  // Actually running Proguard should only be during development.
  private static final boolean RUN_PROGUARD = System.getProperty("run_proguard") != null;
  // Actually running r8.jar in a forked process.
  private static final boolean RUN_R8_JAR = System.getProperty("run_r8_jar") != null;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  /**
   * Check if tests should also run Proguard when applicable.
   */
  protected boolean isRunProguard() {
    return RUN_PROGUARD;
  }

  /**
   * Check if tests should run R8 in a forked process when applicable.
   */
  protected boolean isRunR8Jar() {
    return RUN_R8_JAR;
  }

  /**
   * Write lines of text to a temporary file.
   */
  protected Path writeTextToTempFile(String... lines) throws IOException {
    return writeTextToTempFile(System.lineSeparator(), Arrays.asList(lines));
  }

  /**
   * Write lines of text to a temporary file, along with the specified line separator.
   */
  protected Path writeTextToTempFile(String lineSeparator, List<String> lines)
      throws IOException {
    Path file = temp.newFile().toPath();
    String contents = String.join(lineSeparator, lines) + lineSeparator;
    Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
    return file;
  }

  /**
   * Build an AndroidApp with the specified test classes as byte array.
   */
  protected AndroidApp buildAndroidApp(byte[]... classes) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (byte[] clazz : classes) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.N.getLevel()));
    return builder.build();
  }

  /**
   * Build an AndroidApp with the specified jar.
   */
  protected AndroidApp readJar(Path jar) {
    return AndroidApp.builder()
        .addProgramResourceProvider(ArchiveProgramResourceProvider.fromArchive(jar))
        .build();
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

  protected static AndroidApp readClassesAndRuntimeJar(List<Class> programClasses, Backend backend)
      throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class clazz : programClasses) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    if (backend == Backend.DEX) {
      AndroidApiLevel androidLibrary = ToolHelper.getMinApiLevelForDexVm();
      builder.addLibraryFiles(ToolHelper.getAndroidJar(androidLibrary));
    } else {
      assert backend == Backend.CF;
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    return builder.build();
  }

  /** Build an AndroidApp from the specified program files. */
  protected AndroidApp readProgramFiles(Path... programFiles) throws IOException {
    return AndroidApp.builder().addProgramFiles(programFiles).build();
  }

  /**
   * Copy test classes to the specified directory.
   */
  protected void copyTestClasses(Path dest, Class... classes) throws IOException {
    for (Class clazz : classes) {
      Path path = dest.resolve(clazz.getCanonicalName().replace('.', '/') + ".class");
      Files.createDirectories(path.getParent());
      Files.copy(ToolHelper.getClassFileForTestClass(clazz), path);
    }
  }

  /**
   * Create a temporary JAR file containing the specified test classes.
   */
  protected Path jarTestClasses(Class... classes) throws IOException {
    return jarTestClasses(Arrays.asList(classes), null);
  }

  /** Create a temporary JAR file containing the specified test classes and data resources. */
  protected Path jarTestClasses(Iterable<Class> classes, List<DataEntryResource> dataResources)
      throws IOException {
    Path jar = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      addTestClassesToJar(out, classes);
      if (dataResources != null) {
        addDataResourcesToJar(out, dataResources);
      }
    }
    return jar;
  }

  /** Create a temporary JAR file containing the specified test classes. */
  protected void addTestClassesToJar(JarOutputStream out, Iterable<Class> classes)
      throws IOException {
    for (Class clazz : classes) {
      try (FileInputStream in =
          new FileInputStream(ToolHelper.getClassFileForTestClass(clazz).toFile())) {
        out.putNextEntry(new ZipEntry(ToolHelper.getJarEntryForTestClass(clazz)));
        ByteStreams.copy(in, out);
        out.closeEntry();
      }
    }
  }

  /** Create a temporary JAR file containing the specified data resources. */
  protected void addDataResourcesToJar(JarOutputStream out, List<DataEntryResource> dataResources)
      throws IOException {
    try {
      for (DataEntryResource dataResource : dataResources) {
        out.putNextEntry(new ZipEntry(dataResource.getName()));
        ByteStreams.copy(dataResource.getByteStream(), out);
        out.closeEntry();
      }
    } catch (ResourceException e) {
      throw new IOException("Resource error", e);
    }
  }

  /**
   * Creates a new, temporary JAR that contains all the entries from the given JAR as well as the
   * specified data resources. The given JAR is left unchanged.
   */
  protected Path addDataResourcesToExistingJar(
      Path existingJar, List<DataEntryResource> dataResources) throws IOException {
    Path newJar = File.createTempFile("app", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(newJar.toFile()))) {
      ArchiveProgramResourceProvider.fromArchive(existingJar)
          .readArchive(
              (entry, stream) -> {
                out.putNextEntry(new ZipEntry(entry.getEntryName()));
                ByteStreams.copy(stream, out);
                out.closeEntry();
              });
      addDataResourcesToJar(out, dataResources);
    }
    return newJar;
  }

  /** Returns a list containing all the data resources in the given app. */
  public static List<DataEntryResource> getDataResources(AndroidApp app) throws ResourceException {
    List<DataEntryResource> dataResources = new ArrayList<>();
    for (ProgramResourceProvider programResourceProvider : app.getProgramResourceProviders()) {
      dataResources.addAll(getDataResources(programResourceProvider.getDataResourceProvider()));
    }
    return dataResources;
  }

  public static List<DataEntryResource> getDataResources(DataResourceProvider dataResourceProvider)
      throws ResourceException {
    List<DataEntryResource> dataResources = new ArrayList<>();
    if (dataResourceProvider != null) {
      dataResourceProvider.accept(
          new Visitor() {
            @Override
            public void visit(DataDirectoryResource directory) {}

            @Override
            public void visit(DataEntryResource file) {
              dataResources.add(file);
            }
          });
    }
    return dataResources;
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
   * Get the class name generated by javac.
   */
  protected static String getJavacGeneratedClassName(Class clazz) {
    List<String> parts = Lists.newArrayList(clazz.getCanonicalName().split("\\."));
    Class enclosing = clazz;
    while (enclosing.getEnclosingClass() != null) {
      parts.set(parts.size() - 2, parts.get(parts.size() - 2) + "$" + parts.get(parts.size() - 1));
      parts.remove(parts.size() - 1);
      enclosing = clazz.getEnclosingClass();
    }
    return String.join(".", parts);
  }

  /** Compile an application with D8. */
  protected AndroidApp compileWithD8(AndroidApp app) throws CompilationFailedException {
    D8Command.Builder builder = ToolHelper.prepareD8CommandBuilder(app);
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    return appSink.build();
  }

  /** Compile an application with D8. */
  protected AndroidApp compileWithD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    return ToolHelper.runD8(app, optionsConsumer);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(Class... classes) throws IOException {
    return ToolHelper.runR8(readClasses(classes));
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(List<Class> classes)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(List<Class> classes, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(AndroidApp app)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app).build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app).build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(List<Class> classes, String proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      List<Class> classes, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(List<Class> classes, Path proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(AndroidApp app, Path proguardConfig)
      throws IOException, CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfigurationFiles(proguardConfig)
            .build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(AndroidApp app, String proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(app, proguardConfig, null);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      AndroidApp app, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    return compileWithR8(app, proguardConfig, optionsConsumer, Backend.DEX);
  }

  /** Compile an application with R8 using the supplied proguard configuration and backend. */
  protected AndroidApp compileWithR8(
      AndroidApp app,
      String proguardConfig,
      Consumer<InternalOptions> optionsConsumer,
      Backend backend)
      throws IOException, CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend))
            .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown())
            .addLibraryFiles(runtimeJar(backend))
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      AndroidApp app, Path proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
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
    return keepMainProguardConfiguration(clazz, ImmutableList.of());
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class.
   */
  public static String keepMainProguardConfiguration(Class clazz, List<String> additionalLines) {
    String modifier = (clazz.getModifiers() & Modifier.PUBLIC) == Modifier.PUBLIC ? "public " : "";
    return String.join(System.lineSeparator(),
        Iterables.concat(ImmutableList.of(
            "-keep " + modifier + "class " + getJavacGeneratedClassName(clazz) + " {",
            "  public static void main(java.lang.String[]);",
            "}",
            "-printmapping"),
            additionalLines));
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class.
   *
   * The class is assumed to be public.
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

  public static String keepMainProguardConfiguration(
      String clazz, boolean allowaccessmodification, boolean obfuscate) {
    return keepMainProguardConfiguration(clazz)
        + (allowaccessmodification ? "-allowaccessmodification\n" : "")
        + (obfuscate ? "-printmapping\n" : "-dontobfuscate\n");
  }

  /**
   * Run application on the specified version of Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass,
      Consumer<ArtCommandBuilder> cmdBuilder, DexVm version) throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtRaw(ImmutableList.of(out.toString()), mainClass, cmdBuilder, version);
  }

  /**
   * Run application on the specified version of Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass, DexVm version)
      throws IOException {
    return runOnArtRaw(app, mainClass, null, version);
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass) throws IOException {
    return runOnArtRaw(app, mainClass, null);
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, Class mainClass) throws IOException {
    return runOnArtRaw(app, mainClass.getCanonicalName());
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
  protected String runOnArt(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    return runOnArt(app, mainClass, args, null);
  }

  /**
   * Run application on Art with the specified main class, provided arguments, and specified VM
   * version.
   */
  protected String runOnArt(AndroidApp app, String mainClass, List<String> args, DexVm dexVm)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtNoVerificationErrors(
        ImmutableList.of(out.toString()), mainClass,
        builder -> {
          builder.appendArtOption("-ea");
          for (String arg : args) {
            builder.appendProgramArgument(arg);
          }
        },
        dexVm);
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

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, Class mainClass, String... args) throws IOException {
    return runOnJava(app, mainClass, Arrays.asList(args));
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, Class mainClass, List<String> args)
      throws IOException {
    return runOnJava(app, mainClass.getCanonicalName(), args);
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    return runOnJavaRaw(app, mainClass, args).stdout;
  }

  protected ProcessResult runOnJavaRawNoVerify(String main, byte[]... classes) throws IOException {
    return runOnJavaRawNoVerify(main, Arrays.asList(classes), Collections.emptyList());
  }

  protected ProcessResult runOnJavaRawNoVerify(String main, List<byte[]> classes, List<String> args)
      throws IOException {
    return ToolHelper.runJavaNoVerify(Collections.singletonList(writeToJar(classes)), main, args);
  }

  protected ProcessResult runOnJavaRaw(String main, byte[]... classes) throws IOException {
    return runOnJavaRaw(main, Arrays.asList(classes), Collections.emptyList());
  }

  protected ProcessResult runOnJavaRaw(String main, List<byte[]> classes, List<String> args)
      throws IOException {
    List<String> mainAndArgs = new ArrayList<>();
    mainAndArgs.add(main);
    mainAndArgs.addAll(args);
    return ToolHelper.runJava(
        Collections.singletonList(writeToJar(classes)), mainAndArgs.toArray(new String[0]));
  }

  protected ProcessResult runOnJavaRaw(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.ClassFile);
    List<String> mainAndArgs = new ArrayList<>();
    mainAndArgs.add(mainClass);
    mainAndArgs.addAll(args);
    return ToolHelper.runJava(out, mainAndArgs.toArray(new String[0]));
  }

  protected ProcessResult runOnJavaRawNoVerify(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.ClassFile);
    return ToolHelper.runJavaNoVerify(out, mainClass, args.toArray(new String[0]));
  }

  private String extractClassName(byte[] ccc) {
    class ClassNameExtractor extends ClassVisitor {
      private String className;

      private ClassNameExtractor() {
        super(ASM6);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        className =
            name.replace(
                DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR,
                DescriptorUtils.JAVA_PACKAGE_SEPARATOR);
      }

      String getClassName() {
        return className;
      }
    }

    ClassReader reader = new ClassReader(ccc);
    ClassNameExtractor extractor = new ClassNameExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassName();
  }

  protected Path writeToJar(List<byte[]> classes) throws IOException {
    Path result = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    try (ZipOutputStream out =
        new ZipOutputStream(
            Files.newOutputStream(
                result, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
      for (byte[] clazz : classes) {
        String name = extractClassName(clazz);
        ZipUtils.writeToZipStream(
            out, DescriptorUtils.getPathFromJavaType(name), clazz, ZipEntry.STORED);
      }
    }
    return result;
  }

  protected Path writeToJar(JasminBuilder jasminBuilder) throws Exception {
    return writeToJar(jasminBuilder.buildClasses());
  }

  /**
   * Disassemble the content of an application. Only works for an application with only dex code.
   */
  protected void disassemble(AndroidApp app) throws Exception {
    InternalOptions options = new InternalOptions();
    System.out.println(SmaliWriter.smali(app, options));
  }

  protected DexEncodedMethod getMethod(
      CodeInspector inspector,
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
      CodeInspector inspector = new CodeInspector(application);
      return getMethod(inspector, className, returnType, methodName, parameters);
    } catch (Exception e) {
      return null;
    }
  }

  protected static void checkInstructions(
      DexCode code, List<Class<? extends Instruction>> instructions) {
    assertEquals(instructions.size(), code.instructions.length);
    for (int i = 0; i < instructions.size(); ++i) {
      assertEquals("Unexpected instruction at index " + i,
          instructions.get(i), code.instructions[i].getClass());
    }
  }

  protected Stream<Instruction> filterInstructionKind(
      DexCode dexCode, Class<? extends Instruction> kind) {
    return Arrays.stream(dexCode.instructions)
        .filter(kind::isInstance)
        .map(kind::cast);
  }

  public enum MinifyMode {
    NONE,
    JAVA,
    AGGRESSIVE;

    public boolean isMinify() {
      return this != NONE;
    }
  }

  public static ProgramConsumer emptyConsumer(Backend backend) {
    if (backend == Backend.DEX) {
      return DexIndexedConsumer.emptyConsumer();
    } else {
      assert backend == Backend.CF;
      return ClassFileConsumer.emptyConsumer();
    }
  }

  public static OutputMode outputMode(Backend backend) {
    if (backend == Backend.DEX) {
      return OutputMode.DexIndexed;
    } else {
      assert backend == Backend.CF;
      return OutputMode.ClassFile;
    }
  }

  public static Path runtimeJar(Backend backend) {
    if (backend == Backend.DEX) {
      return ToolHelper.getDefaultAndroidJar();
    } else {
      assert backend == Backend.CF;
      return ToolHelper.getJava8RuntimeJar();
    }
  }
}
