// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jacoco;

import static com.android.tools.r8.utils.DescriptorUtils.JAVA_PACKAGE_SEPARATOR;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.ZipUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.rules.TemporaryFolder;

// Two sets of class files with and without JaCoCo offline instrumentation.
public class JacocoClasses {

  private final TemporaryFolder temp;
  private final Path dir;

  private final Path originalJar;
  private final Path instrumentedJar;

  // Create JacocoClasses with just one class provided as bytes.
  public JacocoClasses(byte[] clazz, TemporaryFolder temp) throws IOException {
    this.temp = temp;
    dir = temp.newFolder().toPath();

    // Write the class to a .class file with package sub-directories.
    String typeName = TestBase.extractClassName(clazz);
    int lastDotIndex = typeName.lastIndexOf('.');
    String pkg = typeName.substring(0, lastDotIndex);
    String baseFileName = typeName.substring(lastDotIndex + 1) + CLASS_EXTENSION;
    Path original = dir.resolve("original");
    Files.createDirectories(original);
    Path packageDir = original.resolve(pkg.replace(JAVA_PACKAGE_SEPARATOR, File.separatorChar));
    Files.createDirectories(packageDir);
    Path classFile = packageDir.resolve(baseFileName);
    Files.write(classFile, clazz);

    // Run offline instrumentation.
    Path instrumented = dir.resolve("instrumented");
    Files.createDirectories(instrumented);
    runJacocoInstrumentation(original, instrumented);
    originalJar = dir.resolve("original" + JAR_EXTENSION);
    ZipUtils.zip(originalJar, original);
    instrumentedJar = dir.resolve("instrumented" + JAR_EXTENSION);
    ZipUtils.zip(instrumentedJar, instrumented);
  }

  public Path getOriginal() {
    return originalJar;
  }

  public Path getInstrumented() {
    return instrumentedJar;
  }

  public List<String> generateReport(Path jacocoExec) throws IOException {
    Path report = temp.newFolder().toPath().resolve("report.scv");
    ProcessResult result = ToolHelper.runJaCoCoReport(originalJar, jacocoExec, report);
    assertEquals(result.toString(), 0, result.exitCode);
    return Files.readAllLines(report);
  }

  private void runJacocoInstrumentation(Path input, Path outdir) throws IOException {
    ProcessResult result = ToolHelper.runJaCoCoInstrument(input, outdir);
    assertEquals(result.toString(), 0, result.exitCode);
  }
}
