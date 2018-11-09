// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.abst;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.methods.MethodsTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;

@NeverMerge
abstract class Super {
  public abstract void m1();
}

@NeverMerge
abstract class Sub extends Super {
  public abstract void m2();
}

@NeverMerge
class SubSub extends Sub {
  public void m1() {}

  public void m2() {}

  public void m3() {}
}

class Main {

  private static void printMethod(Class<?> clazz, String name) {
    try {
      clazz.getDeclaredMethod(name);
      System.out.println(clazz.getSimpleName() + "." + name + " found");
    } catch (NoSuchMethodException e) {
      System.out.println(clazz.getSimpleName() + "." + name + " not found");
    }
  }

  public static void main(String[] args) {
    printMethod(Super.class, "m1");
    printMethod(Sub.class, "m2");
    printMethod(SubSub.class, "m1");
    printMethod(SubSub.class, "m2");
    printMethod(SubSub.class, "m3");
  }
}

public class AbstractMethodsTest extends MethodsTestBase {

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkMethods(CodeInspector inspector, Set<String> expected, Shrinker shrinker) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    MethodSubject m1 = superSubject.uniqueMethodWithName("m1");
    assertEquals(expected.contains("m1") && shrinker != Shrinker.R8Full, m1.isPresent());
    if (m1.isPresent()) {
      assertTrue(m1.isAbstract());
    }
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    MethodSubject m2 = subSubject.uniqueMethodWithName("m2");
    assertEquals(expected.contains("m2") && shrinker != Shrinker.R8Full, m2.isPresent());
    if (m2.isPresent()) {
      assertTrue(m2.isAbstract());
    }
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertEquals(expected.contains("m1"), subSubSubject.uniqueMethodWithName("m1").isPresent());
    assertEquals(expected.contains("m2"), subSubSubject.uniqueMethodWithName("m2").isPresent());
    assertEquals(expected.contains("m3"), subSubSubject.uniqueMethodWithName("m3").isPresent());
  }

  private void checkAllMethods(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1", "m2", "m3"), shrinker);
  }

  private void checkOnlyM1(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1"), shrinker);
  }

  private void checkOnlyM2(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m2"), shrinker);
  }

  private void checkOnlyM3(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m3"), shrinker);
  }

  public String allMethodsOutput(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 found",
          "Sub.m2 found",
          "SubSub.m1 found",
          "SubSub.m2 found",
          "SubSub.m3 found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m2 not found",
          "SubSub.m1 found",
          "SubSub.m2 found",
          "SubSub.m3 found");
    }
  }

  public String onlyM1Output(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 found",
          "Sub.m2 not found",
          "SubSub.m1 found",
          "SubSub.m2 not found",
          "SubSub.m3 not found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m2 not found",
          "SubSub.m1 found",
          "SubSub.m2 not found",
          "SubSub.m3 not found");
    }
  }

  public String onlyM2Output(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m2 found",
          "SubSub.m1 not found",
          "SubSub.m2 found",
          "SubSub.m3 not found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m2 not found",
          "SubSub.m1 not found",
          "SubSub.m2 found",
          "SubSub.m3 not found");
    }
  }

  public String onlyM3Output(Shrinker shrinker) {
    return StringUtils.lines(
        "Super.m1 not found",
        "Sub.m2 not found",
        "SubSub.m1 not found",
        "SubSub.m2 not found",
        "SubSub.m3 found");
  }

  @Test
  public void testKeepAllMethodsWithWildcard() throws Throwable {
    runTest("-keep class **.SubSub { *; }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepAllMethodsWithMethods() throws Throwable {
    runTest("-keep class **.SubSub { <methods>; }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepAllMethodsWithNameWildcard() throws Throwable {
    runTest("-keep class **.SubSub { void m*(); }", this::checkAllMethods, this::allMethodsOutput);
  }

  @Test
  public void testKeepM1() throws Throwable {
    runTest("-keep class **.SubSub { void m1(); }", this::checkOnlyM1, this::onlyM1Output);
  }

  @Test
  public void testKeepM2() throws Throwable {
    runTest("-keep class **.SubSub { void m2(); }", this::checkOnlyM2, this::onlyM2Output);
  }

  @Test
  public void testKeepM3() throws Throwable {
    runTest("-keep class **.SubSub { void m3(); }", this::checkOnlyM3, this::onlyM3Output);
  }

  @Test
  public void testKeepM1WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m1(); }", this::checkOnlyM1, this::onlyM1Output);
  }

  @Test
  public void testKeepM2WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m2(); }", this::checkOnlyM2, this::onlyM2Output);
  }

  @Test
  public void testKeepM3WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m3(); }", this::checkOnlyM3, this::onlyM3Output);
  }
}
