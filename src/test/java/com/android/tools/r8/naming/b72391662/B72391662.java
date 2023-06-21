// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.b72391662;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageSuper;
import com.android.tools.r8.naming.b72391662.subpackage.OtherPackageTestClass;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B72391662 extends ProguardCompatibilityTestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          TestMain.class,
          Interface.class,
          Super.class,
          TestClass.class,
          OtherPackageSuper.class,
          OtherPackageTestClass.class,
          NeverPropagateValue.class);

  private final Shrinker shrinker;
  private final String repackagePrefix;
  private final boolean allowAccessModification;
  private final boolean minify;

  @Parameterized.Parameters(name = "Shrinker: {0}, Prefix: {1}, AllowAccessMod: {2}, Minify: {3}")
  public static Collection<Object[]> data() {
    List<Object[]> result = new ArrayList<>();
    for (Shrinker shrinker :
        new Shrinker[] {
          Shrinker.R8,
          Shrinker.R8_COMPAT,
          Shrinker.PROGUARD6_THEN_D8,
          Shrinker.R8_CF,
          Shrinker.R8_COMPAT_CF
        }) {
      for (boolean useRepackagePrefix : new boolean[] {false, true}) {
        String repackagePrefix;
        if (useRepackagePrefix) {
          switch (shrinker) {
            case R8:
              repackagePrefix = "r8";
              break;
            case R8_COMPAT:
              repackagePrefix = "rc";
              break;
            case PROGUARD6_THEN_D8:
              repackagePrefix = "pg";
              break;
            case R8_CF:
              repackagePrefix = "r8cf";
              break;
            case R8_COMPAT_CF:
              repackagePrefix = "rccf";
              break;
            default:
              assert false;
              throw new Unreachable();
          }
        } else {
          repackagePrefix = null;
        }
        for (boolean allowAccessModification : new boolean[] {false, true}) {
          for (boolean minify : new boolean[] {false, true}) {
            result.add(new Object[] {shrinker, repackagePrefix, allowAccessModification, minify});
          }
        }
      }
    }
    return result;
  }

  public B72391662(
      Shrinker shrinker, String repackagePrefix, boolean allowAccessModification, boolean minify) {
    this.shrinker = shrinker;
    this.repackagePrefix = repackagePrefix;
    this.allowAccessModification = allowAccessModification;
    this.minify = minify;
  }

  private static boolean vmVersionIgnored() {
    return !ToolHelper.getDexVm().getVersion().isNewerThanOrEqual(Version.V7_0_0);
  }

  @Test
  public void test_keepAll() throws Exception {
    Assume.assumeFalse(shrinker.generatesDex() && vmVersionIgnored());
    Class mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    List<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  *;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  *;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );

    AndroidApp app = runShrinker(shrinker, CLASSES, config);
    assertEquals(
        StringUtils.withNativeLineSeparator("123451234567\nABC\n"),
        runOnVM(app, mainClass.getCanonicalName(), shrinker.toBackend()));

    CodeInspector codeInspector =
        shrinker.isR8() ? new CodeInspector(app) : new CodeInspector(app, proguardMap);
    ClassSubject testClass = codeInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.uniqueMethodWithOriginalName("unused");
    assertThat(staticMethod, isPresent());
    assertThat(staticMethod, isPackagePrivate());
    assertEquals(minify, staticMethod.isRenamed());

    // Test an indirectly referred method.
    staticMethod = testClass.uniqueMethodWithOriginalName("staticMethod");
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    boolean publicizeCondition =
        shrinker.isProguard() && minify && repackagePrefix != null && allowAccessModification;
    assertEquals(publicizeCondition, staticMethod.getMethod().isPublic());
  }

  @Test
  public void test_keepNonPublic() throws Exception {
    Assume.assumeFalse(shrinker.generatesDex() && vmVersionIgnored());
    Class<?> mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    List<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  !public <methods>;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  !public <methods>;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );

    AndroidApp app = runShrinker(shrinker, CLASSES, config);
    assertEquals(
        StringUtils.withNativeLineSeparator("123451234567\nABC\n"),
        runOnVM(app, mainClass.getCanonicalName(), shrinker.toBackend()));

    CodeInspector codeInspector =
        shrinker.isR8() ? new CodeInspector(app) : new CodeInspector(app, proguardMap);
    ClassSubject testClass = codeInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.uniqueMethodWithOriginalName("unused");
    assertThat(staticMethod, isPresent());
    assertThat(staticMethod, isPackagePrivate());
    assertEquals(minify, staticMethod.isRenamed());

    // Test an indirectly referred method.
    staticMethod = testClass.uniqueMethodWithOriginalName("staticMethod");
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    boolean publicizeCondition =
        shrinker.isProguard() && minify && repackagePrefix != null && allowAccessModification;
    assertEquals(publicizeCondition, staticMethod.getMethod().isPublic());
  }

  @Test
  public void test_keepPublic() throws Exception {
    Assume.assumeFalse(shrinker.generatesDex() && vmVersionIgnored());
    Class<?> mainClass = TestMain.class;
    String keep = !minify ? "-keep" : "-keep,allowobfuscation";
    Iterable<String> config = ImmutableList.of(
        "-printmapping",
        repackagePrefix != null ? "-repackageclasses '" + repackagePrefix + "'" : "",
        allowAccessModification ? "-allowaccessmodification" : "",
        !minify ? "-dontobfuscate" : "",
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        keep + " class " + TestClass.class.getCanonicalName() + " {",
        "  public <methods>;",
        "}",
        keep + " class " + OtherPackageTestClass.class.getCanonicalName() + " {",
        "  public <methods>;",
        "}",
        "-dontwarn java.lang.invoke.*"
    );
    if (shrinker.isR8()) {
      config =
          Iterables.concat(
              config,
              ImmutableList.of(
                  "-assumemayhavesideeffects class " + TestClass.class.getCanonicalName() + " {",
                  "  * staticMethod();",
                  "}",
                  "-neverpropagatevalue class * {",
                  "  @com.android.tools.r8.NeverPropagateValue <methods>;",
                  "}",
                  "-neverinline class " + TestClass.class.getCanonicalName() + " {",
                  "  * staticMethod();",
                  "}"));
    }

    AndroidApp app = runShrinker(shrinker, CLASSES, config);
    assertEquals(
        StringUtils.withNativeLineSeparator("123451234567\nABC\n"),
        runOnVM(app, mainClass.getCanonicalName(), shrinker.toBackend()));

    CodeInspector codeInspector =
        shrinker.isR8() ? new CodeInspector(app) : new CodeInspector(app, proguardMap);
    ClassSubject testClass = codeInspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Test the totally unused method.
    MethodSubject staticMethod = testClass.uniqueMethodWithOriginalName("unused");
    assertThat(staticMethod, not(isPresent()));

    // Test an indirectly referred method.
    staticMethod = testClass.uniqueMethodWithOriginalName("staticMethod");
    assertThat(staticMethod, isPresent());
    assertEquals(minify, staticMethod.isRenamed());
    boolean publicizeCondition = shrinker.isR8() ? allowAccessModification
        : minify && repackagePrefix != null && allowAccessModification;
    assertEquals(publicizeCondition, staticMethod.getMethod().accessFlags.isPublic());
  }
}
