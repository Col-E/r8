// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LATEST;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion.LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.onlyIf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
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

  private final boolean libraryDesugarJavaUtilObjects;

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    LibraryDesugaringSpecification jdk8MaxCompileSdk =
        new LibraryDesugaringSpecification(
            "JDK8_MAX",
            DESUGARED_JDK_8_LIB_JAR,
            "desugar_jdk_libs.json",
            AndroidApiLevel.LATEST,
            LibraryDesugaringSpecification.JDK8_DESCRIPTOR,
            LEGACY);
    LibraryDesugaringSpecification jdk11MaxCompileSdk =
        new LibraryDesugaringSpecification(
            "JDK11_MAX",
            LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar(),
            "jdk11/desugar_jdk_libs.json",
            AndroidApiLevel.LATEST,
            LibraryDesugaringSpecification.JDK11_DESCRIPTOR,
            LATEST);
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        ImmutableList.of(JDK8, JDK11, jdk8MaxCompileSdk, jdk11MaxCompileSdk),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public ObjectsTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugarJavaUtilObjects =
        !libraryDesugaringSpecification.toString().contains("JDK8");
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
    String supplier =
        libraryDesugaringSpecification.hasJDollarFunction(parameters)
            ? "j$.util.function.Supplier"
            : "java.util.function.Supplier";

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsCompare"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsCompare("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsCompare"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsCompare("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsDeepEquals"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsDeepEquals("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsDeepEquals"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsDeepEquals("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsEquals"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsEquals("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsEquals"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsEquals("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsHash"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsHash("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsHash"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsHash("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsHashCode"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsHashCode("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsHashCode"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsHashCode("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsRequireNonNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNull"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsRequireNonNull("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNullWithMessage"),
        onlyIf(
            invokeJavaUtilObjects, invokesObjectsRequireNonNullWithMessage("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNullWithMessage"),
        onlyIf(
            invokeJDollarUtilObjects, invokesObjectsRequireNonNullWithMessage("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNullWithSupplier"),
        onlyIf(
            invokeJavaUtilObjectsWithSupplier,
            invokesObjectsRequireNonNullWithSupplier("java.util.Objects", supplier)));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsRequireNonNullWithSupplier"),
        onlyIf(
            invokeJDollarUtilObjectsWithSupplier,
            invokesObjectsRequireNonNullWithSupplier("j$.util.Objects", supplier)));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsToString"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsToString("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsToString"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsToString("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsToStringWithNullDefault"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsToStringWithNullDefault("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsToStringWithNullDefault"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsToStringWithNullDefault("j$.util.Objects")));

    invokeJavaUtilObjects = parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsIsNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsIsNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsIsNull"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsIsNull("j$.util.Objects")));

    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsNonNull"),
        onlyIf(invokeJavaUtilObjects, invokesObjectsNonNull("java.util.Objects")));
    assertThat(
        testClass.uniqueMethodWithOriginalName("objectsNonNull"),
        onlyIf(invokeJDollarUtilObjects, invokesObjectsNonNull("j$.util.Objects")));
  }

  @Test
  public void testObjects() throws Throwable {
    Assume.assumeFalse(
        "Method is absent on JDK8",
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.R)
            && parameters.isCfRuntime(CfVm.JDK8));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addProgramClassFileData(ImmutableList.of(dumpAndroidRUtilsObjectsMethods()))
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableConstantArgumentAnnotations()
        .noMinification()
        .addKeepRules("-keep class AndroidRUtilsObjectsMethods { *; }")
        .addKeepRules("-neverinline class AndroidRUtilsObjectsMethods { *; }")
        .compile()
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

    // We keep constant arguments to avoid the argument to be proven non-null leading to
    // Objects#hashCode(Object) being rewritten to Object#hashCode().
    @KeepConstantArguments
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
  public static byte[] dumpAndroidRUtilsObjectsMethods() {

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
