// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk8;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class Jdk9TestUtils {

  public static ThrowableConsumer<R8FullTestBuilder> addJdk9LibraryFiles(
      TemporaryFolder temporaryFolder) {
    return builder -> builder.addLibraryFiles(getJdk9LibraryFiles(temporaryFolder));
  }

  public static Path[] getJdk9LibraryFiles(TemporaryFolder temp) throws IOException {
    Assume.assumeFalse(ToolHelper.isWindows());
    // TODO(b/180553597): Add JDK-9 runtime jar instead. As a temporary solution we use the JDK-8
    //  runtime with additional stubs.
    Path stubs =
        JavaCompilerTool.create(getCheckedInJdk8(), temp)
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "StringConcatFactory.java"))
            .addSourceFiles(Paths.get("src", "test", "javaStubs", "VarHandle.java"))
            .compile();
    return new Path[] {stubs, ToolHelper.getJava8RuntimeJar()};
  }
}
