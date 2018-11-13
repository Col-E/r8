// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.desugar.dflt;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.shaking.methods.MethodsTestBase.Shrinker;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.Test;

@NeverMerge
interface SuperIface {
  default void m1() {}
}

@NeverMerge
interface SubIface extends SuperIface {
  default void m2() {}
}

@NeverMerge
interface SubSubIface extends SubIface {
  default void m3() {}
}

@NeverMerge
class Impl implements SubSubIface {
  void m4() {}
}

@NeverMerge
class SubImpl extends Impl {
  void m5() {}
}

@NeverMerge
class SubSubImpl extends SubImpl {
  void m6() {}
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
    printMethod(SuperIface.class, "m1");
    printMethod(SubIface.class, "m2");
    printMethod(SubSubIface.class, "m3");
    printMethod(Impl.class, "m1");
    printMethod(Impl.class, "m2");
    printMethod(Impl.class, "m3");
    printMethod(Impl.class, "m4");
    printMethod(SubImpl.class, "m1");
    printMethod(SubImpl.class, "m2");
    printMethod(SubImpl.class, "m3");
    printMethod(SubImpl.class, "m4");
    printMethod(SubImpl.class, "m5");
    printMethod(SubSubImpl.class, "m1");
    printMethod(SubSubImpl.class, "m2");
    printMethod(SubSubImpl.class, "m3");
    printMethod(SubSubImpl.class, "m4");
    printMethod(SubSubImpl.class, "m5");
    printMethod(SubSubImpl.class, "m6");
  }
}

public class DefaultMethodsTest extends TestBase {

  public Collection<Class<?>> getClasses() {
    return ImmutableSet.of(
        SuperIface.class,
        SubIface.class,
        SubSubIface.class,
        Impl.class,
        SubImpl.class,
        SubSubImpl.class,
        getMainClass());
  }

  public Class<?> getMainClass() {
    return Main.class;
  }

  public void testOnR8(
      List<String> keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    testForR8(Backend.DEX)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(i -> inspector.accept(i, Shrinker.R8Full))
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void testOnR8Compat(
      List<String> keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    testForR8Compat(Backend.DEX)
        .setMinApi(AndroidApiLevel.L)
        .enableMergeAnnotations()
        .addProgramClasses(getClasses())
        .addKeepRules(keepRules)
        .compile()
        .inspect(i -> inspector.accept(i, Shrinker.R8Compat))
        .run(getMainClass())
        .assertSuccessWithOutput(expected);
  }

  public void runTest(
      List<String> keepRules,
      BiConsumer<CodeInspector, Shrinker> inspector,
      Function<Shrinker, String> expected)
      throws Throwable {
    // TODO(sgjesse): Maybe add test with proguard and desuagar.
    testOnR8Compat(keepRules, inspector, expected.apply(Shrinker.R8Compat));
    testOnR8(keepRules, inspector, expected.apply(Shrinker.R8Full));
  }

  public void runTest(
      String keepRules, BiConsumer<CodeInspector, Shrinker> inspector, String expected)
      throws Throwable {
    runTest(keepRules, inspector, (unused) -> expected);
  }

  public void runTest(
      String keepRules,
      BiConsumer<CodeInspector, Shrinker> inspector,
      Function<Shrinker, String> expected)
      throws Throwable {
    runTest(
        ImmutableList.of(
            keepRules, keepMainProguardConfiguration(getMainClass()), "-dontobfuscate"),
        inspector,
        expected);
  }

  private void checkMethods(
      CodeInspector inspector,
      Set<String> expected,
      boolean interfaceMethodsKept,
      Shrinker shrinker) {
    ClassSubject superIfaceSubject = inspector.clazz(SuperIface.class);
    assertThat(superIfaceSubject, isPresent());
    if (interfaceMethodsKept) {
      assertEquals(
          expected.contains("m1"), superIfaceSubject.uniqueMethodWithName("m1").isPresent());
      if (expected.contains("m1")) {
        assertThat(superIfaceSubject.uniqueMethodWithName("m1"), isAbstract());
      }
    }
    ClassSubject subIfaceSubject = inspector.clazz(SubIface.class);
    assertThat(subIfaceSubject, isPresent());
    if (interfaceMethodsKept) {
      assertEquals(expected.contains("m2"), subIfaceSubject.uniqueMethodWithName("m2").isPresent());
      if (expected.contains("m2")) {
        assertThat(subIfaceSubject.uniqueMethodWithName("m2"), isAbstract());
      }
    }
    ClassSubject subSubIfaceSubject = inspector.clazz(SubSubIface.class);
    assertThat(subSubIfaceSubject, isPresent());
    if (interfaceMethodsKept) {
      assertEquals(
          expected.contains("m3"), subSubIfaceSubject.uniqueMethodWithName("m3").isPresent());
      if (expected.contains("m3")) {
        assertThat(subSubIfaceSubject.uniqueMethodWithName("m3"), isAbstract());
      }
    }
    ClassSubject implSubject = inspector.clazz(Impl.class);
    assertThat(implSubject, isPresent());
    if (interfaceMethodsKept) {
      assertEquals(expected.contains("m1"), implSubject.uniqueMethodWithName("m1").isPresent());
      assertEquals(expected.contains("m2"), implSubject.uniqueMethodWithName("m2").isPresent());
      assertEquals(expected.contains("m3"), implSubject.uniqueMethodWithName("m3").isPresent());
    }
    assertEquals(expected.contains("m4"), implSubject.uniqueMethodWithName("m4").isPresent());
    ClassSubject subImplSubject = inspector.clazz(SubImpl.class);
    assertThat(subImplSubject, isPresent());
    assertThat(subImplSubject.uniqueMethodWithName("m1"), not(isPresent()));
    assertThat(subImplSubject.uniqueMethodWithName("m2"), not(isPresent()));
    assertThat(subImplSubject.uniqueMethodWithName("m3"), not(isPresent()));
    assertThat(subImplSubject.uniqueMethodWithName("m4"), not(isPresent()));
    assertEquals(expected.contains("m5"), subImplSubject.uniqueMethodWithName("m5").isPresent());
    ClassSubject subSubImplSubject = inspector.clazz(SubSubImpl.class);
    assertThat(subSubImplSubject, isPresent());
    assertThat(subSubImplSubject.uniqueMethodWithName("m1"), not(isPresent()));
    assertThat(subSubImplSubject.uniqueMethodWithName("m2"), not(isPresent()));
    assertThat(subSubImplSubject.uniqueMethodWithName("m3"), not(isPresent()));
    assertThat(subSubImplSubject.uniqueMethodWithName("m4"), not(isPresent()));
    assertThat(subSubImplSubject.uniqueMethodWithName("m5"), not(isPresent()));
    assertEquals(expected.contains("m6"), subSubImplSubject.uniqueMethodWithName("m6").isPresent());
  }

  private void checkAllMethodsInterfacesKept(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1", "m2", "m3", "m4", "m5", "m6"), true, shrinker);
  }

  private void checkAllMethodsInterfacesNotKept(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1", "m2", "m3", "m4", "m5", "m6"), false, shrinker);
  }

  private void checkOnlyM1(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1"), true, shrinker);
  }

  private void checkOnlyM1InterfacesNotKept(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m1"), false, shrinker);
  }

  private void checkOnlyM2(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m2"), true, shrinker);
  }

  private void checkOnlyM3(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m3"), true, shrinker);
  }

  private void checkOnlyM4(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m4"), true, shrinker);
  }

  private void checkOnlyM5(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m5"), true, shrinker);
  }

  private void checkOnlyM6(CodeInspector inspector, Shrinker shrinker) {
    checkMethods(inspector, ImmutableSet.of("m6"), true, shrinker);
  }

  public String allMethodsOutput() {
    return StringUtils.lines(
        "SuperIface.m1 found",
        "SubIface.m2 found",
        "SubSubIface.m3 found",
        "Impl.m1 found",
        "Impl.m2 found",
        "Impl.m3 found",
        "Impl.m4 found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 found");
  }

  public String allMethodsOutputInterfacesNotKept() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 found");
  }

  public String onlyM1Output() {
    return StringUtils.lines(
        "SuperIface.m1 found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM1OutputInterfacesNotKept() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM2Output() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 found",
        "Impl.m3 not found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM3Output() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM4Output() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM5Output() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 not found");
  }

  public String onlyM6Output() {
    return StringUtils.lines(
        "SuperIface.m1 not found",
        "SubIface.m2 not found",
        "SubSubIface.m3 not found",
        "Impl.m1 not found",
        "Impl.m2 not found",
        "Impl.m3 not found",
        "Impl.m4 not found",
        "SubImpl.m1 not found",
        "SubImpl.m2 not found",
        "SubImpl.m3 not found",
        "SubImpl.m4 not found",
        "SubImpl.m5 not found",
        "SubSubImpl.m1 not found",
        "SubSubImpl.m2 not found",
        "SubSubImpl.m3 not found",
        "SubSubImpl.m4 not found",
        "SubSubImpl.m5 not found",
        "SubSubImpl.m6 found");
  }

  @Test
  public void testKeepAllMethodsWithWildcard() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { *; }",
        this::checkAllMethodsInterfacesKept,
        allMethodsOutput());
  }

  @Test
  public void testKeepAllMethodsImplementationOnlyWithWildcard() throws Throwable {
    // TODO(118851616): Is this correct, that m1(), m2() and m3() is not desugared when the
    // interfaces with the default methods are not explicitly kept?
    runTest(
        "-keep class **.SubSubImpl { *; }",
        this::checkAllMethodsInterfacesNotKept,
        allMethodsOutputInterfacesNotKept());
  }

  @Test
  public void testKeepAllMethodsWithMethods() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { <methods>; }",
        this::checkAllMethodsInterfacesKept,
        allMethodsOutput());
  }

  @Test
  public void testKeepAllMethodsWithNameWildcard() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { void m*(); }",
        this::checkAllMethodsInterfacesKept,
        allMethodsOutput());
  }

  @Test
  public void testKeepM1() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { void m1(); }", this::checkOnlyM1, onlyM1Output());
  }

  @Test
  public void testKeepM1ImplementationOnly() throws Throwable {
    // TODO(118851616): Is this correct, that m1() is not desugared when the interface with
    // the default method is not explicitly kept?
    runTest(
        "-keep class **.SubSubImpl { void m1(); }",
        this::checkOnlyM1InterfacesNotKept,
        onlyM1OutputInterfacesNotKept());
  }

  @Test
  public void testKeepM2() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { void m2(); }", this::checkOnlyM2, onlyM2Output());
  }

  @Test
  public void testKeepM3() throws Throwable {
    runTest(
        "-keep class **.*Iface, **.SubSubImpl { void m3(); }", this::checkOnlyM3, onlyM3Output());
  }

  @Test
  public void testKeepM4() throws Throwable {
    runTest("-keep class **.SubSubImpl { void m4(); }", this::checkOnlyM4, onlyM4Output());
  }

  @Test
  public void testKeepM5() throws Throwable {
    runTest("-keep class **.SubSubImpl { void m5(); }", this::checkOnlyM5, onlyM5Output());
  }

  @Test
  public void testKeepM6() throws Throwable {
    runTest("-keep class **.SubSubImpl { void m6(); }", this::checkOnlyM6, onlyM6Output());
  }

  @Test
  public void testKeepM1WithExtends() throws Throwable {
    runTest(
        "-keep class * extends **.SubImpl { void m1(); } -keep class **.*Iface { void m1(); }",
        this::checkOnlyM1,
        onlyM1Output());
  }

  @Test
  public void testKeepM2WithExtends() throws Throwable {
    runTest(
        "-keep class * extends **.SubImpl { void m2(); } -keep class **.*Iface { void m2(); }",
        this::checkOnlyM2,
        onlyM2Output());
  }

  @Test
  public void testKeepM3WithExtends() throws Throwable {
    runTest(
        "-keep class * extends **.SubImpl { void m3(); } -keep class **.*Iface { void m3(); }",
        this::checkOnlyM3,
        onlyM3Output());
  }

  @Test
  public void testKeepM4WithExtends() throws Throwable {
    runTest("-keep class * extends **.SubImpl { void m4(); }", this::checkOnlyM4, onlyM4Output());
  }

  @Test
  public void testKeepM5WithExtends() throws Throwable {
    runTest("-keep class * extends **.SubImpl { void m5(); }", this::checkOnlyM5, onlyM5Output());
  }

  @Test
  public void testKeepM6WithExtends() throws Throwable {
    runTest("-keep class * extends **.SubImpl { void m6(); }", this::checkOnlyM6, onlyM6Output());
  }
}
