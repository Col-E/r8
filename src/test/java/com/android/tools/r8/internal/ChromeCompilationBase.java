// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.hamcrest.Matcher;

public abstract class ChromeCompilationBase extends CompilationTestBase {

  static final String LIBRARY_JAR = "library.jar";
  static final String PROGRAM_JAR = "program.jar";
  static final String PROGUARD_CONFIG = "proguard.config";

  final String base;

  public ChromeCompilationBase(int version, boolean minimal) {
    this.base =
        ToolHelper.THIRD_PARTY_DIR
            + "chrome/"
            + (minimal ? "monochrome_public_minimal_apks/" : "")
            + "chrome_"
            + version
            + "/";
  }

  protected List<Path> getKeepRuleFiles() {
    ImmutableList.Builder<Path> builder = ImmutableList.builder();
    builder.add(Paths.get(base).resolve(PROGUARD_CONFIG));
    return builder.build();
  }

  protected List<Path> getLibraryFiles() throws IOException {
    assert verifyNoLibraryJarsInProguardConfig();
    List<Path> result = ImmutableList.of(Paths.get(base).resolve(LIBRARY_JAR));
    assert result.stream().allMatch(path -> path.toFile().exists());
    return result;
  }

  protected List<Path> getProgramFiles() throws IOException {
    assert verifyNoInJarsInProguardConfig();
    List<Path> result = ImmutableList.of(Paths.get(base).resolve(PROGRAM_JAR));
    assert result.stream().allMatch(path -> path.toFile().exists());
    return result;
  }

  private boolean verifyNoInJarsInProguardConfig() throws IOException {
    return verifyAllLinesInKeepRuleFilesMatch(not(containsString("-injars")));
  }

  private boolean verifyNoLibraryJarsInProguardConfig() throws IOException {
    return verifyAllLinesInKeepRuleFilesMatch(not(containsString("-libraryjars")));
  }

  private boolean verifyAllLinesInKeepRuleFilesMatch(Matcher<String> matcher) throws IOException {
    for (Path keepRuleFile : getKeepRuleFiles()) {
      for (String line : FileUtils.readAllLines(keepRuleFile)) {
        assertThat(line, matcher);
      }
    }
    return true;
  }
}
