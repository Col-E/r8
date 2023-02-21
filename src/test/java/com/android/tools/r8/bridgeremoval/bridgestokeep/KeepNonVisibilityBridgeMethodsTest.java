// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.bridgestokeep;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepNonVisibilityBridgeMethodsTest extends TestBase {

  private final boolean minification;
  private final TestParameters parameters;

  @Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters().withDexRuntimes().withAllRuntimesAndApiLevels().build());
  }

  public KeepNonVisibilityBridgeMethodsTest(boolean minification, TestParameters parameters) {
    this.minification = minification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(
            DataAdapter.class,
            SimpleDataAdapter.class,
            ObservableList.class,
            SimpleObservableList.class,
            Main.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-neverinline class " + SimpleDataAdapter.class.getTypeName() + " {",
            "  synthetic void registerObserver(...);",
            "}")
        .allowAccessModification()
        .addAlwaysInliningAnnotations()
        .addKeepRules(
            "-alwaysinline class * { @"
                + AlwaysInline.class.getTypeName()
                + " !synthetic <methods>; }",
            "-noparametertypestrengthening class * { synthetic <methods>; }")
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableProguardTestOptions()
        .minification(minification)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject classSubject = inspector.clazz(SimpleDataAdapter.class);
              assertThat(classSubject, isPresent());

              MethodSubject subject = classSubject.uniqueMethodWithOriginalName("registerObserver");
              assertThat(subject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess();
  }
}
