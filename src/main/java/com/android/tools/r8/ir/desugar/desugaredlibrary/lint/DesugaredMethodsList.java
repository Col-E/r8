// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import static java.lang.Integer.parseInt;

import com.android.tools.r8.Keep;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;

@Keep
public class DesugaredMethodsList extends GenerateDesugaredLibraryLintFiles {

  private final AndroidApiLevel minApi;

  private DesugaredMethodsList(
      int minApi,
      String desugarConfigurationPath,
      String desugarImplementationPath,
      String ouputFile,
      String androidJarPath) {
    super(desugarConfigurationPath, desugarImplementationPath, ouputFile, androidJarPath);
    this.minApi = AndroidApiLevel.getAndroidApiLevel(minApi);
  }

  @Override
  public AndroidApiLevel run() throws Exception {
    AndroidApiLevel compilationLevel =
        desugaredLibrarySpecification.getRequiredCompilationApiLevel();
    SupportedClasses supportedMethods =
        new SupportedClassesGenerator(options, androidJar, minApi, true)
            .run(desugaredLibraryImplementation, desugaredLibrarySpecificationPath);
    System.out.println(
        "Generating lint files for "
            + getDebugIdentifier()
            + " (compile API "
            + compilationLevel
            + ")");
    writeLintFiles(compilationLevel, minApi, supportedMethods);
    return compilationLevel;
  }

  @Override
  Path lintFile(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel minApiLevel, String extension) {
    return output;
  }

  public static void main(String[] args) throws Exception {
    if (args.length == 4 || args.length == 5) {
      new DesugaredMethodsList(
              parseInt(args[0]), args[1], args[2], args[3], getAndroidJarPath(args, 5))
          .run();
      return;
    }
    throw new RuntimeException(
        StringUtils.joinLines(
            "Invalid invocation.",
            "Usage: DesugaredMethodList <min-api> <desugar configuration> "
                + "<desugar implementation> <output file> [<android jar path for Android "
                + MAX_TESTED_ANDROID_API_LEVEL
                + " or higher>]"));
  }
}
