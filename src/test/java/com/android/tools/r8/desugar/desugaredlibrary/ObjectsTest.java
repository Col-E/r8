// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class ObjectsTest extends DesugaredLibraryTestBase implements Opcodes {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "1",
          "false",
          "false",
          Objects.toString(Objects.hash(1, 2)),
          "4",
          "NPE",
          "Was null",
          "Supplier said was null",
          "5",
          "6",
          "true",
          "false",
          "1",
          "2",
          "3",
          "4");

  private final TestParameters parameters;
  private final boolean libraryDesugarJavaUtilObjects;
  private final boolean shrinkDesugaredLibrary = false;
  private final Path androidJar;

  @Parameters(name = "{0}, libraryDesugarJavaUtilObjects: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public ObjectsTest(TestParameters parameters, boolean libraryDesugarJavaUtilObjects) {
    this.parameters = parameters;
    this.libraryDesugarJavaUtilObjects = libraryDesugarJavaUtilObjects;
    // Using desugared library require a compile SDK of 26 or higher.
    this.androidJar =
        ToolHelper.getAndroidJar(Ordered.max(parameters.getApiLevel(), AndroidApiLevel.O));
  }

  LegacyDesugaredLibrarySpecification desugaredLibrarySpecification(
      InternalOptions options, boolean libraryCompilation, TestParameters parameters) {
    return new LegacyDesugaredLibrarySpecificationParser(
            options.dexItemFactory(),
            options.reporter,
            libraryCompilation,
            parameters.getApiLevel().getLevel())
        .parse(
            StringResource.fromFile(
                libraryDesugarJavaUtilObjects
                    ? ToolHelper.getDesugarLibJsonForTestingAlternative3()
                    : ToolHelper.getDesugarLibJsonForTesting()));
  }

  private void configurationForProgramCompilation(InternalOptions options) {
    setDesugaredLibrarySpecificationForTesting(
        options, desugaredLibrarySpecification(options, false, parameters));
  }

  private void configurationForLibraryCompilation(InternalOptions options) {
    setDesugaredLibrarySpecificationForTesting(
        options, desugaredLibrarySpecification(options, true, parameters));
  }

  private Matcher<MethodSubject> invokesObjectsCompare(String holder) {
    return invokesMethod(
        "int",
        holder,
        "compare",
        ImmutableList.of("java.lang.Object", "java.lang.Object", "java.util.Comparator"));
  }

  private Matcher<MethodSubject> invokesObjectsEquals(String holder) {
    return invokesMethod(
        "boolean", holder, "equals", ImmutableList.of("java.lang.Object", "java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesObjectsDeepEquals(String holder) {
    return invokesMethod(
        "boolean", holder, "deepEquals", ImmutableList.of("java.lang.Object", "java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesObjectsHash(String holder) {
    return invokesMethod("int", holder, "hash", ImmutableList.of("java.lang.Object[]"));
  }

  private Matcher<MethodSubject> invokesObjectsHashCode(String holder) {
    return invokesMethod("int", holder, "hashCode", ImmutableList.of("java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesClassGetClass() {
    return invokesMethod("java.lang.Class", "java.lang.Object", "getClass", ImmutableList.of());
  }

  private Matcher<MethodSubject> invokesObjectsRequireNonNull(String holder) {
    return invokesMethod(
        "java.lang.Object", holder, "requireNonNull", ImmutableList.of("java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesObjectsRequireNonNullWithMessage(String holder) {
    return invokesMethod(
        "java.lang.Object",
        holder,
        "requireNonNull",
        ImmutableList.of("java.lang.Object", "java.lang.String"));
  }

  private Matcher<MethodSubject> invokesObjectsRequireNonNullWithSupplier(
      String holder, String Supplier) {
    return invokesMethod(
        "java.lang.Object",
        holder,
        "requireNonNull",
        ImmutableList.of("java.lang.Object", Supplier));
  }

  private Matcher<MethodSubject> invokesObjectsToString(String holder) {
    return invokesMethod(
        "java.lang.String", holder, "toString", ImmutableList.of("java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesObjectsToStringWithNullDefault(String holder) {
    return invokesMethod(
        "java.lang.String",
        holder,
        "toString",
        ImmutableList.of("java.lang.Object", "java.lang.String"));
  }

  private Matcher<MethodSubject> invokesObjectsIsNull(String holder) {
    return invokesMethod("boolean", holder, "isNull", ImmutableList.of("java.lang.Object"));
  }

  private Matcher<MethodSubject> invokesObjectsNonNull(String holder) {
    return invokesMethod("boolean", holder, "nonNull", ImmutableList.of("java.lang.Object"));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertThat(testClass, isPresent());

    // Objects.equals as added in Android K, so when backporting, this is only backported below K.
    // However, for library desugaring, the desugaring of Objects.equals happens all the way up to
    // Android M, as that is grouped with other methods like Objects.requireNonNull which was
    // added in Android N.
    boolean invokeJavaUtilObjects =
        !libraryDesugarJavaUtilObjects
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K)
            || (libraryDesugarJavaUtilObjects
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N));
    boolean invokeJDollarUtilObjects =
        libraryDesugarJavaUtilObjects && parameters.getApiLevel().isLessThan(AndroidApiLevel.N);
    boolean invokeJavaUtilObjectsWithSupplier =
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
    boolean invokeJDollarUtilObjectsWithSupplier =
        libraryDesugarJavaUtilObjects && parameters.getApiLevel().isLessThan(AndroidApiLevel.N);

    assertThat(
        testClass.uniqueMethodWithName("objectsCompare"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsCompare("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsCompare"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsCompare("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsDeepEquals"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsDeepEquals("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsDeepEquals"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsDeepEquals("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsEquals"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsEquals("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsEquals"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsEquals("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsHash"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsHash("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsHash"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsHash("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsHashCode"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsHashCode("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsHashCode"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsHashCode("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsRequireNonNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNull"),
        onlyIf(parameters.getApiLevel().isLessThan(AndroidApiLevel.K), invokesClassGetClass()));

    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNullWithMessage"),
        onlyIf(
            invokeJavaUtilObjects, invokesObjectsRequireNonNullWithMessage("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNullWithMessage"),
        onlyIf(
            invokeJDollarUtilObjects, invokesObjectsRequireNonNullWithMessage("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNullWithSupplier"),
        onlyIf(
            invokeJavaUtilObjectsWithSupplier,
            invokesObjectsRequireNonNullWithSupplier(
                "java.util.Objects", "java.util.function.Supplier")));
    assertThat(
        testClass.uniqueMethodWithName("objectsRequireNonNullWithSupplier"),
        onlyIf(
            invokeJDollarUtilObjectsWithSupplier,
            invokesObjectsRequireNonNullWithSupplier(
                "j$.util.Objects", "j$.util.function.Supplier")));

    assertThat(
        testClass.uniqueMethodWithName("objectsToString"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsToString("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsToString"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsToString("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsToStringWithNullDefault"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsToStringWithNullDefault("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsToStringWithNullDefault"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsToStringWithNullDefault("j$.util.Objects")));

    invokeJavaUtilObjects = parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);

    assertThat(
        testClass.uniqueMethodWithName("objectsIsNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsIsNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsIsNull"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsIsNull("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithName("objectsNonNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsNonNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithName("objectsNonNull"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsNonNull("j$.util.Objects")));
  }

  @Test
  public void testD8Cf() throws Exception {
    // Adjust API level if running on JDK 8. The java.util.Objects methods added in
    // Android R where added in JDK 9, so setting the the API level to Android P will backport
    // these methods for JDK 8.
    AndroidApiLevel apiLevel = parameters.getApiLevel();
    if (parameters.getRuntime().isCf()
        && parameters.getRuntime().asCf().getVm() == CfVm.JDK8
        && apiLevel.isGreaterThanOrEqualTo(AndroidApiLevel.R)) {
      apiLevel = AndroidApiLevel.P;
    }

    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    // Use D8 to desugar with Java classfile output.
    Path jar =
        testForD8(Backend.CF)
            .addLibraryFiles(androidJar)
            .addOptionsModification(this::configurationForProgramCompilation)
            .addInnerClasses(ObjectsTest.class)
            .addProgramClassFileData(dumpAndroidRUtilsObjectsMethods())
            .setMinApi(apiLevel)
            .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    if (parameters.getRuntime().isDex()) {
      // Collection keep rules is only implemented in the DEX writer.
      String desugaredLibraryKeepRules = keepRuleConsumer.get();
      if (desugaredLibraryKeepRules != null) {
        assertEquals(0, desugaredLibraryKeepRules.length());
        desugaredLibraryKeepRules = "-keep class * { *; }";
      }

      // Convert to DEX without desugaring and run.
      testForD8()
          .addLibraryFiles(androidJar)
          .addProgramFiles(jar)
          .setMinApi(apiLevel)
          .disableDesugaring()
          .compile()
          .addDesugaredCoreLibraryRunClassPath(
              (apiLevel_, keepRules, shrink) ->
                  buildDesugaredLibrary(
                      apiLevel_,
                      keepRules,
                      shrink,
                      ImmutableList.of(),
                      this::configurationForLibraryCompilation),
              parameters.getApiLevel(),
              desugaredLibraryKeepRules,
              shrinkDesugaredLibrary)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    } else {
      // Build the desugared library in class file format.
      Path desugaredLib =
          getDesugaredLibraryInCF(parameters, this::configurationForLibraryCompilation);

      // Run on the JVM with desugared library on classpath.
      testForJvm()
          .addProgramFiles(jar)
          .addRunClasspathFiles(desugaredLib)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(EXPECTED_OUTPUT);
    }
  }

  @Test
  public void testD8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .addLibraryFiles(androidJar)
        .addOptionsModification(this::configurationForProgramCompilation)
        .addInnerClasses(ObjectsTest.class)
        .addProgramClassFileData(dumpAndroidRUtilsObjectsMethods())
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    this::configurationForLibraryCompilation),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(parameters.getRuntime().isDex());
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .addLibraryFiles(androidJar)
        .addOptionsModification(this::configurationForProgramCompilation)
        .addInnerClasses(ObjectsTest.class)
        .addKeepMainRule(TestClass.class)
        .addProgramClassFileData(dumpAndroidRUtilsObjectsMethods())
        .enableInliningAnnotations()
        .noMinification()
        .addKeepRules("-keep class AndroidRUtilsObjectsMethods { *; }")
        .addKeepRules("-neverinline class AndroidRUtilsObjectsMethods { *; }")
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            (apiLevel, keepRules, shrink) ->
                buildDesugaredLibrary(
                    apiLevel,
                    keepRules,
                    shrink,
                    ImmutableList.of(),
                    this::configurationForLibraryCompilation),
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {
    @NeverInline
    private static void objectsCompare(String s1, String s2) {
      Comparator<String> stringsNullLast =
          (o1, o2) -> {
            if (o1 == null) {
              return o2 == null ? 0 : 1;
            }
            return o2 == null ? -1 : o1.compareTo(o2);
          };
      System.out.println(Objects.compare(s1, s2, stringsNullLast));
    }

    @NeverInline
    private static void objectsDeepEquals(Object o1, Object o2) {
      System.out.println(Objects.deepEquals(o1, o2));
    }

    @NeverInline
    private static void objectsEquals(Object o1, Object o2) {
      System.out.println(Objects.equals(o1, o2));
    }

    @NeverInline
    private static void objectsHash(Object o1, Object o2) {
      System.out.println(Objects.hash(o1, o2));
    }

    @NeverInline
    private static void objectsHashCode(Object o) {
      System.out.println(Objects.hashCode(o));
    }

    @NeverInline
    private static void objectsRequireNonNull(Object o) {
      try {
        System.out.println(Objects.requireNonNull(o));
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
    }

    @NeverInline
    private static void objectsRequireNonNullWithMessage(Object o, String message) {
      try {
        System.out.println(Objects.requireNonNull(o, message));
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }
    }

    @NeverInline
    private static void objectsRequireNonNullWithSupplier(
        Object o, Supplier<String> messageSupplier) {
      try {
        System.out.println(Objects.requireNonNull(o, messageSupplier));
      } catch (NullPointerException e) {
        System.out.println(e.getMessage());
      }
    }

    @NeverInline
    private static void objectsToString(Object o) {
      System.out.println(Objects.toString(o));
    }

    @NeverInline
    private static void objectsToStringWithNullDefault(Object o, String nullDefault) {
      System.out.println(Objects.toString(o, nullDefault));
    }

    @NeverInline
    private static void objectsIsNull(Object o) {
      System.out.println(Objects.isNull(o));
    }

    @NeverInline
    private static void objectsNonNull(Object o) {
      System.out.println(Objects.nonNull(o));
    }

    public static void main(String[] args) throws Exception {
      // Android K methods.
      objectsCompare("b", "a");
      objectsDeepEquals(args, new Object());
      objectsEquals(makeNullable(args), new Object());
      objectsHash(1, 2);
      objectsHashCode(4);
      objectsRequireNonNull(getNonNullableNull());
      objectsRequireNonNullWithMessage(null, "Was null");
      objectsRequireNonNullWithSupplier(null, () -> "Supplier said was null");
      objectsToString(makeNullable("5"));
      objectsToStringWithNullDefault(getNonNullableNull(), "6");

      // Android N methods.
      objectsIsNull(getNonNullableNull());
      objectsNonNull(getNonNullableNull());

      // Android R methods.
      Class<?> c = Class.forName("AndroidRUtilsObjectsMethods");
      c.getDeclaredMethod("checkFromIndexSize", int.class, int.class, int.class)
          .invoke(null, 1, 2, 10);
      c.getDeclaredMethod("checkFromToIndex", int.class, int.class, int.class)
          .invoke(null, 2, 4, 10);
      c.getDeclaredMethod("checkIndex", int.class, int.class).invoke(null, 3, 10);
      c.getDeclaredMethod("requireNonNullElse", Object.class, Object.class).invoke(null, null, 4);
      // TODO(b/174840626) Also support requireNonNullElseGet.
    }

    private static Object makeNullable(Object obj) {
      return System.currentTimeMillis() > 0 ? obj : null;
    }

    private static Object getNonNullableNull() {
      return System.currentTimeMillis() > 0 ? null : new Object();
    }
  }

  /*
    Dump below is from this source:

    import java.util.function.Supplier;
    import java.util.Objects;

    public class AndroidRUtilsObjectsMethods {
      public static void checkFromIndexSize(int fromIndex, int size, int length) {
        System.out.println(Objects.checkFromIndexSize(fromIndex, size, length));
      }
      public static void checkFromToIndex(int fromIndex, int toIndex, int length) {
        System.out.println(Objects.checkFromToIndex(fromIndex, toIndex, length));
      }
      public static void checkIndex(int index, int length) {
        System.out.println(Objects.checkIndex(index, length));
      }
      public static <T> void requireNonNullElse(T obj, T defaultObj) {
        System.out.println(Objects.requireNonNullElse(obj, defaultObj));
      }
      public static <T> void requireNonNullElseGet(T obj, Supplier<? extends T> supplier) {
        System.out.println(Objects.requireNonNullElse(obj, supplier));
      }
    }

    This is added as a dump as it use APIs which are only abailable from JDK 9.
  */
  public static byte[] dumpAndroidRUtilsObjectsMethods() throws Exception {

    ClassWriter classWriter = new ClassWriter(0);
    MethodVisitor methodVisitor;

    classWriter.visit(
        V9, ACC_PUBLIC | ACC_SUPER, "AndroidRUtilsObjectsMethods", null, "java/lang/Object", null);

    classWriter.visitSource("AndroidRUtilsObjectsMethods.java", null);

    {
      methodVisitor = classWriter.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(3, label0);
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(1, 1);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "checkFromIndexSize", "(III)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(5, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/util/Objects", "checkFromIndexSize", "(III)I", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(6, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC, "checkFromToIndex", "(III)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(8, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitVarInsn(ILOAD, 2);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/util/Objects", "checkFromToIndex", "(III)I", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(9, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(4, 3);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(ACC_PUBLIC | ACC_STATIC, "checkIndex", "(II)V", null, null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(11, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ILOAD, 0);
      methodVisitor.visitVarInsn(ILOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC, "java/util/Objects", "checkIndex", "(II)I", false);
      methodVisitor.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(I)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(12, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "requireNonNullElse",
              "(Ljava/lang/Object;Ljava/lang/Object;)V",
              "<T:Ljava/lang/Object;>(TT;TT;)V",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(14, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/util/Objects",
          "requireNonNullElse",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(15, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 2);
      methodVisitor.visitEnd();
    }
    {
      methodVisitor =
          classWriter.visitMethod(
              ACC_PUBLIC | ACC_STATIC,
              "requireNonNullElseGet",
              "(Ljava/lang/Object;Ljava/util/function/Supplier;)V",
              "<T:Ljava/lang/Object;>(TT;Ljava/util/function/Supplier<+TT;>;)V",
              null);
      methodVisitor.visitCode();
      Label label0 = new Label();
      methodVisitor.visitLabel(label0);
      methodVisitor.visitLineNumber(18, label0);
      methodVisitor.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
      methodVisitor.visitVarInsn(ALOAD, 0);
      methodVisitor.visitVarInsn(ALOAD, 1);
      methodVisitor.visitMethodInsn(
          INVOKESTATIC,
          "java/util/Objects",
          "requireNonNullElse",
          "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;",
          false);
      methodVisitor.visitMethodInsn(
          INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/Object;)V", false);
      Label label1 = new Label();
      methodVisitor.visitLabel(label1);
      methodVisitor.visitLineNumber(19, label1);
      methodVisitor.visitInsn(RETURN);
      methodVisitor.visitMaxs(3, 2);
      methodVisitor.visitEnd();
    }
    classWriter.visitEnd();

    return classWriter.toByteArray();
  }
}
