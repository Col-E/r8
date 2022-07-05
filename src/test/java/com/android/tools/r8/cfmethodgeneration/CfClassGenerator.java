// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class CfClassGenerator extends CodeGenerationBase {

  public String generateClass() throws IOException {
    File temporaryFile = File.createTempFile("output-", ".java");
    generateRawOutput(temporaryFile.toPath());
    String result = formatRawOutput(temporaryFile.toPath());
    temporaryFile.deleteOnExit();
    return result;
  }

  private void generateRawOutput(Path temporaryFile) throws IOException {
    try (PrintStream printer = new PrintStream(Files.newOutputStream(temporaryFile))) {
      printer.print(getHeaderString());
      printer.println("public class " + getGeneratedClassName() + " {");
      printer.println("}");
    }
  }

  public void writeClassToFile() throws IOException {
    FileUtils.writeToFile(getGeneratedFile(), null, generateClass().getBytes());
  }
}
