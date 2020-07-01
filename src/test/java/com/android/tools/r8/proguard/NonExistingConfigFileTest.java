// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.proguard;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.nio.file.Path;
import org.junit.Test;

public class NonExistingConfigFileTest extends TestBase {

  @Test
  public void testWithExistingFile() throws Exception {
    Path pg = temp.getRoot().toPath().resolve("existing/directory/pg.txt");
    pg.getParent().toFile().mkdirs();
    FileUtils.writeTextFile(pg, "-keep,allowobfuscation class " + TestClass.class.getName());
    testForR8(Backend.DEX)
        .addInnerClasses(NonExistingConfigFileTest.class)
        .addKeepRuleFiles(pg)
        .compile()
        .assertNoMessages()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              assertThat(clazz, isPresentAndRenamed());
            });
  }

  @Test
  public void testWithNonExistingFile() throws Exception {
    Path pg = temp.getRoot().toPath().resolve("not/a/directory/pg.txt");
    try {
      testForR8(Backend.DEX)
          .addInnerClasses(NonExistingConfigFileTest.class)
          .addKeepRuleFiles(pg)
          .compile();
      fail("Expect to fail");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Failed to read file"));
    }
  }

  static class TestClass {}
}