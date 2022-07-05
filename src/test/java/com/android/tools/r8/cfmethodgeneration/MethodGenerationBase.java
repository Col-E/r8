// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.CfCodePrinter;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.JarClassFileReader;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;

public abstract class MethodGenerationBase extends CodeGenerationBase {

  protected abstract List<Class<?>> getMethodTemplateClasses();

  protected CfCode getCode(String holderName, String methodName, CfCode code) {
    return code;
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
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.testing.readInputStackMaps = true;
    JarClassFileReader<DexProgramClass> reader =
        new JarClassFileReader<>(
            new JarApplicationReader(options),
            clazz -> {
              for (DexEncodedMethod method : clazz.allMethodsSorted()) {
                if (method.isInitializer()) {
                  continue;
                }
                String holderName = method.getHolderType().getName();
                String methodName = method.getReference().name.toString();
                String generatedMethodName = holderName + "_" + methodName;
                CfCode code = getCode(holderName, methodName, method.getCode().asCfCode());
                if (code != null) {
                  codePrinter.visitMethod(generatedMethodName, code);
                }
              }
            },
            ClassKind.PROGRAM);
    for (Class<?> clazz : getMethodTemplateClasses()) {
      reader.read(Origin.unknown(), ToolHelper.getClassAsBytes(clazz));
    }
  }

  private void generateRawOutput(CfCodePrinter codePrinter, Path tempFile) throws IOException {
    try (PrintStream printer = new PrintStream(Files.newOutputStream(tempFile))) {
      printer.print(getHeaderString());
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
}
