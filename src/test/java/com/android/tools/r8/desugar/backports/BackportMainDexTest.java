// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.synthesis.SyntheticItems.EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.desugar.backports.AbstractBackportTest.MiniAssert;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BackportMainDexTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  static final List<Class<?>> CLASSES =
      ImmutableList.of(MiniAssert.class, TestClass.class, User1.class, User2.class);

  static final String SyntheticUnderUser1 =
      User1.class.getTypeName() + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;
  static final String SyntheticUnderUser2 =
      User2.class.getTypeName() + EXTERNAL_SYNTHETIC_CLASS_SEPARATOR;

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withApiLevel(AndroidApiLevel.J).build();
  }

  public BackportMainDexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private String[] getRunArgs() {
    // Only call User1 methods on runtimes with native multidex.
    if (parameters.isCfRuntime()
        || parameters
            .getRuntime()
            .asDex()
            .getMinApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithNativeMultiDexSupport())) {
      return new String[] {User1.class.getTypeName()};
    }
    return new String[0];
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    Set<String> mainDex1 = runD8();
    Set<String> mainDex2 = runD8();
    assertEquals("Expected deterministic main-dex lists", mainDex1, mainDex2);
  }

  private Set<String> runD8() throws Exception {
    MainDexConsumer mainDexConsumer = new MainDexConsumer();
    testForD8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .setMinApi(parameters.getApiLevel())
        .addMainDexListClasses(MiniAssert.class, TestClass.class, User2.class)
        .setProgramConsumer(mainDexConsumer)
        .compile()
        .inspect(
            inspector -> {
              // Note: This will change if we group methods in classes, in which case we should
              // preferable not put non-main-dex referenced methods in the main-dex group.
              // User1 has two synthetics, one shared with User2 and one for self.
              assertEquals(
                  2,
                  inspector.allClasses().stream()
                      .filter(c -> c.getFinalName().startsWith(SyntheticUnderUser1))
                      .count());
              // User2 has one synthetic as the shared call is placed in User1.
              assertEquals(
                  1,
                  inspector.allClasses().stream()
                      .filter(c -> c.getFinalName().startsWith(SyntheticUnderUser2))
                      .count());
            })
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    checkMainDex(mainDexConsumer);
    return mainDexConsumer.mainDexDescriptors;
  }

  @Test
  public void testR8() throws Exception {
    Set<String> mainDex1 = runR8();
    if (parameters.isDexRuntime()) {
      Set<String> mainDex2 = runR8();
      assertEquals(mainDex1, mainDex2);
    }
  }

  private Set<String> runR8() throws Exception {
    MainDexConsumer mainDexConsumer = parameters.isDexRuntime() ? new MainDexConsumer() : null;
    testForR8(parameters.getBackend())
        .debug() // Use debug mode to force a minimal main dex.
        .noMinification() // Disable minification so we can inspect the synthetic names.
        .applyIf(mainDexConsumer != null, b -> b.setProgramConsumer(mainDexConsumer))
        .addProgramClasses(CLASSES)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(MiniAssert.class)
        .addMainDexClassRules(MiniAssert.class, TestClass.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(User1.class.getMethod("testBooleanCompare")),
            Reference.methodFromMethod(User1.class.getMethod("testCharacterCompare")))
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class, getRunArgs())
        .assertSuccessWithOutput(EXPECTED);
    if (mainDexConsumer != null) {
      checkMainDex(mainDexConsumer);
      return mainDexConsumer.mainDexDescriptors;
    }
    return null;
  }

  private void checkMainDex(MainDexConsumer mainDexConsumer) throws Exception {
    AndroidApp mainDexApp =
        AndroidApp.builder()
            .addDexProgramData(mainDexConsumer.mainDexBytes, Origin.unknown())
            .build();
    CodeInspector mainDexInspector = new CodeInspector(mainDexApp);

    // The program classes in the main-dex list must be in main-dex.
    assertThat(mainDexInspector.clazz(MiniAssert.class), isPresent());
    assertThat(mainDexInspector.clazz(TestClass.class), isPresent());
    assertThat(mainDexInspector.clazz(User2.class), isPresent());

    // At least one synthetic class placed under User2 must be included in the main-dex file.
    assertEquals(
        1,
        mainDexInspector.allClasses().stream()
            .filter(c -> c.getFinalName().startsWith(SyntheticUnderUser2))
            .count());

    // Minimal main dex should only include one of the User1 synthetics.
    assertThat(mainDexInspector.clazz(User1.class), not(isPresent()));
    assertEquals(
        1,
        mainDexInspector.allClasses().stream()
            .filter(c -> c.getFinalName().startsWith(SyntheticUnderUser1))
            .count());
    assertThat(mainDexInspector.clazz(SyntheticUnderUser1), not(isPresent()));
  }

  static class User1 {

    public static void testBooleanCompare() {
      // These 4 calls should share the same synthetic method.
      MiniAssert.assertTrue(Boolean.compare(true, false) > 0);
      MiniAssert.assertTrue(Boolean.compare(true, true) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, false) == 0);
      MiniAssert.assertTrue(Boolean.compare(false, true) < 0);
    }

    public static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('b', 'a') > 0);
      MiniAssert.assertTrue(Character.compare('a', 'a') == 0);
      MiniAssert.assertTrue(Character.compare('a', 'b') < 0);
    }
  }

  static class User2 {

    public static void testCharacterCompare() {
      // All 6 (User1 and User2) calls should share the same synthetic method.
      MiniAssert.assertTrue(Character.compare('y', 'x') > 0);
      MiniAssert.assertTrue(Character.compare('x', 'x') == 0);
      MiniAssert.assertTrue(Character.compare('x', 'y') < 0);
    }

    public static void testIntegerCompare() {
      // These 3 calls should share the same synthetic method.
      MiniAssert.assertTrue(Integer.compare(2, 0) > 0);
      MiniAssert.assertTrue(Integer.compare(0, 0) == 0);
      MiniAssert.assertTrue(Integer.compare(0, 2) < 0);
    }
  }

  static class TestClass {

    public static void main(String[] args) throws Exception {
      if (args.length == 1) {
        // Reflectively call the backports on User1 which is not in the main-dex list.
        Class<?> user1 = Class.forName(args[0]);
        user1.getMethod("testBooleanCompare").invoke(user1);
        user1.getMethod("testCharacterCompare").invoke(user1);
      }
      User2.testCharacterCompare();
      User2.testIntegerCompare();
      System.out.println("Hello, world");
    }
  }

  private static class MainDexConsumer implements DexIndexedConsumer {

    byte[] mainDexBytes;
    Set<String> mainDexDescriptors;

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      if (fileIndex == 0) {
        assertNull(mainDexBytes);
        assertNull(mainDexDescriptors);
        mainDexBytes = data.copyByteData();
        mainDexDescriptors = descriptors;
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      assertNotNull(mainDexBytes);
      assertNotNull(mainDexDescriptors);
    }
  }
}
