// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredLibraryNavTypeTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT_FORMAT =
      StringUtils.lines(
          "java.lang.IllegalArgumentException",
          "java.lang.RuntimeException java.lang.ClassNotFoundException java.time.XYZ",
          "Nav class %s.time.LocalDate",
          "Nav class %s.time.LocalDate[]",
          "Nav class java.lang.Object",
          "java.lang.IllegalArgumentException",
          "java.lang.RuntimeException java.lang.ClassNotFoundException java.time.XYZ",
          "Nav class %s.time.LocalDate",
          "Nav class %s.time.LocalDate[]",
          "Nav class java.lang.Object");
  private static final String NAV_TYPE = "Landroidx/navigation/NavType;";
  private static final String NAV_TYPE_COMPANION = "Landroidx/navigation/NavType$Companion;";

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        ImmutableList.of(JDK11, JDK11_PATH),
        ImmutableList.of(D8_L8DEBUG));
  }

  public DesugaredLibraryNavTypeTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testNavType() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addProgramClassFileData(getMain(), getNavType(), getNavTypeCompanion())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class"
                + " com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryNavTypeTest.Main"
                + " { public static LocalDate MIN; }")
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(
            String.format(
                EXPECTED_RESULT_FORMAT, getPrefix(), getPrefix(), getPrefix(), getPrefix()));
  }

  private String getPrefix() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O) ? "java" : "j$";
  }

  public static byte[] getNavTypeCompanion() throws Exception {
    return transformer(NavType.Companion.class)
        .setClassDescriptor(NAV_TYPE_COMPANION)
        .replaceClassDescriptorInMembers(descriptor(NavType.class), NAV_TYPE)
        .replaceClassDescriptorInMethodInstructions(descriptor(NavType.class), NAV_TYPE)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(NavType.Companion.class), NAV_TYPE_COMPANION)
        .transform();
  }

  public static byte[] getNavType() throws Exception {
    return transformer(NavType.class)
        .setClassDescriptor(NAV_TYPE)
        .replaceClassDescriptorInMembers(descriptor(NavType.Companion.class), NAV_TYPE_COMPANION)
        .replaceClassDescriptorInMethodInstructions(descriptor(NavType.class), NAV_TYPE)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(NavType.Companion.class), NAV_TYPE_COMPANION)
        .transform();
  }

  public static byte[] getMain() throws Exception {
    return transformer(Main.class)
        .replaceClassDescriptorInMethodInstructions(descriptor(NavType.class), NAV_TYPE)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(NavType.Companion.class), NAV_TYPE_COMPANION)
        .transform();
  }

  public static class Main {

    // The field should be kept to force keep it on desugared library.
    public static LocalDate MIN = LocalDate.MIN;

    public static void main(String[] args) {
      testCallCompanion();
      testCallStatic();
    }

    private static void testCallStatic() {
      // Test IAE are correctly rethrown.
      try {
        System.out.println(NavType.fromArgType("IAETest", null));
      } catch (Throwable t) {
        System.out.println(t.getClass().getName());
      }
      // Test missing class is still missing with the correct error.
      try {
        System.out.println(
            NavType.fromArgType("java.time.XYZ", "com.android.tools.r8.desugar.desugaredlibrary"));
      } catch (Throwable t) {
        System.out.println(
            t.getClass().getName()
                + " "
                + t.getCause().getClass().getName()
                + " "
                + t.getCause().getMessage());
      }
      // Test class is present with the retargeting and desugared library.
      System.out.println(
          NavType.fromArgType(
              "java.time.LocalDate", "com.android.tools.r8.desugar.desugaredlibrary"));
      // Test array class is present with the retargeting and desugared library.
      System.out.println(
          NavType.fromArgType(
              "java.time.LocalDate[]", "com.android.tools.r8.desugar.desugaredlibrary"));
      // Test always present class.
      System.out.println(
          NavType.fromArgType("java.lang.Object", "com.android.tools.r8.desugar.desugaredlibrary"));
    }

    private static void testCallCompanion() {
      // Test IAE are correctly rethrown.
      try {
        System.out.println(NavType.Companion.fromArgType("IAETest", null));
      } catch (Throwable t) {
        System.out.println(t.getClass().getName());
      }
      // Test missing class is still missing with the correct error.
      try {
        System.out.println(
            NavType.Companion.fromArgType(
                "java.time.XYZ", "com.android.tools.r8.desugar.desugaredlibrary"));
      } catch (Throwable t) {
        System.out.println(
            t.getClass().getName()
                + " "
                + t.getCause().getClass().getName()
                + " "
                + t.getCause().getMessage());
      }
      // Test class is present with the retargeting and desugared library.
      System.out.println(
          NavType.Companion.fromArgType(
              "java.time.LocalDate", "com.android.tools.r8.desugar.desugaredlibrary"));
      // Test array class is present with the retargeting and desugared library.
      System.out.println(
          NavType.Companion.fromArgType(
              "java.time.LocalDate[]", "com.android.tools.r8.desugar.desugaredlibrary"));
      // Test always present class.
      System.out.println(
          NavType.Companion.fromArgType(
              "java.lang.Object", "com.android.tools.r8.desugar.desugaredlibrary"));
    }

    public String run(Supplier<String> supplier) {
      try {
        return supplier.get();
      } catch (Throwable t) {
        return t.getClass().toString();
      }
    }
  }

  // Will be rewritten to androidx/navigation/NavType.
  public static class NavType {

    public static final Companion Companion = new Companion();

    private final Class<?> clazz;
    private final boolean isArray;

    public NavType(Class<?> clazz, boolean isArray) {
      this.clazz = clazz;
      this.isArray = isArray;
    }

    @Override
    public String toString() {
      return "Nav " + clazz.toString() + (isArray ? "[]" : "");
    }

    public static NavType fromArgType(String type, String pkgName) {
      return Companion.fromArgType(type, pkgName);
    }

    public static class Companion {

      public NavType fromArgType(String type, String pkgName) {
        try {
          String className = (type.startsWith(".") && pkgName != null) ? pkgName + type : type;
          if (type.equals("IAETest")) {
            throw new IllegalArgumentException(className + " is not Serializable or Parcelable.");
          } else {
            if (type.endsWith("[]")) {
              className = className.substring(0, className.length() - 2);
              return new NavType(Class.forName(className), true);
            } else {
              return new NavType(Class.forName(className), false);
            }
          }
        } catch (ClassNotFoundException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
}
