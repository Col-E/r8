// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.boxedprimitives;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnboxToCheckNotNullTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testValue() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertCheckNotNull)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  private void assertCheckNotNull(CodeInspector codeInspector) {
    DexItemFactory factory = codeInspector.getFactory();

    Set<DexMethod> unboxMethods = Sets.newIdentityHashSet();
    unboxMethods.addAll(factory.unboxPrimitiveMethod.values());
    Set<DexType> boxedTypes = Sets.newIdentityHashSet();
    boxedTypes.addAll(factory.primitiveToBoxed.values());

    // All unbox operations should have been replaced by checkNotNull operations.
    codeInspector
        .clazz(Main.class)
        .allMethods(
            m ->
                !m.getParameters().isEmpty()
                    && boxedTypes.contains(
                        factory.createType(
                            DescriptorUtils.javaTypeToDescriptor(m.getParameter(0).getTypeName()))))
        .forEach(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isInvoke() && unboxMethods.contains(i.getMethod()))));
  }

  private String getExpectedResult() {
    List<String> result = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      result.add("run succeeded");
      result.add("npe failure");
    }
    return StringUtils.lines(result);
  }

  public static class Main {

    public static void main(String[] args) {
      testCheckingNPE(() -> intTest(1));
      testCheckingNPE(() -> intTest(null));

      testCheckingNPE(() -> longTest(1L));
      testCheckingNPE(() -> longTest(null));

      testCheckingNPE(() -> doubleTest(1.0));
      testCheckingNPE(() -> doubleTest(null));

      testCheckingNPE(() -> floatTest(1.0f));
      testCheckingNPE(() -> floatTest(null));

      testCheckingNPE(() -> byteTest((byte) 1));
      testCheckingNPE(() -> byteTest(null));

      testCheckingNPE(() -> shortTest((short) 1));
      testCheckingNPE(() -> shortTest(null));

      testCheckingNPE(() -> charTest('c'));
      testCheckingNPE(() -> charTest(null));

      testCheckingNPE(() -> booleanTest(true));
      testCheckingNPE(() -> booleanTest(null));
    }

    public static void testCheckingNPE(Runnable runnable) {
      try {
        runnable.run();
        System.out.println("run succeeded");
      } catch (NullPointerException npe) {
        System.out.println("npe failure");
      }
    }

    @NeverInline
    public static void intTest(Integer i) {
      i.intValue();
    }

    @NeverInline
    public static void doubleTest(Double d) {
      d.doubleValue();
    }

    @NeverInline
    public static void longTest(Long l) {
      l.longValue();
    }

    @NeverInline
    public static void floatTest(Float f) {
      f.floatValue();
    }

    @NeverInline
    public static void shortTest(Short s) {
      s.shortValue();
    }

    @NeverInline
    public static void charTest(Character c) {
      c.charValue();
    }

    @NeverInline
    public static void byteTest(Byte b) {
      b.byteValue();
    }

    @NeverInline
    public static void booleanTest(Boolean b) {
      b.booleanValue();
    }
  }
}
