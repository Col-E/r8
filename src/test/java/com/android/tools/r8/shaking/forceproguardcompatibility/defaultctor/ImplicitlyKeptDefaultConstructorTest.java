// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility.defaultctor;

import static com.android.tools.r8.utils.codeinspector.Matchers.hasDefaultConstructor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.smali.ConstantFoldingTest.TriConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

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
    // The instance to check-cast is conditional on args to prevent the compiler from removing it.
    System.out.println((SubClass) (args.length == 42 ? new Object() : null));
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
  public static Object field = new Object();
}

class MainGetStaticFieldInitialized {

  public static void main(String[] args) {
    System.out.println(StaticFieldInitialized.field);
  }
}

@RunWith(Parameterized.class)
public class ImplicitlyKeptDefaultConstructorTest extends ProguardCompatibilityTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
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
      Class<?> mainClass, List<Class<?>> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(programClasses.size(), inspector.allClasses().size());
    inspector.forAllClasses(this::checkPresentWithDefaultConstructor);
  }

  private void checkAllClassesPresentOnlyMainWithDefaultConstructor(
      Class<?> mainClass, List<Class<?>> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(programClasses.size(), inspector.allClasses().size());
    checkPresentWithDefaultConstructor(inspector.clazz(mainClass));
    inspector.allClasses()
        .stream()
        .filter(subject -> !subject.getOriginalName().equals(mainClass.getCanonicalName()))
        .forEach(this::checkPresentWithoutDefaultConstructor);
  }

  private void checkOnlyMainPresent(
      Class<?> mainClass, List<Class<?>> programClasses, CodeInspector inspector) {
    assert programClasses.contains(mainClass);
    assertEquals(1, inspector.allClasses().size());
    inspector.forAllClasses(this::checkPresentWithDefaultConstructor);
  }

  private void runTest(
      Class<?> mainClass,
      List<Class<?>> programClasses,
      String proguardConfiguration,
      TriConsumer<Class, List<Class<?>>, CodeInspector> r8Checker,
      TriConsumer<Class, List<Class<?>>, CodeInspector> proguardChecker)
      throws Exception {
    CodeInspector inspector =
        inspectR8CompatResult(programClasses, proguardConfiguration, null, parameters.getBackend());
    r8Checker.accept(mainClass, programClasses, inspector);

    if (isRunProguard()) {
      inspector = inspectProguard6Result(programClasses, proguardConfiguration);
      proguardChecker.accept(mainClass, programClasses, inspector);
      inspector = inspectProguard6AndD8Result(programClasses, proguardConfiguration, null);
      proguardChecker.accept(mainClass, programClasses, inspector);
    }
  }

  private void runTest(
      Class<?> mainClass,
      List<Class<?>> programClasses,
      String proguardConfiguration,
      TriConsumer<Class, List<Class<?>>, CodeInspector> checker)
      throws Exception {
    runTest(mainClass, programClasses, proguardConfiguration, checker, checker);
  }

  @Test
  public void testInstantiation() throws Exception {
    // A new instance call keeps the default constructor.
    Class<?> mainClass = MainInstantiationSubClass.class;
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
    Class<?> mainClass = MainGetClassSubClass.class;
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
    Class<?> mainClass = MainCheckCastSubClass.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, SuperClass.class, SubClass.class),
        keepMainProguardConfiguration(mainClass),
        (ignoreMainClass, ignoreProgramClasses, inspector) -> {
          assertTrue(inspector.clazz(mainClass).isPresent());
          assertTrue(inspector.clazz(SubClass.class).isPresent());
          // SuperClass may have been merged with SubClass.
          inspector.forAllClasses(this::checkPresentWithDefaultConstructor);
        });
  }

  @Test
  public void testCheckCastWithoutInlining() throws Exception {
    // Reference to the class constant keeps the default constructor.
    Class<?> mainClass = MainCheckCastSubClass.class;
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
    Class<?> mainClass = MainClassForNameSubClass.class;
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
    Class<?> mainClass = MainGetStaticFieldNotInitialized.class;
    String proguardConfiguration =
        keepMainProguardConfiguration(
            mainClass,
            ImmutableList.of(
                "-keep class " + StaticFieldNotInitialized.class.getTypeName() + " {", "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithInitializationStaticClassKept() throws Exception {
    // An explicit keep rule keeps the default constructor.
    Class<?> mainClass = MainGetStaticFieldInitialized.class;
    String proguardConfiguration =
        keepMainProguardConfiguration(
            mainClass,
            ImmutableList.of(
                "-keep class " + StaticFieldInitialized.class.getTypeName() + " {", "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticClassKept() throws Exception {
    // An explicit keep rule keeps the default constructor.
    Class<?> mainClass = MainCallStaticMethod.class;
    String proguardConfiguration =
        keepMainProguardConfiguration(
            mainClass,
            ImmutableList.of("-keep class " + StaticMethod.class.getTypeName() + " {", "}"));
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        proguardConfiguration,
        this::checkAllClassesPresentWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithoutInitialization() throws Exception {
    Class<?> mainClass = MainGetStaticFieldNotInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        keepMainProguardConfiguration(mainClass),
        this::checkOnlyMainPresent,
        this::checkOnlyMainPresent);
  }

  @Test
  public void testStaticFieldWithInitialization() throws Exception {
    Class<?> mainClass = MainGetStaticFieldInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        keepMainProguardConfiguration(mainClass),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticClassNotKept() throws Exception {
    // Due to inlining only the main method is left.
    Class<?> mainClass = MainCallStaticMethod.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        keepMainProguardConfiguration(mainClass),
        this::checkOnlyMainPresent);
  }

  @Test
  public void testStaticFieldWithoutInitializationWithoutInlining() throws Exception {
    Class<?> mainClass = MainGetStaticFieldNotInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldNotInitialized.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticFieldWithInitializationWithoutInlining() throws Exception {
    Class<?> mainClass = MainGetStaticFieldInitialized.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticFieldInitialized.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }

  @Test
  public void testStaticMethodStaticWithoutInlining() throws Exception {
    Class<?> mainClass = MainCallStaticMethod.class;
    runTest(
        mainClass,
        ImmutableList.of(mainClass, StaticMethod.class),
        keepMainProguardConfiguration(mainClass, ImmutableList.of("-dontoptimize")),
        this::checkAllClassesPresentOnlyMainWithDefaultConstructor);
  }
}
