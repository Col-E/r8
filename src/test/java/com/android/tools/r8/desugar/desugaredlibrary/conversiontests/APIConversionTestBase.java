// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.CoreLibDesugarTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.Assume;

public class APIConversionTestBase extends CoreLibDesugarTestBase {

  private static final Path CONVERSION_FOLDER = Paths.get("src/test/desugaredLibrary");

  public Path[] getConversionClasses() throws IOException {
    Assume.assumeTrue(
        "JDK8 javac is required to avoid dealing with modules and JDK8 is not checked-in on"
            + " windows",
        !ToolHelper.isWindows());

    CfRuntime runtime = new CfRuntime(CfVm.JDK8);
    Path conversionFolder = temp.newFolder("conversions").toPath();

    // Compile the stubs to be able to compile the conversions.
    Path stubsJar =
        javac(runtime)
            .addSourceFiles(
                getAllFilesWithSuffixInDirectory(CONVERSION_FOLDER.resolve("stubs"), "java"))
            .compileAndWrite();

    // Compile the conversions using the stubs.
    javac(runtime)
        .setOutputPath(conversionFolder)
        .addClasspathFiles(stubsJar)
        .addSourceFiles(
            getAllFilesWithSuffixInDirectory(CONVERSION_FOLDER.resolve("conversions"), "java"))
        .compile();

    Path[] classes = getAllFilesWithSuffixInDirectory(conversionFolder, "class");
    assert classes.length > 0
        : "Something went wrong during compilation, check the runJavac return value for debugging.";
    return classes;
  }

  protected Path buildDesugaredLibraryWithConversionExtension(AndroidApiLevel apiLevel) {
    Path[] timeConversionClasses;
    try {
      timeConversionClasses = getConversionClasses();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    ArrayList<Path> paths = new ArrayList<>();
    Collections.addAll(paths, timeConversionClasses);
    return buildDesugaredLibrary(apiLevel, "", false, paths);
  }
}
