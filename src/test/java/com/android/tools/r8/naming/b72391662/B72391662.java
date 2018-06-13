// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageSuper;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageTestClass;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class B72391662 extends ProguardCompatabilityTestBase {

  private static final List<Class> CLASSES = ImmutableList.of(
      TestMain.class, Interface.class, Super.class, TestClass.class,
      OtherPackageSuper.class, OtherPackageTestClass.class);

  private void doTest_keepAll(
      Shrinker shrinker,
      String repackagePrefix,
      boolean allowAccessModification,
      boolean minify) throws Exception {
    Class mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    List<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  *;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  *;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );

    AndroidApp app = runShrinkerRaw(shrinker, CLASSES, config);
    assertEquals("123451234567\nABC\n", runOnArt(app, mainClass.getCanonicalName()));

    DexInspector dexInspector =
        isR8(shrinker) ? new DexInspector(app) : new DexInspector(app, proguardMap);
    ClassSubject testClass = dexInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.method("void", "unused", ImmutableList.of());
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    if (isR8(shrinker)) {
      assertEquals(allowAccessModification, staticMethod.getMethod().accessFlags.isPublic());
    } else {
      assertFalse(staticMethod.getMethod().accessFlags.isPublic());
    }

    // Test an indirectly referred method.
    staticMethod = testClass.method("java.lang.String", "staticMethod", ImmutableList.of());
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    boolean publicizeCondition = isR8(shrinker) ? allowAccessModification
        : minify && repackagePrefix != null && allowAccessModification;
    assertEquals(publicizeCondition, staticMethod.getMethod().accessFlags.isPublic());
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepAll_R8() throws Exception {
    doTest_keepAll(Shrinker.R8, "r8", true, true);
    doTest_keepAll(Shrinker.R8, "r8", true, false);
    doTest_keepAll(Shrinker.R8, "r8", false, true);
    doTest_keepAll(Shrinker.R8, "r8", false, false);
    doTest_keepAll(Shrinker.R8, null, true, true);
    doTest_keepAll(Shrinker.R8, null, true, false);
    doTest_keepAll(Shrinker.R8, null, false, true);
    doTest_keepAll(Shrinker.R8, null, false, false);
  }

  @Ignore("b/92236970")
  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepAll_R8Compat() throws Exception {
    doTest_keepAll(Shrinker.R8_COMPAT, "rc", true, true);
    doTest_keepAll(Shrinker.R8_COMPAT, "rc", true, false);
    doTest_keepAll(Shrinker.R8_COMPAT, "rc", false, true);
    doTest_keepAll(Shrinker.R8_COMPAT, "rc", false, false);
    doTest_keepAll(Shrinker.R8_COMPAT, null, true, true);
    doTest_keepAll(Shrinker.R8_COMPAT, null, true, false);
    doTest_keepAll(Shrinker.R8_COMPAT, null, false, true);
    doTest_keepAll(Shrinker.R8_COMPAT, null, false, false);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepAll_Proguard6() throws Exception {
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, "pg", true, true);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, "pg", true, false);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, "pg", false, true);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, "pg", false, false);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, null, true, true);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, null, true, false);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, null, false, true);
    doTest_keepAll(Shrinker.PROGUARD6_THEN_D8, null, false, false);
  }

  private void doTest_keepNonPublic(
      Shrinker shrinker,
      String repackagePrefix,
      boolean allowAccessModification,
      boolean minify) throws Exception {
    Class mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    List<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  !public <methods>;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  !public <methods>;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );

    AndroidApp app = runShrinkerRaw(shrinker, CLASSES, config);
    assertEquals("123451234567\nABC\n", runOnArt(app, mainClass.getCanonicalName()));

    DexInspector dexInspector =
        isR8(shrinker) ? new DexInspector(app) : new DexInspector(app, proguardMap);
    ClassSubject testClass = dexInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.method("void", "unused", ImmutableList.of());
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    if (isR8(shrinker)) {
      assertEquals(allowAccessModification, staticMethod.getMethod().accessFlags.isPublic());
    } else {
      assertFalse(staticMethod.getMethod().accessFlags.isPublic());
    }

    // Test an indirectly referred method.
    staticMethod = testClass.method("java.lang.String", "staticMethod", ImmutableList.of());
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    boolean publicizeCondition = isR8(shrinker) ? allowAccessModification
        : minify && repackagePrefix != null && allowAccessModification;
    assertEquals(publicizeCondition, staticMethod.getMethod().accessFlags.isPublic());
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepNonPublic_R8() throws Exception {
    doTest_keepNonPublic(Shrinker.R8, "r8", true, true);
    doTest_keepNonPublic(Shrinker.R8, "r8", true, false);
    doTest_keepNonPublic(Shrinker.R8, "r8", false, true);
    doTest_keepNonPublic(Shrinker.R8, "r8", false, false);
    doTest_keepNonPublic(Shrinker.R8, null, true, true);
    doTest_keepNonPublic(Shrinker.R8, null, true, false);
    doTest_keepNonPublic(Shrinker.R8, null, false, true);
    doTest_keepNonPublic(Shrinker.R8, null, false, false);
  }

  @Ignore("b/92236970")
  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepNonPublic_R8Compat() throws Exception {
    doTest_keepNonPublic(Shrinker.R8_COMPAT, "rc", true, true);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, "rc", true, false);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, "rc", false, true);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, "rc", false, false);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, null, true, true);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, null, true, false);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, null, false, true);
    doTest_keepNonPublic(Shrinker.R8_COMPAT, null, false, false);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepNonPublic_Proguard6() throws Exception {
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, "pg", true, true);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, "pg", true, false);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, "pg", false, true);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, "pg", false, false);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, null, true, true);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, null, true, false);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, null, false, true);
    doTest_keepNonPublic(Shrinker.PROGUARD6_THEN_D8, null, false, false);
  }

  private void doTest_keepPublic(
      Shrinker shrinker,
      String repackagePrefix,
      boolean allowAccessModification,
      boolean minify) throws Exception {
    Class mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    List<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  public <methods>;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  public <methods>;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );

    AndroidApp app = runShrinkerRaw(shrinker, CLASSES, config);
    assertEquals("123451234567\nABC\n", runOnArt(app, mainClass.getCanonicalName()));

    DexInspector dexInspector =
        isR8(shrinker) ? new DexInspector(app) : new DexInspector(app, proguardMap);
    ClassSubject testClass = dexInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.method("void", "unused", ImmutableList.of());
    assertThat(staticMethod, not(isPresent()));

    // Test an indirectly referred method.
    staticMethod = testClass.method("java.lang.String", "staticMethod", ImmutableList.of());
    if (isR8(shrinker)) {
      // Inlined.
      assertThat(staticMethod, not(isPresent()));
    } else {
      assertThat(staticMethod, isPresent());
      assertEquals(minify, staticMethod.isRenamed());
      boolean publicizeCondition = minify && repackagePrefix != null && allowAccessModification;
      assertEquals(publicizeCondition, staticMethod.getMethod().accessFlags.isPublic());
    }
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepPublic_R8() throws Exception {
    doTest_keepPublic(Shrinker.R8, "r8", true, true);
    doTest_keepPublic(Shrinker.R8, "r8", true, false);
    doTest_keepPublic(Shrinker.R8, "r8", false, true);
    doTest_keepPublic(Shrinker.R8, "r8", false, false);
    doTest_keepPublic(Shrinker.R8, null, true, true);
    doTest_keepPublic(Shrinker.R8, null, true, false);
    doTest_keepPublic(Shrinker.R8, null, false, true);
    doTest_keepPublic(Shrinker.R8, null, false, false);
  }

  @Ignore("b/92236970")
  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepPublic_R8Compat() throws Exception {
    doTest_keepPublic(Shrinker.R8_COMPAT, "rc", true, true);
    doTest_keepPublic(Shrinker.R8_COMPAT, "rc", true, false);
    doTest_keepPublic(Shrinker.R8_COMPAT, "rc", false, true);
    doTest_keepPublic(Shrinker.R8_COMPAT, "rc", false, false);
    doTest_keepPublic(Shrinker.R8_COMPAT, null, true, true);
    doTest_keepPublic(Shrinker.R8_COMPAT, null, true, false);
    doTest_keepPublic(Shrinker.R8_COMPAT, null, false, true);
    doTest_keepPublic(Shrinker.R8_COMPAT, null, false, false);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V7_0_0)
  public void test_keepPublic_Proguard6() throws Exception {
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, "pg", true, true);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, "pg", true, false);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, "pg", false, true);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, "pg", false, false);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, null, true, true);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, null, true, false);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, null, false, true);
    doTest_keepPublic(Shrinker.PROGUARD6_THEN_D8, null, false, false);
  }
}
