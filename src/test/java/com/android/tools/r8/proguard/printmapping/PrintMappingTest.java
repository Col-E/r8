// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.proguard.printmapping;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrintMappingTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public PrintMappingTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testWithExistingDirectory() throws Exception {
    Path mapping = temp.getRoot().toPath().resolve("existing/directory/mapping.txt");
    mapping.getParent().toFile().mkdirs();
    test(mapping);
  }

  @Test
  public void testWithNonExistingDirectory() throws Exception {
    test(temp.getRoot().toPath().resolve("not/a/directory/mapping.txt"));
  }

  private void test(Path mapping) throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(PrintMappingTest.class)
        .addKeepRules("-keep,allowobfuscation class " + TestClass.class.getTypeName())
        .addKeepRules("-printmapping " + mapping)
        .setMinApi(AndroidApiLevel.B)
        .compile();
    assertTrue(mapping.toFile().exists());
  }

  static class TestClass {}
}
