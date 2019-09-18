// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.backports;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

// Class to generate and validate CfCode for backport methods.
@RunWith(Parameterized.class)
public class GenerateBackportMethods extends TestBase {

  static final Path javaExecutable = Paths.get(ToolHelper.getJavaExecutable(CfVm.JDK9));
  static final Path googleFormatDir = Paths.get(ToolHelper.THIRD_PARTY_DIR, "google-java-format");
  static final Path googleFormatJar =
      googleFormatDir.resolve("google-java-format-1.7-all-deps.jar");
  static final Path backportMethodsFile =
      Paths.get(
          ToolHelper.SOURCE_DIR,
          "com",
          "android",
          "tools",
          "r8",
          "ir",
          "desugar",
          "backports",
          "BackportedMethods.java");

  static final String header =
      StringUtils.lines(
          "// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file",
          "// for details. All rights reserved. Use of this source code is governed by a",
          "// BSD-style license that can be found in the LICENSE file.",
          "",
          "// ***********************************************************************************",
          "// GENERATED FILE. DO NOT EDIT! Changes should be made to GenerateBackportMethods.java",
          "// ***********************************************************************************",
          "",
          "package com.android.tools.r8.ir.desugar.backports;");

  static final List<Class<?>> methodTemplateClasses =
      ImmutableList.of(
          BooleanMethods.class,
          ByteMethods.class,
          CharacterMethods.class,
          CloseResourceMethod.class,
          CollectionsMethods.class,
          DoubleMethods.class,
          FloatMethods.class,
          IntegerMethods.class,
          ListMethods.class,
          LongMethods.class,
          MathMethods.class,
          ObjectsMethods.class,
          OptionalMethods.class,
          ShortMethods.class,
          StringMethods.class);

  final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenerateBackportMethods(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    ArrayList<Class<?>> sorted = new ArrayList<>(methodTemplateClasses);
    sorted.sort((a, b) -> a.getTypeName().compareTo(b.getTypeName()));
    assertEquals("Classes should be listed in sorted order", sorted, methodTemplateClasses);
    assertEquals(
        FileUtils.readTextFile(backportMethodsFile, StandardCharsets.UTF_8),
        generateBackportMethods());
  }

  // Running this method will regenerate / overwrite the content of the backport methods.
  public static void main(String[] args) throws Exception {
    FileUtils.writeToFile(backportMethodsFile, null, generateBackportMethods().getBytes());
  }

  private static String generateBackportMethods() throws IOException {
    InternalOptions options = new InternalOptions();
    CfCodePrinter codePrinter = new CfCodePrinter();
    JarClassFileReader reader =
        new JarClassFileReader(
            new JarApplicationReader(options),
            clazz -> {
              for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                if (method.isInitializer()) {
                  continue;
                }
                String methodName =
                    method.method.holder.getName() + "_" + method.method.name.toString();
                codePrinter.visitMethod(methodName, method.getCode().asCfCode());
              }
            });
    for (Class<?> clazz : methodTemplateClasses) {
      try (InputStream stream = Files.newInputStream(ToolHelper.getClassFileForTestClass(clazz))) {
        reader.read(Origin.unknown(), ClassKind.PROGRAM, stream);
      }
    }

    Path outfile = Paths.get(ToolHelper.BUILD_DIR, "backports.java");
    try (PrintStream printer = new PrintStream(Files.newOutputStream(outfile))) {
      printer.println(header);
      codePrinter.getImports().forEach(i -> printer.println("import " + i + ";"));
      printer.println("public final class BackportedMethods {\n");
      codePrinter.getMethods().forEach(printer::println);
      printer.println("}");
    }

    ProcessBuilder builder =
        new ProcessBuilder(
            ImmutableList.of(
                javaExecutable.toString(),
                "-jar",
                googleFormatJar.toString(),
                outfile.toAbsolutePath().toString()));
    String commandString = String.join(" ", builder.command());
    System.out.println(commandString);
    Process process = builder.start();
    ProcessResult result = ToolHelper.drainProcessOutputStreams(process, commandString);
    if (result.exitCode != 0) {
      throw new IllegalStateException(result.toString());
    }
    String content = result.stdout;
    if (!StringUtils.LINE_SEPARATOR.equals("\n")) {
      return content.replace(StringUtils.LINE_SEPARATOR, "\n");
    }
    return content;
  }
}
