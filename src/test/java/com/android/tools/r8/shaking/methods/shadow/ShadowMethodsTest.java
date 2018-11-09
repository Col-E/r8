// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.shadow;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.methods.MethodsTestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import org.junit.Test;

@NeverMerge
class Super {
  public void m1() {}
  private void m2() {}
}

@NeverMerge
class Sub extends Super {
  public void m1() {}
  private void m2() {}
}

@NeverMerge
class SubSub extends Sub {
  public void m1() {}
  public void m2() {}
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
    printMethod(Sub.class, "m1");
    printMethod(SubSub.class, "m1");
    printMethod(Super.class, "m2");
    printMethod(Sub.class, "m2");
    printMethod(SubSub.class, "m2");
  }
}

public class ShadowMethodsTest extends MethodsTestBase {

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkAllMethods(CodeInspector inspector, Shrinker shrinker, String name) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertEquals(shrinker != Shrinker.R8Full, superSubject.uniqueMethodWithName(name).isPresent());
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertEquals(shrinker != Shrinker.R8Full, subSubject.uniqueMethodWithName(name).isPresent());
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertThat(subSubSubject.uniqueMethodWithName(name), isPresent());
  }

  private void checkAllMethods(CodeInspector inspector, Shrinker shrinker) {
    checkAllMethods(inspector, shrinker, "m1");
    checkAllMethods(inspector, shrinker, "m2");
  }

  @Override
  public String allMethodsOutput() {
    return StringUtils.lines("Super.m1 found", "Sub.m1 found", "SubSub.m1 found");
  }

  public String allMethodsOutputInCompatMode(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 found",
          "Sub.m1 found",
          "SubSub.m1 found",
          "Super.m2 found",
          "Sub.m2 found",
          "SubSub.m2 found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m1 not found",
          "SubSub.m1 found",
          "Super.m2 not found",
          "Sub.m2 not found",
          "SubSub.m2 found");
    }
  }

  public String m1OutputInCompatMode(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 found",
          "Sub.m1 found",
          "SubSub.m1 found",
          "Super.m2 not found",
          "Sub.m2 not found",
          "SubSub.m2 not found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m1 not found",
          "SubSub.m1 found",
          "Super.m2 not found",
          "Sub.m2 not found",
          "SubSub.m2 not found");
    }
  }

  public String m2OutputInCompatMode(Shrinker shrinker) {
    if (shrinker != Shrinker.R8Full) {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m1 not found",
          "SubSub.m1 not found",
          "Super.m2 found",
          "Sub.m2 found",
          "SubSub.m2 found");
    } else {
      return StringUtils.lines(
          "Super.m1 not found",
          "Sub.m1 not found",
          "SubSub.m1 not found",
          "Super.m2 not found",
          "Sub.m2 not found",
          "SubSub.m2 found");
    }
  }

  @Test
  public void testKeepAllMethodsWithWildcard() throws Throwable {
    runTest(
        "-keep class **.SubSub { *; }", this::checkAllMethods, this::allMethodsOutputInCompatMode);
  }

  @Test
  public void testKeepAllMethodsWithNameWildcard() throws Throwable {
    runTest(
        "-keep class **.SubSub { void m*(); }",
        this::checkAllMethods,
        this::allMethodsOutputInCompatMode);
  }

  @Test
  public void testKeepAllMethodsWithMethods() throws Throwable {
    runTest(
        "-keep class **.SubSub { <methods>; }",
        this::checkAllMethods,
        this::allMethodsOutputInCompatMode);
  }

  @Test
  public void testKeepM1() throws Throwable {
    runTest(
        "-keep class **.SubSub { void m1(); }",
        (inspector, shrinker) -> checkAllMethods(inspector, shrinker, "m1"),
        this::m1OutputInCompatMode);
  }

  @Test
  public void testKeepM1WithExtends() throws Throwable {
    runTest(
        "-keep class * extends **.Sub { void m1(); }",
        (inspector, shrinker) -> checkAllMethods(inspector, shrinker, "m1"),
        this::m1OutputInCompatMode);
  }

  @Test
  public void testKeepM2() throws Throwable {
    runTest(
        "-keep class **.SubSub { void m2(); }",
        (inspector, shrinker) -> checkAllMethods(inspector, shrinker, "m2"),
        this::m2OutputInCompatMode);
  }

  @Test
  public void testKeepM2WithExtends() throws Throwable {
    runTest(
        "-keep class * extends **.Sub { void m2(); }",
        (inspector, shrinker) -> checkAllMethods(inspector, shrinker, "m2"),
        this::m2OutputInCompatMode);
  }
}
