// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cfmethodgeneration;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class CodeGenerationBase extends TestBase {

  private static final Path GOOGLE_FORMAT_DIR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "google", "google-java-format", "1.14.0");
  private static final Path GOOGLE_FORMAT_JAR =
      GOOGLE_FORMAT_DIR.resolve("google-java-format-1.14.0-all-deps.jar");

  protected final DexItemFactory factory = new DexItemFactory();

  public static String formatRawOutput(String rawOutput) throws IOException {
    File temporaryFile = File.createTempFile("output-", ".java");
    Files.write(temporaryFile.toPath(), rawOutput.getBytes());
    String result = formatRawOutput(temporaryFile.toPath());
    temporaryFile.deleteOnExit();
    return result;
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

  public String getGeneratedClassDescriptor() {
    return getGeneratedType().toDescriptorString();
  }

  public String getGeneratedClassName() {
    return getGeneratedType().getName();
  }

  public String getGeneratedClassPackageName() {
    return getGeneratedType().getPackageName();
  }

  public Path getGeneratedFile() {
    return Paths.get(ToolHelper.MAIN_SOURCE_DIR, getGeneratedType().getInternalName() + ".java");
  }

  protected abstract DexType getGeneratedType();

  public String getHeaderString() {
    String simpleName = getClass().getSimpleName();
    return getHeaderString(getYear(), simpleName)
        + StringUtils.lines("package " + getGeneratedClassPackageName() + ";");
  }

  public static String getHeaderString(int year, String simpleNameOfGenerator) {
    return StringUtils.lines(
        "// Copyright (c) " + year + ", the R8 project authors. Please see the AUTHORS file",
        "// for details. All rights reserved. Use of this source code is governed by a",
        "// BSD-style license that can be found in the LICENSE file.",
        "",
        "// ***********************************************************************************",
        "// GENERATED FILE. DO NOT EDIT! See " + simpleNameOfGenerator + ".java.",
        "// ***********************************************************************************",
        "");
  }

  protected static String getJavaExecutable() {
    return ToolHelper.getSystemJavaExecutable();
  }

  protected abstract int getYear();
}
