// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class KeepClassMembersTest extends TestBase {

  public void runTest(Class mainClass, Class<?> staticClass,
      boolean forceProguardCompatibility) throws Exception {
    boolean staticClassHasDefaultConstructor = true;
    try {
      staticClass.getDeclaredConstructor();
    } catch (NoSuchMethodException e) {
      staticClassHasDefaultConstructor = false;
    }
    String proguardConfig = String.join("\n", ImmutableList.of(
        "-keepclassmembers class **.PureStatic* {",
        "  public static int b;",
        "  public static int getA();",
        "  public int getI();",
        "}",
        "-keep class **.MainUsing* {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontoptimize", "-dontobfuscate"
    ));
    DexInspector inspector = new DexInspector(
        compileWithR8(ImmutableList.of(mainClass, staticClass), proguardConfig,
            options -> options.forceProguardCompatibility = forceProguardCompatibility));
    assertTrue(inspector.clazz(mainClass).isPresent());
    ClassSubject staticClassSubject = inspector.clazz(staticClass);
    assertTrue(staticClassSubject.isPresent());
    assertTrue(staticClassSubject.method("int", "getA", ImmutableList.of()).isPresent());
    assertFalse(staticClassSubject.method("int", "getB", ImmutableList.of()).isPresent());
    assertTrue(staticClassSubject.field("int", "a").isPresent());
    assertTrue(staticClassSubject.field("int", "b").isPresent());
    assertFalse(staticClassSubject.field("int", "c").isPresent());
    // Force Proguard compatibility keeps the default constructor if present and then assumes
    // instantiated, hence keeps the instance method as well.
    assertEquals(forceProguardCompatibility && staticClassHasDefaultConstructor,
        staticClassSubject.init(ImmutableList.of()).isPresent());
    assertEquals(forceProguardCompatibility && staticClassHasDefaultConstructor,
        staticClassSubject.method("int", "getI", ImmutableList.of()).isPresent());
    assertEquals(forceProguardCompatibility && staticClassHasDefaultConstructor,
        staticClassSubject.field("int", "i").isPresent());
    assertFalse(staticClassSubject.method("int", "getJ", ImmutableList.of()).isPresent());
    assertFalse(staticClassSubject.field("int", "j").isPresent());
  }

  @Test
  public void regress69028743() throws Exception {
    runTest(MainUsingWithDefaultConstructor.class,
        PureStaticClassWithDefaultConstructor.class, false);
    runTest(MainUsingWithDefaultConstructor.class,
        PureStaticClassWithDefaultConstructor.class, true);
    runTest(MainUsingWithoutDefaultConstructor.class,
        PureStaticClassWithoutDefaultConstructor.class, false);
    runTest(MainUsingWithoutDefaultConstructor.class,
        PureStaticClassWithoutDefaultConstructor.class, true);
  }
}
