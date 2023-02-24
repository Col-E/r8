// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.Matchers;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PackageInfoClassFileWithoutAbstractAccessModifierTest extends TestBase
    implements Opcodes {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public Backend backend;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), Backend.values());
  }

  private static final String CLASS_NAME = "it.unimi.dsi.fastutil.package-info";

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject packageInfo = inspector.clazz(CLASS_NAME);
    assertThat(packageInfo, Matchers.isInterface());
    assertEquals(backend.isDex() || isR8, packageInfo.isAbstract());
  }

  @Test
  public void testD8() throws Exception {
    testForD8(backend)
        .addProgramClassFileData(dump())
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(backend)
        .addProgramClassFileData(dump())
        .addKeepMainRule(CLASS_NAME)
        .setMinApi(AndroidApiLevel.B)
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }

  // Some versions of javac would generate package-info class files without ACC_ABSTRACT.
  // Dump of it/unimi/dsi/fastutil/package-info.class from fastutil-8.5.8.jar.
  public static byte[] dump() {
    ClassWriter classWriter = new ClassWriter(0);
    classWriter.visit(
        V1_5, ACC_INTERFACE, "it/unimi/dsi/fastutil/package-info", null, "java/lang/Object", null);
    classWriter.visitSource("package-info.java", null);
    classWriter.visitEnd();
    return classWriter.toByteArray();
  }
}
