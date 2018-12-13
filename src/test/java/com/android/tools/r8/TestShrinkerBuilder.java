// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public abstract class TestShrinkerBuilder<
        C extends BaseCompilerCommand,
        B extends BaseCompilerCommand.Builder<C, B>,
        CR extends TestCompileResult<RR>,
        RR extends TestRunResult,
        T extends TestCompilerBuilder<C, B, CR, RR, T>>
    extends TestCompilerBuilder<C, B, CR, RR, T> {

  TestShrinkerBuilder(TestState state, B builder, Backend backend) {
    super(state, builder, backend);
  }

  public abstract T noTreeShaking();

  public abstract T noMinification();

  public T addKeepRules(Path path) throws IOException {
    return addKeepRules(FileUtils.readAllLines(path));
  }

  public abstract T addKeepRules(Collection<String> rules);

  public T addKeepRules(String... rules) {
    return addKeepRules(Arrays.asList(rules));
  }

  public T addKeepAllClassesRule() {
    return addKeepRules("-keep class ** { *; }");
  }

  public T addKeepClassRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addKeepRules("-keep class " + clazz.getTypeName());
    }
    return self();
  }

  public T addKeepPackageRules(Package pkg) {
    return addKeepRules("-keep class " + pkg.getName() + ".*");
  }

  public T addKeepMainRule(Class<?> mainClass) {
    return addKeepMainRule(mainClass.getTypeName());
  }

  public T addKeepMainRule(String mainClass) {
    return addKeepRules(
        StringUtils.joinLines(
            "-keep class " + mainClass + " {",
            "  public static void main(java.lang.String[]);",
            "}"));
  }

}
