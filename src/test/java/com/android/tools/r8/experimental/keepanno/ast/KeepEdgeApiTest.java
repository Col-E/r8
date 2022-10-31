// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.experimental.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.experimental.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeApiTest extends TestBase {

  private static String CLASS = "com.example.Foo";

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
  public void testSoftPinViaDisallow() {
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItem(KeepItemPattern.any())
                            .setOptions(KeepOptions.disallow(KeepOption.OPTIMIZING))
                            .build())
                    .build())
            .build();
    // Disallow will issue the full inverse of the known options, e.g., 'allowaccessmodification'.
    assertEquals(
        StringUtils.unixLines(
            "-keep,allowshrinking,allowobfuscation,allowaccessmodification class * { *; }"),
        extract(edge));
  }

  @Test
  public void testSoftPinViaAllow() {
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItem(KeepItemPattern.any())
                            .setOptions(
                                KeepOptions.allow(KeepOption.OBFUSCATING, KeepOption.SHRINKING))
                            .build())
                    .build())
            .build();
    // Allow is just the ordered list of options.
    assertEquals(
        StringUtils.unixLines("-keep,allowshrinking,allowobfuscation class * { *; }"),
        extract(edge));
  }

  @Test
  public void testKeepClass() throws Exception {
    KeepTarget target = target(classItem(CLASS));
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    assertEquals(StringUtils.unixLines("-keep class " + CLASS), extract(edge));
  }

  @Test
  public void testKeepInitIfReferenced() throws Exception {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setItem(classItem(CLASS))
                            .build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        target(
                            buildClassItem(CLASS)
                                .setMembersPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines("-keepclassmembers class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceIfReferenced() throws Exception {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setItem(classItem(CLASS))
                            .build())
                    .build())
            .setConsequences(KeepConsequences.builder().addTarget(target(classItem(CLASS))).build())
            .build();
    assertEquals(
        StringUtils.unixLines("-if class " + CLASS + " -keep class " + CLASS), extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferenced() throws Exception {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(
                        KeepCondition.builder()
                            .setItem(classItem(CLASS))
                            .build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(classItem(CLASS)))
                    .addTarget(
                        target(
                            buildClassItem(CLASS)
                                .setMembersPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class " + CLASS + " -keep class " + CLASS,
            "-keepclassmembers class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  private KeepTarget target(KeepItemPattern item) {
    return KeepTarget.builder().setItem(item).build();
  }

  private KeepItemPattern classItem(String typeName) {
    return buildClassItem(typeName).build();
  }

  private KeepItemPattern.Builder buildClassItem(String typeName) {
    return KeepItemPattern.builder().setClassPattern(KeepQualifiedClassNamePattern.exact(typeName));
  }

  private KeepMembersPattern defaultInitializerPattern() {
    return KeepMembersPattern.builder()
        .addMethodPattern(
            KeepMethodPattern.builder()
                .setNamePattern(KeepMethodNamePattern.initializer())
                .setParametersPattern(KeepMethodParametersPattern.none())
                .setReturnTypeVoid()
                .build())
        .build();
  }
}
