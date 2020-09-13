// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.List;
import java.util.TreeSet;

public abstract class MethodGenerationBase extends TestBase {

  private static final Path GOOGLE_FORMAT_DIR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "google-java-format");
  private static final Path GOOGLE_FORMAT_JAR =
      GOOGLE_FORMAT_DIR.resolve("google-java-format-1.7-all-deps.jar");

  protected final DexItemFactory factory = new DexItemFactory();

  protected static String getJavaExecutable() {
    return ToolHelper.getSystemJavaExecutable();
  }

  protected abstract DexType getGeneratedType();

  protected abstract List<Class<?>> getMethodTemplateClasses();

  public static String getHeaderString(Class<?> generationClass, String generatedPackage) {
    int year = Calendar.getInstance().get(Calendar.YEAR);
    String simpleName = generationClass.getSimpleName();
    return StringUtils.lines(
        "// Copyright (c) " + year + ", the R8 project authors. Please see the AUTHORS file",
        "// for details. All rights reserved. Use of this source code is governed by a",
        "// BSD-style license that can be found in the LICENSE file.",
        "",
        "// ***********************************************************************************",
        "// GENERATED FILE. DO NOT EDIT! See " + simpleName + ".java.",
        "// ***********************************************************************************",
        "",
        "package " + generatedPackage + ";");
  }

  protected Path getGeneratedFile() {
    return Paths.get(ToolHelper.SOURCE_DIR, getGeneratedType().getInternalName() + ".java");
  }

  private String getGeneratedClassName() {
    return getGeneratedType().getName();
  }

  private String getGeneratedClassPackageName() {
    return getGeneratedType().getPackageName();
  }

  // Running this method will regenerate / overwrite the content of the generated class.
  protected void generateMethodsAndWriteThemToFile() throws IOException {
    FileUtils.writeToFile(getGeneratedFile(), null, generateMethods().getBytes());
  }

  // Running this method generate the content of the generated class but does not overwrite it.
  protected String generateMethods() throws IOException {
    CfCodePrinter codePrinter = new CfCodePrinter();

    File tempFile = File.createTempFile("output-", ".java");

    readMethodTemplatesInto(codePrinter);
    generateRawOutput(codePrinter, tempFile.toPath());
    String result = formatRawOutput(tempFile.toPath());

    tempFile.deleteOnExit();
    return result;
  }

  private void readMethodTemplatesInto(CfCodePrinter codePrinter) throws IOException {
    InternalOptions options = new InternalOptions();
    JarClassFileReader reader =
        new JarClassFileReader(
            new JarApplicationReader(options),
            clazz -> {
              for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                if (method.isInitializer()) {
                  continue;
                }
                String methodName = method.holder().getName() + "_" + method.method.name.toString();
                codePrinter.visitMethod(methodName, method.getCode().asCfCode());
              }
            });
    for (Class<?> clazz : getMethodTemplateClasses()) {
      reader.read(Origin.unknown(), ClassKind.PROGRAM, ToolHelper.getClassAsBytes(clazz));
    }
  }

  private void generateRawOutput(CfCodePrinter codePrinter, Path tempFile) throws IOException {
    try (PrintStream printer = new PrintStream(Files.newOutputStream(tempFile))) {
      printer.print(getHeaderString(this.getClass(), getGeneratedClassPackageName()));
      printer.println("import com.android.tools.r8.graph.DexItemFactory;");
      codePrinter.getImports().forEach(i -> printer.println("import " + i + ";"));
      printer.println("public final class " + getGeneratedClassName() + " {\n");
      printer.println(
          "public static void registerSynthesizedCodeReferences(DexItemFactory factory) {");
      for (String type : new TreeSet<>(codePrinter.getSynthesizedTypes())) {
        printer.println("factory.createSynthesizedType(\"" + type + "\");");
      }
      printer.println("}");
      codePrinter.getMethods().forEach(printer::println);
      printer.println("}");
    }
  }

  public static String formatRawOutput(Path tempFile) throws IOException {
    // Apply google format.
    ProcessBuilder builder =
        new ProcessBuilder(
            ImmutableList.of(
                getJavaExecutable(),
                "-jar",
                GOOGLE_FORMAT_JAR.toString(),
                tempFile.toAbsolutePath().toString()));
    String commandString = String.join(" ", builder.command());
    System.out.println(commandString);
    Process process = builder.start();
    ProcessResult result = ToolHelper.drainProcessOutputStreams(process, commandString);
    if (result.exitCode != 0) {
      throw new IllegalStateException(result.toString());
    }
    // Fix line separators.
    String content = result.stdout;
    if (!StringUtils.LINE_SEPARATOR.equals("\n")) {
      return content.replace(StringUtils.LINE_SEPARATOR, "\n");
    }
    return content;
  }
}
