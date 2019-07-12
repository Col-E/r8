// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryContentTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DesugaredLibraryContentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getApiLevel()));
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

    if (requiresCoreLibDesugaring(parameters)) {
      InstructionSubject long8Invoke =
          inspector
              .clazz("j$.util.stream.LongStream$-CC")
              .uniqueMethodWithName("range")
              .streamInstructions()
              .filter(InstructionSubject::isInvokeStatic)
              .collect(Collectors.toList())
              .get(1);
      assertFalse(long8Invoke.toString().contains("Long8;->"));
      assertTrue(long8Invoke.toString().contains("backportedMethods"));
      for (FoundClassSubject clazz : inspector.allClasses()) {
        if (!(clazz.getOriginalName().equals("j$.lang.Long8")
            || clazz.getOriginalName().equals("j$.lang.Integer8")
            || clazz.getOriginalName().equals("j$.lang.Double8"))) {
          for (FoundMethodSubject method : clazz.allMethods()) {
            if (!method.isAbstract()) {
              assertTrue(method.streamInstructions().noneMatch(instr -> instr.isInvoke() && (
                  instr.toString().contains("Double8")
                      || instr.toString().contains("Integer8")
                      || instr.toString().contains("Long8"))
              ));
            }
          }
        }
      }
    }

    // TODO(134732760): This should be a 0 count.
    assertEquals(
        requiresCoreLibDesugaring(parameters) ? 0 : 5,
        inspector.allClasses().stream()
            .map(ClassSubject::getOriginalName)
            .filter(name -> name.startsWith("java."))
            .filter(name -> !interfaces.contains(name))
            .count());

    // TODO(134732760): Remove this when above is a 0 count.
    assertEquals(
        requiresCoreLibDesugaring(parameters) ? 0 : 5,
        inspector.allClasses().stream()
            .map(ClassSubject::getOriginalName)
            .filter(name -> name.startsWith("java."))
            .filter(name -> !interfaces.contains(name))
            .filter(name -> !name.contains("$-CC"))
            .filter(name -> !name.contains("-$$Lambda"))
            .count());
  }
}
