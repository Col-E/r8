// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.shaking.forceproguardcompatibility.TestMain.MentionedClass;
import com.android.tools.r8.shaking.forceproguardcompatibility.defaultmethods.ClassImplementingInterface;
import com.android.tools.r8.shaking.forceproguardcompatibility.defaultmethods.InterfaceWithDefaultMethods;
import com.android.tools.r8.shaking.forceproguardcompatibility.defaultmethods.TestClass;
import com.android.tools.r8.shaking.forceproguardcompatibility.keepattributes.TestKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ForceProguardCompatibilityTest extends TestBase {

  private final TestParameters parameters;
  private final boolean forceProguardCompatibility;

  @Parameters(name = "{0}, compat:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.O)
            .build(),
        BooleanUtils.values());
  }

  public ForceProguardCompatibilityTest(
      TestParameters parameters, boolean forceProguardCompatibility) {
    this.parameters = parameters;
    this.forceProguardCompatibility = forceProguardCompatibility;
  }

  private void test(Class<?> mainClass, Class<?> mentionedClass) throws Exception {
    test(mainClass, mentionedClass, null);
  }

  private void test(
      Class<?> mainClass,
      Class<?> mentionedClass,
      ThrowableConsumer<R8CompatTestBuilder> configuration)
      throws Exception {
    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addDontObfuscate()
            .allowAccessModification()
            .addProgramClasses(mainClass, mentionedClass)
            .addKeepMainRule(mainClass)
            .apply(configuration)
            .setMinApi(parameters)
            .compile()
            .inspector();
    assertThat(inspector.clazz(mainClass), isPresent());
    ClassSubject clazz = inspector.clazz(mentionedClass);
    assertThat(clazz, isPresent());
    MethodSubject defaultInitializer = clazz.method(MethodSignature.initializer(new String[]{}));
    assertEquals(forceProguardCompatibility, defaultInitializer.isPresent());
  }

  @Test
  public void testKeepDefaultInitializer() throws Exception {
    test(
        TestMain.class,
        TestMain.MentionedClass.class,
        testBuilder ->
            testBuilder.addProgramClasses(
                TestMain.MentionedClassWithAnnotation.class, TestAnnotation.class));
  }

  @Test
  public void testKeepDefaultInitializerArrayType() throws Exception {
    test(TestMainArrayType.class, TestMainArrayType.MentionedClass.class);
  }

  private void runAnnotationsTest(boolean keepAnnotations) throws Exception {
    // Add application classes including the annotation class.
    Class<?> mainClass = TestMain.class;
    Class<?> mentionedClassWithAnnotations = TestMain.MentionedClassWithAnnotation.class;
    Class<?> annotationClass = TestAnnotation.class;

    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(
                mainClass, MentionedClass.class, mentionedClassWithAnnotations, annotationClass)
            .addKeepMainRule(mainClass)
            .allowAccessModification()
            .addKeepClassAndMembersRules(annotationClass)
            .map(b -> keepAnnotations ? b.addKeepAttributes("*Annotation*") : b)
            .setMinApi(parameters)
            .compile()
            .inspector();

    assertThat(inspector.clazz(mainClass), isPresent());
    ClassSubject clazz = inspector.clazz(mentionedClassWithAnnotations);
    assertThat(clazz, isPresent());

    // The test contains only a member class so the enclosing-method attribute will be null.
    assertTrue(clazz.getDexProgramClass().getInnerClasses().isEmpty());

    if (keepAnnotations) {
      assertThat(
          clazz.annotation(annotationClass.getCanonicalName()),
          onlyIf(forceProguardCompatibility, isPresent()));
    } else {
      assertThat(clazz.annotation(annotationClass.getCanonicalName()), isAbsent());
    }
  }

  @Test
  public void testAnnotations() throws Exception {
    runAnnotationsTest(true);
    runAnnotationsTest(false);
  }

  private void runDefaultConstructorTest(Class<?> testClass, boolean hasDefaultConstructor)
      throws Exception {

    List<String> proguardConfig = ImmutableList.of(
        "-keep class " + testClass.getCanonicalName() + " {",
        "  public void method();",
        "}");

    GraphInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(testClass)
            .addKeepRules(proguardConfig)
            .enableGraphInspector()
            .setMinApi(parameters)
            .compile()
            .graphInspector();

    QueryNode clazzNode = inspector.clazz(classFromClass(testClass)).assertPresent();
    if (hasDefaultConstructor) {
      QueryNode initNode = inspector.method(methodFromMethod(testClass.getConstructor()));
      if (forceProguardCompatibility) {
        initNode.assertPureCompatKeptBy(clazzNode);
      } else {
        initNode.assertAbsent();
      }
    }

    if (isRunProguard()) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      Path proguardMapFile = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(testClass),
          proguardedJar, proguardConfigFile, proguardMapFile);
    }
  }

  @Test
  public void testDefaultConstructor() throws Exception {
    runDefaultConstructorTest(TestClassWithDefaultConstructor.class, true);
    runDefaultConstructorTest(TestClassWithoutDefaultConstructor.class, false);
  }

  public void testCheckCast(
      Class<?> mainClass, Class<?> instantiatedClass, boolean containsCheckCast) throws Exception {
    List<String> proguardConfig =
        ImmutableList.of(
            "-keep class " + mainClass.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}",
            "-dontobfuscate");

    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(mainClass, instantiatedClass)
            .addKeepRules(proguardConfig)
            .setMinApi(parameters)
            .compile()
            .inspector();

    assertTrue(inspector.clazz(mainClass).isPresent());
    ClassSubject clazz = inspector.clazz(instantiatedClass);
    assertEquals(containsCheckCast, clazz.isPresent());
    if (clazz.isPresent()) {
      assertEquals(forceProguardCompatibility && containsCheckCast, !clazz.isAbstract());
    }

    if (isRunProguard()) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(ImmutableList.of(mainClass, instantiatedClass)),
          proguardedJar, proguardConfigFile, null);
      CodeInspector proguardInspector = new CodeInspector(readJar(proguardedJar));
      assertTrue(proguardInspector.clazz(mainClass).isPresent());
      assertEquals(
          containsCheckCast, proguardInspector.clazz(instantiatedClass).isPresent());
    }
  }

  @Test
  public void checkCastTest() throws Exception {
    testCheckCast(
        TestMainWithCheckCast.class,
        TestClassWithDefaultConstructor.class,
        forceProguardCompatibility);
    testCheckCast(TestMainWithoutCheckCast.class, TestClassWithDefaultConstructor.class, false);
  }

  public void testClassForName(boolean allowObfuscation) throws Exception {
    Class<?> mainClass = TestMainWithClassForName.class;
    Class<?> forNameClass1 = TestClassWithDefaultConstructor.class;
    Class<?> forNameClass2 = TestClassWithoutDefaultConstructor.class;
    List<Class<?>> forNameClasses = ImmutableList.of(forNameClass1, forNameClass2);
    ImmutableList.Builder<String> proguardConfigurationBuilder = ImmutableList.builder();
    proguardConfigurationBuilder.add(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  <init>();",  // Add <init>() so it does not become a compatibility rule below.
        "  public static void main(java.lang.String[]);",
        "}");
    if (!allowObfuscation) {
      proguardConfigurationBuilder.add("-dontobfuscate");
    }
    List<String> proguardConfig = proguardConfigurationBuilder.build();

    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(mainClass, forNameClass1, forNameClass2)
            .addKeepRules(proguardConfig)
            .setMinApi(parameters)
            .compile()
            .inspector();

    assertTrue(inspector.clazz(mainClass).isPresent());
    forNameClasses.forEach(
        clazz -> {
          ClassSubject subject = inspector.clazz(clazz);
          assertTrue(subject.isPresent());
          assertEquals(allowObfuscation, subject.isRenamed());
        });

    if (isRunProguard()) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      Path proguardMapFile = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(
          ImmutableList.of(mainClass, forNameClass1, forNameClass2)),
          proguardedJar, proguardConfigFile, proguardMapFile);
      CodeInspector proguardedInspector = new CodeInspector(readJar(proguardedJar), proguardMapFile);
      assertEquals(3, proguardedInspector.allClasses().size());
      assertTrue(proguardedInspector.clazz(mainClass).isPresent());
      for (Class<?> clazz : ImmutableList.of(forNameClass1, forNameClass2)) {
        assertTrue(proguardedInspector.clazz(clazz).isPresent());
        assertEquals(allowObfuscation, proguardedInspector.clazz(clazz).isRenamed());
      }
    }
  }

  @Test
  public void classForNameTest() throws Exception {
    testClassForName(true);
    testClassForName(false);
  }

  public void testClassGetMembers(boolean allowObfuscation) throws Exception {
    Class<?> mainClass = TestMainWithGetMembers.class;
    Class<?> withMemberClass = TestClassWithMembers.class;

    ImmutableList.Builder<String> proguardConfigurationBuilder = ImmutableList.builder();
    proguardConfigurationBuilder.add(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  <init>();",  // Add <init>() so it does not become a compatibility rule below.
        "  public static void main(java.lang.String[]);",
        "}");
    if (!allowObfuscation) {
      proguardConfigurationBuilder.add("-dontobfuscate");
    }
    List<String> proguardConfig = proguardConfigurationBuilder.build();

    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(mainClass, withMemberClass)
            .addKeepRules(proguardConfig)
            .setMinApi(parameters)
            .compile()
            .inspector();

    assertTrue(inspector.clazz(mainClass).isPresent());
    ClassSubject classSubject = inspector.clazz(withMemberClass);
    // Due to the direct usage of .class
    assertTrue(classSubject.isPresent());
    assertEquals(allowObfuscation, classSubject.isRenamed());
    FieldSubject foo = classSubject.field("java.lang.String", "foo");
    assertTrue(foo.isPresent());
    assertEquals(allowObfuscation, foo.isRenamed());
    MethodSubject bar =
        classSubject.method("java.lang.String", "bar", ImmutableList.of("java.lang.String"));
    assertTrue(bar.isPresent());
    assertEquals(allowObfuscation, bar.isRenamed());

    if (isRunProguard()) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      Path proguardMapFile = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(
          ImmutableList.of(mainClass, withMemberClass)),
          proguardedJar, proguardConfigFile, proguardMapFile);
      CodeInspector proguardedInspector = new CodeInspector(readJar(proguardedJar), proguardMapFile);
      assertEquals(2, proguardedInspector.allClasses().size());
      assertTrue(proguardedInspector.clazz(mainClass).isPresent());
      classSubject = proguardedInspector.clazz(withMemberClass);
      assertTrue(classSubject.isPresent());
      assertEquals(allowObfuscation, classSubject.isRenamed());
      foo = classSubject.field("java.lang.String", "foo");
      assertTrue(foo.isPresent());
      assertEquals(allowObfuscation, foo.isRenamed());
      bar = classSubject.method("java.lang.String", "bar", ImmutableList.of("java.lang.String"));
      assertTrue(bar.isPresent());
      assertEquals(allowObfuscation, bar.isRenamed());
    }
  }

  @Test
  public void classGetMembersTest() throws Exception {
    testClassGetMembers(true);
    testClassGetMembers(false);
  }

  public void testAtomicFieldUpdaters(boolean allowObfuscation) throws Exception {
    Class<?> mainClass = TestMainWithAtomicFieldUpdater.class;
    Class<?> withVolatileFields = TestClassWithVolatileFields.class;

    ImmutableList.Builder<String> proguardConfigurationBuilder = ImmutableList.builder();
    proguardConfigurationBuilder.add(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  <init>();",  // Add <init>() so it does not become a compatibility rule below.
        "  public static void main(java.lang.String[]);",
        "}");
    if (!allowObfuscation) {
      proguardConfigurationBuilder.add("-dontobfuscate");
    }
    List<String> proguardConfig = proguardConfigurationBuilder.build();

    CodeInspector inspector =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramClasses(mainClass, withVolatileFields)
            .addKeepRules(proguardConfig)
            .setMinApi(parameters)
            .compile()
            .inspector();

    assertTrue(inspector.clazz(mainClass).isPresent());
    ClassSubject classSubject = inspector.clazz(withVolatileFields);
    // Due to the direct usage of .class
    assertTrue(classSubject.isPresent());
    assertEquals(allowObfuscation, classSubject.isRenamed());
    FieldSubject f = classSubject.field("int", "intField");
    assertTrue(f.isPresent());
    assertEquals(allowObfuscation, f.isRenamed());
    f = classSubject.field("long", "longField");
    assertTrue(f.isPresent());
    assertEquals(allowObfuscation, f.isRenamed());
    f = classSubject.field("java.lang.Object", "objField");
    assertTrue(f.isPresent());
    assertEquals(allowObfuscation, f.isRenamed());

    if (isRunProguard()) {
      Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
      Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
      Path proguardMapFile = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
      FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
      ToolHelper.runProguard(jarTestClasses(
          ImmutableList.of(mainClass, withVolatileFields)),
          proguardedJar, proguardConfigFile, proguardMapFile);
      CodeInspector proguardedInspector = new CodeInspector(readJar(proguardedJar), proguardMapFile);
      assertEquals(2, proguardedInspector.allClasses().size());
      assertTrue(proguardedInspector.clazz(mainClass).isPresent());
      classSubject = proguardedInspector.clazz(withVolatileFields);
      assertTrue(classSubject.isPresent());
      assertEquals(allowObfuscation, classSubject.isRenamed());
      f = classSubject.field("int", "intField");
      assertTrue(f.isPresent());
      assertEquals(allowObfuscation, f.isRenamed());
      f = classSubject.field("long", "longField");
      assertTrue(f.isPresent());
      assertEquals(allowObfuscation, f.isRenamed());
      f = classSubject.field("java.lang.Object", "objField");
      assertTrue(f.isPresent());
      assertEquals(allowObfuscation, f.isRenamed());
    }
  }

  @Test
  public void atomicFieldUpdaterTest() throws Exception {
    testAtomicFieldUpdaters(true);
    testAtomicFieldUpdaters(false);
  }

  public void testKeepAttributes(boolean innerClasses, boolean enclosingMethod) throws Exception {
    String keepRules = "";
    if (innerClasses || enclosingMethod) {
      List<String> attributes = new ArrayList<>();
      if (innerClasses) {
        attributes.add(ProguardKeepAttributes.INNER_CLASSES);
      }
      if (enclosingMethod) {
        attributes.add(ProguardKeepAttributes.ENCLOSING_METHOD);
      }
      keepRules = "-keepattributes " + String.join(",", attributes);
    }
    CodeInspector inspector;

    try {
      inspector =
          testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
              .addProgramFiles(
                  ToolHelper.getClassFilesForTestPackage(TestKeepAttributes.class.getPackage()))
              .addKeepRules(
                  "-keep class " + TestKeepAttributes.class.getTypeName() + " {",
                  "  <init>();", // Add <init>() so it does not become a compatibility rule below.
                  "  public static void main(java.lang.String[]);",
                  "}",
                  keepRules)
              .addOptionsModification(options -> options.enableClassInlining = false)
              .enableInliningAnnotations()
              .enableSideEffectAnnotations()
              .setMinApi(parameters)
              .run(parameters.getRuntime(), TestKeepAttributes.class)
              .applyIf(
                  forceProguardCompatibility && (innerClasses || enclosingMethod),
                  result -> result.assertSuccessWithOutput("1"))
              .applyIf(
                  !forceProguardCompatibility || !(innerClasses || enclosingMethod),
                  result -> result.assertSuccessWithOutput("0"))
              .inspector();
    } catch (CompilationFailedException e) {
      assertTrue(!forceProguardCompatibility && (!innerClasses || !enclosingMethod));
      return;
    }

    ClassSubject clazz = inspector.clazz(TestKeepAttributes.class);
    assertThat(clazz, isPresent());
    if (forceProguardCompatibility && (innerClasses || enclosingMethod)) {
      assertFalse(clazz.getDexProgramClass().getInnerClasses().isEmpty());
    } else {
      assertTrue(clazz.getDexProgramClass().getInnerClasses().isEmpty());
    }
  }

  @Test
  public void keepAttributesTest() throws Exception {
    testKeepAttributes(false, false);
    testKeepAttributes(true, false);
    testKeepAttributes(false, true);
    testKeepAttributes(true, true);
  }

  private void runKeepDefaultMethodsTest(
      List<String> additionalKeepRules,
      Consumer<CodeInspector> inspection,
      Consumer<GraphInspector> compatInspection)
      throws Exception {
    if (parameters.isDexRuntime()) {
      assert parameters.getApiLevel().getLevel() >= AndroidApiLevel.O.getLevel();
    }

    Class<?> mainClass = TestClass.class;
    R8TestCompileResult compileResult =
        testForR8Compat(parameters.getBackend(), forceProguardCompatibility)
            .addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()))
            .addKeepRules(
                "-keep class " + mainClass.getCanonicalName() + "{",
                "  public <init>();",
                "  public static void main(java.lang.String[]);",
                "}",
                "-dontobfuscate")
            .addKeepRules(additionalKeepRules)
            .enableGraphInspector()
            .setMinApi(parameters)
            .addOptionsModification(
                options -> {
                  options.enableClassInlining = false;

                  // Prevent InterfaceWithDefaultMethods from being merged into
                  // ClassImplementingInterface.
                  options.getVerticalClassMergerOptions().disable();
                })
            .compile();
    inspection.accept(compileResult.inspector());
    compatInspection.accept(compileResult.graphInspector());
  }

  private void noCompatibilityRules(GraphInspector inspector) {
    inspector.assertNoPureCompatibilityEdges();
  }

  private void defaultMethodKept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method("int", "method", ImmutableList.of());
    assertTrue(method.isPresent());
    assertFalse(method.isAbstract());
  }

  private void defaultMethodCompatibilityRules(GraphInspector inspector) {
    // The enqueuer does not add an edge for the referenced => kept edges so we cant check compat.
  }

  private void defaultMethod2Kept(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(InterfaceWithDefaultMethods.class);
    assertTrue(clazz.isPresent());
    MethodSubject method =
        clazz.method("void", "method2", ImmutableList.of("java.lang.String", "int"));
    assertTrue(method.isPresent());
    assertFalse(method.isAbstract());
  }

  private void defaultMethod2CompatibilityRules(GraphInspector inspector) {
    // The enqueuer does not add an edge for the referenced => kept edges so we cant check compat.
  }

  @Test
  public void keepDefaultMethodsTest() throws Exception {
    assumeTrue(forceProguardCompatibility);
    runKeepDefaultMethodsTest(ImmutableList.of(
        "-keep interface " + InterfaceWithDefaultMethods.class.getCanonicalName() + "{",
        "  public int method();",
        "}"
    ), this::defaultMethodKept, this::noCompatibilityRules);
    runKeepDefaultMethodsTest(ImmutableList.of(
        "-keep class " + ClassImplementingInterface.class.getCanonicalName() + "{",
        "  <methods>;",
        "}",
        "-keep class " + TestClass.class.getCanonicalName() + "{",
        "  public void useInterfaceMethod();",
        "}"
    ), this::defaultMethodKept, this::defaultMethodCompatibilityRules);
    runKeepDefaultMethodsTest(ImmutableList.of(
        "-keep class " + ClassImplementingInterface.class.getCanonicalName() + "{",
        "  <methods>;",
        "}",
        "-keep class " + TestClass.class.getCanonicalName() + "{",
        "  public void useInterfaceMethod2();",
        "}"
    ), this::defaultMethod2Kept, this::defaultMethod2CompatibilityRules);
  }
}
