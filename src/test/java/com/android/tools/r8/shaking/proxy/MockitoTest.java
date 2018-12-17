// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.proxy;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MockitoTest extends TestBase {
  private static final String M_I_PKG = "mockito_interface";
  private static final String M_I = M_I_PKG + ".Interface";
  private static final Path MOCKITO_INTERFACE_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, M_I_PKG + FileUtils.JAR_EXTENSION);

  private final Backend backend;
  private final boolean minify;

  @Parameterized.Parameters(name = "Backend: {0} minify: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public MockitoTest(Backend backend, boolean minify) {
    this.backend = backend;
    this.minify = minify;
  }

  @Test
  public void b120675359_devirtualized() throws Exception {
    Path flagToKeepTestRunner = Paths.get(ToolHelper.EXAMPLES_DIR, M_I_PKG, "keep-rules.txt");
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(MOCKITO_INTERFACE_JAR)
        .addKeepRules(flagToKeepTestRunner);
    if (!minify) {
      builder.noMinification();
    }
    CodeInspector inspector = builder.compile().inspector();
    ClassSubject itf = inspector.clazz(M_I);
    assertThat(itf, isPresent());
    MethodSubject mtd = itf.method("void", "onEnterForeground");
    assertThat(mtd, not(isPresent()));
  }

  @Test
  public void b120675359_conditional_keep() throws Exception {
    Path flagToKeepInterfaceConditionally =
        Paths.get(ToolHelper.EXAMPLES_DIR, M_I_PKG, "keep-rules-conditional-on-mock.txt");
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(MOCKITO_INTERFACE_JAR)
        .addKeepRules(flagToKeepInterfaceConditionally);
    if (!minify) {
      builder.noMinification();
    }
    CodeInspector inspector = builder.compile().inspector();
    ClassSubject itf = inspector.clazz(M_I);
    assertThat(itf, isPresent());
    MethodSubject mtd = itf.method("void", "onEnterForeground");
    assertThat(mtd, isPresent());
    assertThat(mtd, not(isRenamed()));
  }

}
