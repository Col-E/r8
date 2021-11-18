// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
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
  private final boolean canUseDefaultAndStaticInterfaceMethods;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  public ImplementedInterfacesTest(TestParameters parameters) {
    this.parameters = parameters;
    this.canUseDefaultAndStaticInterfaceMethods =
        parameters
            .getApiLevel()
            .isGreaterThanOrEqualTo(apiLevelWithDefaultInterfaceMethodsSupport());
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

  @Test
  public void testInterfaces() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8(parameters.getBackend())
        .addLibraryFiles(getLibraryFile())
        .addInnerClasses(ImplementedInterfacesTest.class)
        .setMinApi(parameters.getApiLevel())
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .inspect(this::checkInterfaces);
  }

  abstract static class MultipleInterfaces<T> implements List<T>, Serializable, Set<T> {

    // Disambiguate between default methods List.spliterator() and Set.spliterator()
    @Override
    public Spliterator<T> spliterator() {
      return Set.super.spliterator();
    }
  }
}
