// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.AbstractSequentialList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredGenericSignatureTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getJdk8Jdk11(),
        SPECIFICATIONS_WITH_CF2CF);
  }

  public DesugaredGenericSignatureTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testGenericSignature() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAllClassesRuleWithAllowObfuscation()
        .addKeepAttributes(
            ProguardKeepAttributes.SIGNATURE,
            ProguardKeepAttributes.INNER_CLASSES,
            ProguardKeepAttributes.ENCLOSING_METHOD)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::checkRewrittenSignature)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(expected(parameters, compilationSpecification.isCfToCf()));
  }

  private void checkRewrittenSignature(CodeInspector inspector) {
    if (!libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters)) {
      return;
    }
    ClassSubject javaTimeBox = inspector.clazz(JavaTimeDateBox.class);
    assertThat(javaTimeBox, isPresent());
    ClassSubject box = inspector.clazz(Box.class);
    assertThat(box, isPresent());
    String finalBoxDescriptor = box.getFinalDescriptor();
    assertEquals(
        "Ljava/lang/Object;"
            + finalBoxDescriptor.substring(0, finalBoxDescriptor.length() - 1)
            + "<Lj$/time/LocalDate;>;",
        javaTimeBox.getFinalSignatureAttribute());
  }

  public interface Box<T> {

    T addOne(T t);
  }

  public static class JavaTimeDateBox implements Box<java.time.LocalDate> {

    @Override
    @NeverInline
    public LocalDate addOne(LocalDate localDate) {
      return localDate.plusDays(1);
    }
  }

  private static String expected(
      TestParameters parameters, boolean genericSignaturesOnEmulatedInterfaces) {
    final String EXPECTED = StringUtils.lines("Box", "1970", "1", "2");
    final String STRING_KEY_HASH_MAP_EXPECTED =
        StringUtils.lines(
            "StringKeyHashMap", "1", "j$.util.Map<java.lang.String, T>", "2", "true", "true");
    final String SAME_KEY_AND_VALUE_TYPE_HASH_MAP_EXPECTED =
        StringUtils.lines(
            "SameKeyAndValueTypeHashMap", "1", "j$.util.Map<T, T>", "2", "true", "true");
    final String TRANSFORMING_SEQUENTIAL_LIST_EXPECTED =
        StringUtils.lines("TransformingSequentialList", "2", "j$.util.List<T>");

    final String EXPECTED_WITH_EMULATED_INTERFACE =
        STRING_KEY_HASH_MAP_EXPECTED
            + SAME_KEY_AND_VALUE_TYPE_HASH_MAP_EXPECTED
            + TRANSFORMING_SEQUENTIAL_LIST_EXPECTED;
    final String EXPECTED_WITHOUT_EMULATED_INTERFACE_ART_BEFORE_O =
        StringUtils.lines(
            "StringKeyHashMap",
            "1",
            "interface j$.util.Map",
            "SameKeyAndValueTypeHashMap",
            "1",
            "interface j$.util.Map",
            "TransformingSequentialList",
            "2",
            "interface j$.util.List");
    final String EXPECTED_WITHOUT_EMULATED_INTERFACE_JVM_AND_ART_FROM_O =
        StringUtils.lines(
            "StringKeyHashMap",
            "0",
            "SameKeyAndValueTypeHashMap",
            "0",
            "TransformingSequentialList",
            "1");

    return EXPECTED
        + (genericSignaturesOnEmulatedInterfaces
                && !parameters
                    .getApiLevel()
                    .isGreaterThanOrEqualTo(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())
            ? EXPECTED_WITH_EMULATED_INTERFACE
            : (parameters.isDexRuntime()
                    && (parameters
                            .getRuntime()
                            .asDex()
                            .getMinApiLevel()
                            .isLessThan(AndroidApiLevel.N)
                        || parameters
                            .getApiLevel()
                            .isLessThan(TestBase.apiLevelWithDefaultInterfaceMethodsSupport())))
                ? EXPECTED_WITHOUT_EMULATED_INTERFACE_ART_BEFORE_O
                : EXPECTED_WITHOUT_EMULATED_INTERFACE_JVM_AND_ART_FROM_O);
  }

  public static class Main {

    public static Box<java.time.LocalDate> bar() {
      return new JavaTimeDateBox();
    }

    public static void main(String[] args) {
      testBox();
      testEmulatedInterfaceGenericSignatureStringKeyHashMap();
      testEmulatedInterfaceGenericSignatureSameKeyAndValueTypeHashMap();
      testEmulatedInterfaceGenericSignatureTransformingSequentialList();
    }

    public static void testBox() {
      System.out.println("Box");
      LocalDate localDate = bar().addOne(LocalDate.of(1970, 1, 1));
      System.out.println(localDate.getYear());
      System.out.println(localDate.getMonthValue());
      System.out.println(localDate.getDayOfMonth());
    }

    public static void testEmulatedInterfaceGenericSignatureStringKeyHashMap() {
      System.out.println("StringKeyHashMap");
      Class<?> clazz = StringKeyHashMap.class;
      System.out.println(clazz.getGenericInterfaces().length);
      if (clazz.getGenericInterfaces().length == 0) {
        return;
      }
      Type genericInterface = clazz.getGenericInterfaces()[0];
      System.out.println(genericInterface);
      if (genericInterface instanceof ParameterizedType) {
        // The j$.util.Map emulated interface has the generic type arguments <String, T>.
        Type[] actualTypeArguments =
            ((ParameterizedType) genericInterface).getActualTypeArguments();
        System.out.println(actualTypeArguments.length);
        System.out.println(actualTypeArguments[0].equals(String.class));
        System.out.println(actualTypeArguments[1].equals(clazz.getTypeParameters()[0]));
      }
    }

    public static void testEmulatedInterfaceGenericSignatureSameKeyAndValueTypeHashMap() {
      System.out.println("SameKeyAndValueTypeHashMap");
      Class<?> clazz = SameKeyAndValueTypeHashMap.class;
      System.out.println(clazz.getGenericInterfaces().length);
      if (clazz.getGenericInterfaces().length == 0) {
        return;
      }
      Type genericInterface = clazz.getGenericInterfaces()[0];
      System.out.println(genericInterface);
      if (genericInterface instanceof ParameterizedType) {
        // The j$.util.Map emulated interface has the generic type arguments <T, T>.
        Type[] actualTypeArguments =
            ((ParameterizedType) genericInterface).getActualTypeArguments();
        System.out.println(actualTypeArguments.length);
        System.out.println(actualTypeArguments[0].equals(clazz.getTypeParameters()[0]));
        System.out.println(actualTypeArguments[1].equals(clazz.getTypeParameters()[0]));
      }
    }

    public static void testEmulatedInterfaceGenericSignatureTransformingSequentialList() {
      System.out.println("TransformingSequentialList");
      Class<?> clazz = TransformingSequentialList.class;
      System.out.println(clazz.getGenericInterfaces().length);
      if (clazz.getGenericInterfaces().length == 1) {
        return;
      }
      // j$.util.List emulated interface has the generic type argument <T>.
      System.out.println(clazz.getGenericInterfaces()[1]);
    }
  }

  // LinkedHashMap implements Map.
  abstract static class StringKeyHashMap<T> extends LinkedHashMap<String, T> {

    // Need at least one overridden default method for emulated dispatch.
    @Override
    public T getOrDefault(Object key, T defaultValue) {
      return super.getOrDefault(key, defaultValue);
    }
  }

  // LinkedHashMap implements Map.
  abstract static class SameKeyAndValueTypeHashMap<T> extends LinkedHashMap<T, T> {

    // Need at least one overridden default method for emulated dispatch.
    @Override
    public T getOrDefault(Object key, T defaultValue) {
      return super.getOrDefault(key, defaultValue);
    }
  }

  // AbstractSequentialList implements List further up the hierarchy.
  abstract static class TransformingSequentialList<F, T> extends AbstractSequentialList<T>
      implements Serializable {

    // Need at least one overridden default method for emulated dispatch.
    @Override
    public void replaceAll(UnaryOperator<T> operator) {}
  }
}
