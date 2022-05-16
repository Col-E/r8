// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8DEBUG;
import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.D8_L8SHRINK;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ImplementedInterfacesTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;
  private final CompilationSpecification compilationSpecification;
  private final boolean canUseDefaultAndStaticInterfaceMethods;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        ImmutableList.of(D8_L8DEBUG, D8_L8SHRINK));
  }

  public ImplementedInterfacesTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
    this.compilationSpecification = compilationSpecification;
    this.canUseDefaultAndStaticInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
  }

  @Test
  public void testImplementedInterfaces() throws Throwable {
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .compile()
        .inspect(this::checkInterfaces);
  }

  private String desugaredJavaTypeNameFor(Class<?> clazz) {
    return clazz.getTypeName().replace("java.", "j$.");
  }

  private void checkInterfaces(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MultipleInterfaces.class);
    assertThat(clazz, isPresent());
    assertTrue(clazz.isImplementing(Serializable.class));
    assertTrue(clazz.isImplementing(Set.class));
    assertTrue(clazz.isImplementing(List.class));
    assertFalse(clazz.isImplementing(Collection.class));
    assertFalse(clazz.isImplementing(Iterable.class));
    if (!canUseDefaultAndStaticInterfaceMethods) {
      assertFalse(clazz.isImplementing(desugaredJavaTypeNameFor(Serializable.class)));
      assertTrue(clazz.isImplementing(desugaredJavaTypeNameFor(Set.class)));
      assertTrue(clazz.isImplementing(desugaredJavaTypeNameFor(List.class)));
      assertFalse(clazz.isImplementing(desugaredJavaTypeNameFor(Collection.class)));
      assertFalse(clazz.isImplementing(desugaredJavaTypeNameFor(Iterable.class)));
    }
  }

  abstract static class MultipleInterfaces<T> implements List<T>, Serializable, Set<T> {

    // Disambiguate between default methods List.spliterator() and Set.spliterator()
    @Override
    public Spliterator<T> spliterator() {
      return Set.super.spliterator();
    }
  }
}
