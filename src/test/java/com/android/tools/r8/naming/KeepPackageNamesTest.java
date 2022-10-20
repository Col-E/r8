// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.getPackageNameFromDescriptor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.keeppackagenames.Top;
import com.android.tools.r8.naming.keeppackagenames.sub.SubClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepPackageNamesTest extends TestBase {
  private static final String PACKAGE_NAME = Top.class.getPackage().getName();
  private static final List<Class<?>> CLASSES = ImmutableList.of(Top.class, SubClass.class);

  enum TestConfig {
    SINGLE_ASTERISK,
    DOUBLE_ASTERISKS;

    public String getKeepRule() {
      switch (this) {
        case SINGLE_ASTERISK:
          return "-keeppackagenames com.android.tools.r8.naming.keeppackage*";
        case DOUBLE_ASTERISKS:
          return "-keeppackagenames com.android.tools.r8.naming.keeppackage**";
      }
      throw new Unreachable();
    }

    public void inspect(CodeInspector inspector) {
      ClassSubject top = inspector.clazz(Top.class);
      assertThat(top, isPresentAndRenamed());
      assertEquals(
          getPackageNameFromDescriptor(top.getOriginalDescriptor()),
          getPackageNameFromDescriptor(top.getFinalDescriptor()));
      assertEquals(PACKAGE_NAME, getPackageNameFromDescriptor(top.getFinalDescriptor()));

      ClassSubject sub = inspector.clazz(SubClass.class);
      assertThat(sub, isPresentAndRenamed());
      switch (this) {
        case SINGLE_ASTERISK:
          assertNotEquals(
              getPackageNameFromDescriptor(sub.getOriginalDescriptor()),
              getPackageNameFromDescriptor(sub.getFinalDescriptor()));
          break;
        case DOUBLE_ASTERISKS:
          assertEquals(
              getPackageNameFromDescriptor(sub.getOriginalDescriptor()),
              getPackageNameFromDescriptor(sub.getFinalDescriptor()));
          assertThat(
              getPackageNameFromDescriptor(sub.getFinalDescriptor()), containsString(PACKAGE_NAME));
          break;
      }
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static Object[] parameters() {
    return TestConfig.values();
  }

  private final TestConfig config;

  public KeepPackageNamesTest(TestConfig config) {
    this.config = config;
  }

  @Test
  public void testProguard() throws Exception {
    testForProguard(ProguardVersion.V6_0_1)
        .addProgramClasses(CLASSES)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepRules(config.getKeepRule())
        .compile()
        .inspect(config::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(CLASSES)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepRules(config.getKeepRule())
        .compile()
        .inspect(config::inspect);
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(Backend.DEX)
        .addProgramClasses(CLASSES)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepRules(config.getKeepRule())
        .compile()
        .inspect(config::inspect);
  }
}
