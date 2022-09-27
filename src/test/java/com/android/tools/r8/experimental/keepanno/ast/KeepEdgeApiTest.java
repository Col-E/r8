// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.experimental.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeApiTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepEdgeApiTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public static String extract(KeepEdge edge) {
    StringBuilder builder = new StringBuilder();
    KeepRuleExtractor extractor = new KeepRuleExtractor(rule -> builder.append(rule).append('\n'));
    extractor.extract(edge);
    return builder.toString();
  }

  @Test
  public void testKeepAll() {
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(KeepTarget.builder().setItem(KeepItemPattern.any()).build())
                    .build())
            .build();
    assertEquals(StringUtils.unixLines("-keep class * { *; }"), extract(edge));
  }

  @Test
  public void testKeepClass() throws Exception {
    KeepQualifiedClassNamePattern clazz = KeepQualifiedClassNamePattern.exact("com.example.Foo");
    KeepItemPattern item = KeepItemPattern.builder().setClassPattern(clazz).build();
    KeepTarget target = KeepTarget.builder().setItem(item).build();
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    assertEquals(StringUtils.unixLines("-keep class com.example.Foo"), extract(edge));
  }

  @Test
  public void testKeepInitIfReferenced() throws Exception {
    // Equivalent of: -if class com.example.Foo -keep class com.example.Foo { void <init>(); }
    KeepQualifiedClassNamePattern classPattern =
        KeepQualifiedClassNamePattern.exact("com.example.Foo");
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setUsageKind(KeepUsageKind.symbolicReference())
                            .setItem(
                                KeepItemPattern.builder().setClassPattern(classPattern).build())
                            .build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItem(
                                KeepItemPattern.builder()
                                    .setClassPattern(classPattern)
                                    .setMembersPattern(
                                        KeepMembersPattern.builder()
                                            .addMethodPattern(
                                                KeepMethodPattern.builder()
                                                    .setNamePattern(
                                                        KeepMethodNamePattern.initializer())
                                                    .setParametersPattern(
                                                        KeepMethodParametersPattern.none())
                                                    .setReturnTypeVoid()
                                                    .build())
                                            .build())
                                    .build())
                            .build())
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class com.example.Foo -keep class com.example.Foo { void <init>(); }"),
        extract(edge));
  }
}
