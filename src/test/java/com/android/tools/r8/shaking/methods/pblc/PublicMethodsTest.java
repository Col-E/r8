// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.methods.pblc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.shaking.methods.MethodsTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;

@NeverMerge
class Super {
  public void m1() {}
}

@NeverMerge
class Sub extends Super {
  public void m2() {}
}

@NeverMerge
class SubSub extends Sub {
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
    printMethod(SubSub.class, "m3");
  }
}

public class PublicMethodsTest extends MethodsTestBase {

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(Super.class, Sub.class, SubSub.class, getMainClass());
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private void checkConstructors(CodeInspector inspector, Set<String> expected) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertEquals(expected.contains("Super"), superSubject.init(ImmutableList.of()).isPresent());
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertEquals(expected.contains("Sub"), subSubject.init(ImmutableList.of()).isPresent());
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertEquals(expected.contains("SubSub"), subSubSubject.init(ImmutableList.of()).isPresent());
  }

  private void checkNoConstructorsInFullMode(CodeInspector inspector, Shrinker shrinker) {
    checkConstructors(
        inspector,
        shrinker == Shrinker.R8Full
            ? ImmutableSet.of()
            : ImmutableSet.of("Super", "Sub", "SubSub"));
  }

  private void checkMethods(CodeInspector inspector, Set<String> expected) {
    ClassSubject superSubject = inspector.clazz(Super.class);
    assertThat(superSubject, isPresent());
    assertEquals(expected.contains("m1"), superSubject.uniqueMethodWithName("m1").isPresent());
    ClassSubject subSubject = inspector.clazz(Sub.class);
    assertThat(subSubject, isPresent());
    assertEquals(expected.contains("m2"), subSubject.uniqueMethodWithName("m2").isPresent());
    ClassSubject subSubSubject = inspector.clazz(SubSub.class);
    assertThat(subSubSubject, isPresent());
    assertEquals(expected.contains("m3"), subSubSubject.uniqueMethodWithName("m3").isPresent());
  }

  private void checkAllMethodsAndAllConstructors(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1", "m2", "m3"));
    checkConstructors(inspector, ImmutableSet.of("Super", "Sub", "SubSub"));
  }

  private void checkAllMethodsNoConstructorsInFullMode(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1", "m2", "m3"));
    checkNoConstructorsInFullMode(inspector, shrinker);
  }

  private void checkOnlyM1(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1"));
    checkNoConstructorsInFullMode(inspector, shrinker);
  }

  private void checkM1AndAllConstructors(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1"));
    checkConstructors(inspector, ImmutableSet.of("Super", "Sub", "SubSub"));
  }

  private void checkOnlyM2(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m2"));
    checkNoConstructorsInFullMode(inspector, shrinker);
  }

  private void checkM2AndAllConstructors(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m2"));
    checkConstructors(inspector, ImmutableSet.of("Super", "Sub", "SubSub"));
  }

  private void checkOnlyM3(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m3"));
    checkNoConstructorsInFullMode(inspector, shrinker);
  }

  private void checkM3AndAllConstructors(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m3"));
    checkConstructors(inspector, ImmutableSet.of("Super", "Sub", "SubSub"));
  }

  @Test
  public void testKeepAllMethodsWithWildcard() throws Throwable {
    runTest(
        "-keep class **.SubSub { *; }",
        this::checkAllMethodsAndAllConstructors,
        allMethodsOutput());
  }

  @Test
  public void testKeepAllMethodsWithMethods() throws Throwable {
    runTest(
        "-keep class **.SubSub { <methods>; }",
        this::checkAllMethodsAndAllConstructors,
        allMethodsOutput());
  }

  @Test
  public void testKeepAllMethodsWithNameWildcard() throws Throwable {
    runTest(
        "-keep class **.SubSub { void m*(); }",
        this::checkAllMethodsNoConstructorsInFullMode,
        allMethodsOutput());
  }

  @Test
  public void testKeepDefaultConstructorAndAllMethodsWithNameWildcard() throws Throwable {
    runTest(
        "-keep class **.SubSub { <init>(); void m*(); }",
        this::checkAllMethodsAndAllConstructors,
        allMethodsOutput());
  }

  @Test
  public void testKeepM1() throws Throwable {
    runTest("-keep class **.SubSub { void m1(); }", this::checkOnlyM1, onlyM1Output());
  }

  @Test
  public void testKeepDefaultConstructorAndKeepM1() throws Throwable {
    runTest(
        "-keep class **.SubSub { <init>(); void m1(); }",
        this::checkM1AndAllConstructors,
        onlyM1Output());
  }

  @Test
  public void testKeepM2() throws Throwable {
    runTest("-keep class **.SubSub { void m2(); }", this::checkOnlyM2, onlyM2Output());
  }

  @Test
  public void testKeepDefaultConstructorAndKeepM2() throws Throwable {
    runTest(
        "-keep class **.SubSub { <init>(); void m2(); }",
        this::checkM2AndAllConstructors,
        onlyM2Output());
  }

  @Test
  public void testKeepM3() throws Throwable {
    runTest("-keep class **.SubSub { void m3(); }", this::checkOnlyM3, onlyM3Output());
  }

  @Test
  public void testKeepDefaultConstructorAndKeepM3() throws Throwable {
    runTest(
        "-keep class **.SubSub { <init>(); void m3(); }",
        this::checkM3AndAllConstructors,
        onlyM3Output());
  }

  @Test
  public void testKeepM1WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m1(); }", this::checkOnlyM1, onlyM1Output());
  }

  @Test
  public void testKeepM2WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m2(); }", this::checkOnlyM2, onlyM2Output());
  }

  @Test
  public void testKeepM3WithExtends() throws Throwable {
    runTest("-keep class * extends **.Sub { void m3(); }", this::checkOnlyM3, onlyM3Output());
  }
}
