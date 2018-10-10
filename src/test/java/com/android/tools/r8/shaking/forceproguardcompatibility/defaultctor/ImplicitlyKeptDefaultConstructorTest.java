// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor;

import static com.android.tools.r8.utils.codeinspector.Matchers.hasDefaultConstructor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.smali.ConstantFoldingTest.TriConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class SuperClass {

}

class SubClass extends SuperClass {

}

class MainInstantiationSubClass {

  public static void main(String[] args) {
    new SubClass();
  }
}

class MainGetClassSubClass {

  public static void main(String[] args) {
    System.out.println(SubClass.class);
  }
}

class MainCheckCastSubClass {

  public static void main(String[] args) {
    System.out.println((SubClass) null);
  }
}

class MainClassForNameSubClass {

  public static void main(String[] args) throws Exception{
    System.out.println(Class.forName(
        "com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor.SubClass"));
  }
}

class StaticFieldNotInitialized {
  public static int field;
}

class MainGetStaticFieldNotInitialized {

  public static void main(String[] args) {
    System.out.println(StaticFieldNotInitialized.field);
  }
}

class StaticMethod {
  public static int method() {
    return 1;
  };
}

class MainCallStaticMethod {

  public static void main(String[] args) {
    System.out.println(StaticMethod.method());
  }
}

class StaticFieldInitialized {
  public static int field = 1;
}

class MainGetStaticFieldInitialized {

  public static void main(String[] args) {
    System.out.println(StaticFieldInitialized.field);
  }
}

@RunWith(Parameterized.class)
public class ImplicitlyKeptDefaultConstructorTest extends ProguardCompatibilityTestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public ImplicitlyKeptDefaultConstructorTest(Backend backend) {
    this.backend = backend;
  }

  private void checkPresentWithDefaultConstructor(ClassSubject clazz) {
    assertThat(clazz, isPresent());
    assertThat(clazz, hasDefaultConstructor());
  }

  private void checkPresentWithoutDefaultConstructor(ClassSubject clazz) {
    assertThat(clazz, isPresent());
    assertThat(clazz, not(hasDefaultConstructor()));
  }

  private void checkAllClassesPresentWithDefaultConstructor(
      Class mainClass, List<Class> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(programClasses.size(), inspector.allClasses().size());
    inspector.forAllClasses(this::checkPresentWithDefaultConstructor);
  }

  private void checkAllClassesPresentOnlyMainWithDefaultConstructor(
      Class mainClass, List<Class> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(programClasses.size(), inspector.allClasses().size());
    checkPresentWithDefaultConstructor(inspector.clazz(mainClass));
    inspector.allClasses()
        .stream()
        .filter(subject -> !subject.getOriginalName().equals(mainClass.getCanonicalName()))
        .forEach(this::checkPresentWithoutDefaultConstructor);
  }

  private void checkOnlyMainPresent(
      Class mainClass, List<Class> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(1, inspector.allClasses().size());
    inspector.forAllClasses(this::checkPresentWithDefaultConstructor);
  }

  private void runTest(
      Class mainClass, List<Class> programClasses, String proguardConfiguration,
      TriConsumer<Class, List<Class>, CodeInspector> r8Checker,
      TriConsumer<Class, List<Class>, CodeInspector> proguardChecker) throws Exception {
    CodeInspector inspector =
        inspectR8CompatResult(programClasses, proguardConfiguration, null, backend);
    r8Checker.accept(mainClass, programClasses, inspector);

    if (isRunProguard()) {
      inspector = inspectProguard6Result(programClasses, proguardConfiguration);
      proguardChecker.accept(mainClass, programClasses, inspector);
      inspector = inspectProguard6AndD8Result(programClasses, proguardConfiguration, null);
      proguardChecker.accept(mainClass, programClasses, inspector);
    }
  }

  private void runTest(
      Class mainClass, List<Class> programClasses, String proguardConfiguration,
      TriConsumer<Class, List<Class>, CodeInspector> checker) throws Exception {
    runTest(mainClass, programClasses, proguardConfiguration, checker, checker);
  }

  @Test
  public void testInstantiation() throws Exception {
    // A new instance call keeps the default constructor.
    Class mainClass = MainInstantiationSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        // Prevent SuperClass from being merged into SubClass and keep both
        // SuperClass and SubClass after instantiation is inlined.
        keepMainProguardConfiguration(mainClass, ImmutableList.of(
            "-keep class **.SuperClass", "-keep class **.SubClass")),
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testGetClass() throws Exception {
    // Reference to the class constant keeps the default constructor.
    Class mainClass = MainGetClassSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        // Prevent SuperClass from being merged into SubClass.
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-keep class **.SuperClass")),
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testCheckCast() throws Exception {
    // Reference to the class constant keeps the default constructor.
    Class mainClass = MainCheckCastSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        keepMainProguardConfiguration(mainClass),
        this::checkOnlyMainPresent);
  }


  @Test
  public void testCheckCastWithoutInlining() throws Exception {
    // Reference to the class constant keeps the default constructor.
    Class mainClass = MainCheckCastSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        // Prevent SuperClass from being merged into SubClass.
        keepMainProguardConfiguration(
            mainClass, ImmutableList.of("-dontoptimize", "-keep class **.SuperClass")),
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testClassForName() throws Exception {
    // Class.forName with a constant string keeps the default constructor.
    Class mainClass = MainClassForNameSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        // Prevent SuperClass from being merged into SubClass.
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-keep class **.SuperClass")),
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithoutInitializationStaticClassKept() throws Exception {
    // An explicit keep rule keeps the default constructor.
    Class mainClass = MainGetStaticFieldNotInitialized.class;
    String proguardConfiguration = keepMainProguardConfiguration(
        mainClass,
        ImmutableList.of(
            "-keep class " + getJavacGeneratedClassName(StaticFieldNotInitialized.class) + " {",
            "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithInitializationStaticClassKept() throws Exception {
    // An explicit keep rule keeps the default constructor.
    Class mainClass = MainGetStaticFieldInitialized.class;
    String proguardConfiguration = keepMainProguardConfiguration(
        mainClass,
        ImmutableList.of(
            "-keep class " + getJavacGeneratedClassName(StaticFieldInitialized.class) + " {",
            "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticClassKept() throws Exception {
    // An explicit keep rule keeps the default constructor.
    Class mainClass = MainCallStaticMethod.class;
    String proguardConfiguration = keepMainProguardConfiguration(
        mainClass,
        ImmutableList.of(
            "-keep class " + getJavacGeneratedClassName(StaticMethod.class) + " {",
            "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithoutInitialization() throws Exception {
    Class mainClass = MainGetStaticFieldNotInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        keepMainProguardConfiguration(mainClass),
        // TODO(74379749): Proguard does not keep the class with the un-initialized static field.
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor,
        this::checkOnlyMainPresent);
  }

  @Test
  public void testStaticFieldWithInitialization() throws Exception {
    Class mainClass = MainGetStaticFieldInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        keepMainProguardConfiguration(mainClass),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticClassNotKept() throws Exception {
    // Due to inlining only the main method is left.
    Class mainClass = MainCallStaticMethod.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        keepMainProguardConfiguration(mainClass),
        this::checkOnlyMainPresent);
  }

  @Test
  public void testStaticFieldWithoutInitializationWithoutInlining() throws Exception {
    Class mainClass = MainGetStaticFieldNotInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithInitializationWithoutInlining() throws Exception {
    Class mainClass = MainGetStaticFieldInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticWithoutInlining() throws Exception {
    Class mainClass = MainCallStaticMethod.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }
}
