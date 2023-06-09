// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepEdgeAstTest extends TestBase {

  private static String CLASS = "com.example.Foo";

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeepEdgeAstTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  public static String extract(KeepEdge edge) {
    StringBuilder builder = new StringBuilder();
    KeepRuleExtractor extractor = new KeepRuleExtractor(builder::append);
    extractor.extract(edge);
    return builder.toString();
  }

  @Test
  public void testKeepAll() {
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(KeepTarget.builder().setItemPattern(KeepItemPattern.any()).build())
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
                            .setItemPattern(KeepItemPattern.any())
                            .setOptions(KeepOptions.disallow(KeepOption.OPTIMIZING))
                            .build())
                    .build())
            .build();
    // Disallow will issue the full inverse of the known options, e.g., 'allowaccessmodification'.
    List<String> options =
        ImmutableList.of("shrinking", "obfuscation", "accessmodification", "annotationremoval");
    String allows = String.join(",allow", options);
    // The "any" item will be split in two rules, one for the targeted types and one for the
    // targeted members.
    assertEquals(StringUtils.unixLines("-keep,allow" + allows + " class * { *; }"), extract(edge));
  }

  @Test
  public void testSoftPinViaAllow() {
    KeepEdge edge =
        KeepEdge.builder()
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        KeepTarget.builder()
                            .setItemPattern(KeepItemPattern.any())
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
  public void testKeepClass() {
    KeepTarget target = target(classItem(CLASS));
    KeepConsequences consequences = KeepConsequences.builder().addTarget(target).build();
    KeepEdge edge = KeepEdge.builder().setConsequences(consequences).build();
    assertEquals(
        StringUtils.unixLines("-keep class " + CLASS + " { void finalize(); }"), extract(edge));
  }

  @Test
  public void testKeepInitIfReferenced() {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(
                        target(
                            buildClassItem(CLASS)
                                .setMemberPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines("-keepclassmembers class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceIfReferenced() {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(KeepConsequences.builder().addTarget(target(classItem(CLASS))).build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class " + CLASS + " -keep class " + CLASS + " { void finalize(); }"),
        extract(edge));
  }

  @Test
  public void testKeepInstanceAndInitIfReferenced() {
    KeepEdge edge =
        KeepEdge.builder()
            .setPreconditions(
                KeepPreconditions.builder()
                    .addCondition(KeepCondition.builder().setItemPattern(classItem(CLASS)).build())
                    .build())
            .setConsequences(
                KeepConsequences.builder()
                    .addTarget(target(classItem(CLASS)))
                    .addTarget(
                        target(
                            buildClassItem(CLASS)
                                .setMemberPattern(defaultInitializerPattern())
                                .build()))
                    .build())
            .build();
    assertEquals(
        StringUtils.unixLines(
            "-if class " + CLASS + " -keep class " + CLASS + " { void <init>(); }"),
        extract(edge));
  }

  private KeepTarget target(KeepItemPattern item) {
    return KeepTarget.builder().setItemPattern(item).build();
  }

  private KeepItemPattern classItem(String typeName) {
    return buildClassItem(typeName).build();
  }

  private KeepItemPattern.Builder buildClassItem(String typeName) {
    return KeepItemPattern.builder().setClassPattern(KeepQualifiedClassNamePattern.exact(typeName));
  }

  private KeepMemberPattern defaultInitializerPattern() {
    return KeepMethodPattern.builder()
        .setNamePattern(KeepMethodNamePattern.initializer())
        .setParametersPattern(KeepMethodParametersPattern.none())
        .setReturnTypeVoid()
        .build();
  }
}
