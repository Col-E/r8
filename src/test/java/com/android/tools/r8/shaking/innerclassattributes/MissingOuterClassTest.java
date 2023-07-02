// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.innerclassattributes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.shaking.innerclassattributes.MissingOuterClassTest.Outer.Inner;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingOuterClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MissingOuterClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepAllClassesRule()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .compile()
        .inspect(
            inspector -> {
              // Verify that the inner class attributes referring to MissingOuterClassTest have been
              // removed.
              ClassSubject outerClassSubject = inspector.clazz(Outer.class);
              assertThat(outerClassSubject, isPresent());

              ClassSubject innerClassSubject = inspector.clazz(Inner.class);
              assertThat(innerClassSubject, isPresent());

              List<InnerClassAttribute> outerInnerClassAttributes =
                  outerClassSubject.getDexProgramClass().getInnerClasses();
              assertEquals(1, outerInnerClassAttributes.size());
              inspectInnerClassAttribute(
                  outerClassSubject,
                  outerInnerClassAttributes.get(0),
                  outerClassSubject,
                  innerClassSubject);

              List<InnerClassAttribute> innerInnerClassAttributes =
                  innerClassSubject.getDexProgramClass().getInnerClasses();
              assertEquals(1, innerInnerClassAttributes.size());
              inspectInnerClassAttribute(
                  innerClassSubject,
                  innerInnerClassAttributes.get(0),
                  outerClassSubject,
                  innerClassSubject);
            });
  }

  private void inspectInnerClassAttribute(
      ClassSubject hostClassSubject,
      InnerClassAttribute innerClassAttribute,
      ClassSubject outerClassSubject,
      ClassSubject innerClassSubject) {
    assertEquals(innerClassSubject.getDexProgramClass().getType(), innerClassAttribute.getInner());
    assertEquals(outerClassSubject.getDexProgramClass().getType(), innerClassAttribute.getOuter());
    if (parameters.isCfRuntime() || hostClassSubject == innerClassSubject) {
      assertEquals(
          DescriptorUtils.getInnerClassNameFromSimpleName(
              outerClassSubject.getDexProgramClass().getSimpleName(),
              innerClassSubject.getDexProgramClass().getSimpleName()),
          innerClassAttribute.getInnerName().toSourceString());
    } else {
      assertEquals(DexItemFactory.unknownTypeName, innerClassAttribute.getInnerName());
    }
  }

  static class Outer {

    static class Inner {}
  }
}
