// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ShrunkenLibraryTest extends TestBase {

  private static Path mappingFile;

  @Before
  public void setup() throws Exception {
    // Mapping file that describes that Runnable has been renamed to A.
    mappingFile = temp.newFile("mapping.txt").toPath();
    FileUtils.writeTextFile(
        mappingFile, Runnable.class.getTypeName() + " -> " + A.class.getTypeName() + ":");
  }

  @Test
  public void testProguard() throws Exception {
    testForProguard()
        .addProgramClasses(ShrunkenLibraryTestClass.class)
        .addKeepRules(
            "-keep class " + ShrunkenLibraryTestClass.class.getTypeName() + " {",
            "  public void method(" + Runnable.class.getTypeName() + ");",
            "}",
            "-applymapping " + mappingFile.toAbsolutePath())
        .compile()
        .inspect(this::inspect);
  }

  @Ignore("b/121305642")
  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(ShrunkenLibraryTestClass.class)
        .addKeepRules(
            "-keep class " + ShrunkenLibraryTestClass.class.getTypeName() + " {",
            "  public void method(" + Runnable.class.getTypeName() + ");",
            "}",
            "-applymapping " + mappingFile.toAbsolutePath())
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector.clazz(ShrunkenLibraryTestClass.class).uniqueMethodWithName("method");
    assertThat(methodSubject, isPresent());
    assertEquals(
        A.class.getTypeName(), methodSubject.getMethod().method.proto.parameters.toSourceString());
  }
}

class ShrunkenLibraryTestClass {

  public void method(Runnable obj) {}
}

class A {}
