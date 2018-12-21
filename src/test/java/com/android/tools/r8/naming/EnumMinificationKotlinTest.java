// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumMinificationKotlinTest extends KotlinTestBase {
  private static final String FOLDER = "minify_enum";
  private static final String MAIN_CLASS_NAME = "minify_enum.MainKt";
  private static final String ENUM_CLASS_NAME = "minify_enum.MinifyEnum";

  private final Backend backend;
  private final boolean minify;

  @Parameterized.Parameters(name = "Backend: {0} target: {1} minify: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public EnumMinificationKotlinTest(
      Backend backend, KotlinTargetVersion targetVersion, boolean minify) {
    super(targetVersion);
    this.backend = backend;
    this.minify = minify;
  }

  @Test
  public void b121221542() throws Exception {
    R8TestBuilder builder = testForR8(backend)
        .addProgramFiles(getKotlinJarFile(FOLDER))
        .addProgramFiles(getJavaJarFile(FOLDER))
        .addKeepMainRule(MAIN_CLASS_NAME);
    if (!minify) {
      builder.noMinification();
    }
    CodeInspector inspector = builder.run(MAIN_CLASS_NAME).inspector();
    ClassSubject enumClass = inspector.clazz(ENUM_CLASS_NAME);
    assertThat(enumClass, isPresent());
    assertEquals(minify, enumClass.isRenamed());
    MethodSubject clinit = enumClass.clinit();
    assertThat(clinit, isPresent());
    assertEquals(0,
        Streams.stream(clinit.iterateInstructions(InstructionSubject::isThrow)).count());
  }

}
