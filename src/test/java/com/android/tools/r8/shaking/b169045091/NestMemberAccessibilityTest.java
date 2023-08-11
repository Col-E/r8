// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.b169045091;

import static com.android.tools.r8.references.Reference.INT;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.b169045091.B169045091.TestClass;
import com.android.tools.r8.shaking.b169045091.examples.NestHost;
import com.android.tools.r8.shaking.b169045091.examples.NestHost.NestMember;
import com.android.tools.r8.shaking.b169045091.examples.NonNestMember;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestMemberAccessibilityTest extends TestBase {

  private final Path TEST_DIRECTORY =
      Paths.get(ToolHelper.getExamplesJava11BuildDir())
          .resolve(
              DescriptorUtils.getBinaryNameFromJavaType(NestHost.class.getPackage().getName()));

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public NestMemberAccessibilityTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testAccessibility() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            AndroidApp.builder()
                .addProgramFiles(getProgramFiles())
                .addClassProgramData(getNestHostClassFileData())
                .build(),
            TestClass.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexItemFactory dexItemFactory = appView.dexItemFactory();

    DexProgramClass hostContext =
        appView
            .contextIndependentDefinitionFor(buildType(NestHost.class, dexItemFactory))
            .asProgramClass();

    DexProgramClass memberContext =
        appView
            .contextIndependentDefinitionFor(buildType(NestMember.class, dexItemFactory))
            .asProgramClass();

    DexProgramClass nonMemberContext =
        appView
            .contextIndependentDefinitionFor(buildType(NonNestMember.class, dexItemFactory))
            .asProgramClass();

    // Test that NestHost.f is accessible to NestHost and NestMember but not NonNestMember.
    DexField hostFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NestHost.class), "f", INT), dexItemFactory);
    assertTrue(
        appInfo.resolveField(hostFieldReference).isAccessibleFrom(hostContext, appView).isTrue());
    assertTrue(
        appInfo.resolveField(hostFieldReference).isAccessibleFrom(memberContext, appView).isTrue());
    assertTrue(
        appInfo
            .resolveField(hostFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());

    // Test that NestMember.f is accessible to NestMember but not NonNestMember.
    DexField memberFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NestMember.class), "f", INT), dexItemFactory);
    assertTrue(
        appInfo
            .resolveField(memberFieldReference)
            .isAccessibleFrom(memberContext, appView)
            .isTrue());
    assertTrue(
        appInfo
            .resolveField(memberFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());

    // Test that NonNestMember.f is inaccessible to NonNestMember.
    DexField nonMemberFieldReference =
        buildField(
            Reference.field(Reference.classFromClass(NonNestMember.class), "f", INT),
            dexItemFactory);
    assertTrue(
        appInfo
            .resolveField(nonMemberFieldReference)
            .isAccessibleFrom(nonMemberContext, appView)
            .isFalse());
  }

  private List<Path> getProgramFiles() {
    return ImmutableList.of(
        TEST_DIRECTORY.resolve("NestHost$NestMember.class"),
        TEST_DIRECTORY.resolve("NonNestMember.class"));
  }

  private byte[] getNestHostClassFileData() throws Exception {
    return transformer(
            TEST_DIRECTORY.resolve("NestHost.class"), Reference.classFromClass(NestHost.class))
        .setPrivate(NestHost.class.getDeclaredField("f"))
        .transform();
  }
}
