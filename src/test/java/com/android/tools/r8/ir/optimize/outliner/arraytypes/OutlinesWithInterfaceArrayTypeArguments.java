// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.arraytypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OutlinesWithInterfaceArrayTypeArguments extends TestBase {

  private final boolean allowOutlinerInterfaceArrayArguments;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{1}, allow interface array types in outlining on JVM: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public OutlinesWithInterfaceArrayTypeArguments(
      boolean allowOutlinerInterfaceArrayArguments, TestParameters parameters) {
    this.allowOutlinerInterfaceArrayArguments = allowOutlinerInterfaceArrayArguments;
    this.parameters = parameters;
  }

  private void validateOutlining(CodeInspector inspector) {
    // No outlining when arrays of interfaces are involved, see b/132420510 - unless the testing
    // option is set.
    ClassSubject outlineClass = inspector.clazz(OutlineOptions.CLASS_NAME);
    if (allowOutlinerInterfaceArrayArguments && parameters.isCfRuntime()) {
      assertThat(outlineClass, isPresent());
      MethodSubject outline0Method =
          outlineClass.method(
              "void", "outline0", ImmutableList.of("java.lang.Object[]", "java.lang.Object[]"));
      assertThat(outline0Method, isPresent());
      ClassSubject classSubject = inspector.clazz(TestClass.class);
      assertThat(
          classSubject.uniqueMethodWithName("method1"), CodeMatchers.invokesMethod(outline0Method));
      assertThat(
          classSubject.uniqueMethodWithName("method2"), CodeMatchers.invokesMethod(outline0Method));
    } else {
      assertThat(outlineClass, not(isPresent()));
    }
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("1", "1", "2", "2");
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(OutlinesWithInterfaceArrayTypeArguments.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(ClassImplementingIface.class)
        .addKeepClassAndMembersRules(OtherClassImplementingIface.class)
        .setMinApi(parameters.getRuntime())
        .noMinification()
        .addOptionsModification(
            options -> {
              if (parameters.isCfRuntime()) {
                assert !options.outline.enabled;
                options.outline.enabled = true;
              }
              options.outline.threshold = 2;
              options.outline.minSize = 2;
              options.testing.allowOutlinerInterfaceArrayArguments =
                  allowOutlinerInterfaceArrayArguments;
            })
        .compile()
        .inspect(this::validateOutlining)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  interface Iface {
    int interfaceMethod();
  }

  public static class ClassImplementingIface implements Iface {
    public int interfaceMethod() {
      return 1;
    }
  }

  public static class OtherClassImplementingIface implements Iface {
    public int interfaceMethod() {
      return 2;
    }
  }

  public static class TestClass {
    @NeverInline
    public static void useArray(Iface[] ifaceArray) {
      System.out.println(ifaceArray[0].interfaceMethod());
    }

    @NeverInline
    public static void method1(Iface[] ifaceArray) {
      // These two invokes are expected to be outlined, when the testing option is set.
      useArray(ifaceArray);
      useArray(ifaceArray);
    }

    @NeverInline
    public static void method2(Iface[] ifaceArray) {
      // These two invokes are expected to be outlined, when the testing option is set.
      useArray(ifaceArray);
      useArray(ifaceArray);
    }

    public static void main(String[] args) {
      Iface[] ifaceArray1 = new ClassImplementingIface[1];
      ifaceArray1[0] = new ClassImplementingIface();
      method1(ifaceArray1);
      Iface[] ifaceArray2 = new OtherClassImplementingIface[1];
      ifaceArray2[0] = new OtherClassImplementingIface();
      method2(ifaceArray2);
    }
  }
}
