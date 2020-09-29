// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isStatic;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runners.Parameterized;

public class NestClassTest extends HorizontalClassMergingTestBase {
  public static final String PACKAGE_NAME = "horizontalclassmerging.";
  private final String NEST_MAIN_CLASS_1 = PACKAGE_NAME + "BasicNestHostHorizontalClassMerging";
  private final String NEST_MAIN_CLASS_1A = NEST_MAIN_CLASS_1 + "$A";
  private final String NEST_MAIN_CLASS_1B = NEST_MAIN_CLASS_1 + "$B";
  private final String NEST_MAIN_CLASS_2 = PACKAGE_NAME + "BasicNestHostHorizontalClassMerging2";
  private final String NEST_MAIN_CLASS_2A = NEST_MAIN_CLASS_2 + "$A";
  private final String NEST_MAIN_CLASS_2B = NEST_MAIN_CLASS_2 + "$B";

  private List<Path> resolveProgramFiles() {
    return ImmutableList.of(
            NEST_MAIN_CLASS_1,
            NEST_MAIN_CLASS_1A,
            NEST_MAIN_CLASS_1B,
            NEST_MAIN_CLASS_2,
            NEST_MAIN_CLASS_2A,
            NEST_MAIN_CLASS_2B)
        .stream()
        .map(NestAccessControlTestUtils.CLASSES_PATH::resolve)
        .collect(Collectors.toList());
  }

  public NestClassTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Parameterized.Parameters(name = "{0}, horizontalClassMerging:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimesStartingFromIncluding(CfVm.JDK11).build(),
        BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addKeepMainRule(NEST_MAIN_CLASS_1)
        .addProgramFiles(resolveProgramFiles())
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), NEST_MAIN_CLASS_1)
        .assertSuccessWithOutputLines("1: a", "1: b", "2: a", "2: b")
        .inspect(
            codeInspector -> {
              ClassSubject class1A = codeInspector.clazz(NEST_MAIN_CLASS_1A);
              ClassSubject class2A = codeInspector.clazz(NEST_MAIN_CLASS_2A);
              ClassSubject class1 = codeInspector.clazz(NEST_MAIN_CLASS_1);
              ClassSubject class2 = codeInspector.clazz(NEST_MAIN_CLASS_2);
              assertThat(class1, isPresent());
              assertThat(class2, isPresent());
              assertThat(class1A, isPresent());
              assertThat(class2A, isPresent());

              MethodSubject printClass1MethodSubject =
                  class1.method("void", "print", String.class.getTypeName());
              assertThat(printClass1MethodSubject, isPresent());
              assertThat(printClass1MethodSubject, isPrivate());

              MethodSubject printClass2MethodSubject =
                  class2.method("void", "print", String.class.getTypeName());
              assertThat(printClass2MethodSubject, isPresent());
              assertThat(printClass2MethodSubject, isPrivate());
              assertThat(printClass2MethodSubject, isStatic());

              if (enableHorizontalClassMerging) {
                assertThat(codeInspector.clazz(NEST_MAIN_CLASS_1B), not(isPresent()));
                assertThat(codeInspector.clazz(NEST_MAIN_CLASS_2B), not(isPresent()));
              } else {
                assertThat(codeInspector.clazz(NEST_MAIN_CLASS_1B), isPresent());
                assertThat(codeInspector.clazz(NEST_MAIN_CLASS_2B), isPresent());
              }
            });
  }
}
