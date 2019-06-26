// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public DesugaredLibraryContentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getRuntime()));
    assertThat(inspector.clazz("j$.util.Optional"), isPresent());
    assertThat(inspector.clazz("j$.util.OptionalInt"), isPresent());
    assertThat(inspector.clazz("j$.util.OptionalLong"), isPresent());
    assertThat(inspector.clazz("j$.util.OptionalDouble"), isPresent());
    assertThat(inspector.clazz("j$.util.function.Function"), isPresent());
    assertThat(inspector.clazz("j$.time.Clock"), isPresent());

    ImmutableSet interfaces =
        ImmutableSet.of(
            "java.util.List",
            "java.util.concurrent.TransferQueue",
            "java.util.concurrent.ConcurrentMap",
            "java.util.Queue",
            "java.util.concurrent.ConcurrentNavigableMap",
            "java.util.NavigableMap",
            "java.util.ListIterator",
            "java.util.SortedMap",
            "java.util.Set",
            "java.util.List",
            "java.util.NavigableSet",
            "java.util.SortedSet",
            "java.util.Deque",
            "java.util.concurrent.BlockingDeque",
            "java.util.concurrent.BlockingQueue");

    // TODO(134732760): Remove this debugging code.
    inspector
        .allClasses()
        .forEach(
            clazz -> {
              if (clazz.getOriginalName().startsWith("java.")
                  && !interfaces.contains(clazz.getOriginalName())) {
                System.out.println(clazz.getOriginalName());
              }
            });

    // TODO(134732760): This should be a 0 count.
    assertEquals(
        parameters.getRuntime().asDex().getMinApiLevel().getLevel() < AndroidApiLevel.N.getLevel()
            ? 38
            : 27,
        inspector.allClasses().stream()
            .map(ClassSubject::getOriginalName)
            .filter(name -> name.startsWith("java."))
            .filter(name -> !interfaces.contains(name))
            .count());

    // TODO(134732760): Remove this when above is a 0 count.
    assertEquals(
        parameters.getRuntime().asDex().getMinApiLevel().getLevel() < AndroidApiLevel.N.getLevel()
            ? 13
            : 8,
        inspector.allClasses().stream()
            .map(ClassSubject::getOriginalName)
            .filter(name -> name.startsWith("java."))
            .filter(name -> !interfaces.contains(name))
            .filter(name -> !name.contains("$-CC"))
            .filter(name -> !name.contains("-$$Lambda"))
            .count());
  }
}
