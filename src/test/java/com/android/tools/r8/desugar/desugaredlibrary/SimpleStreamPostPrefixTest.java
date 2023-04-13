// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.DEFAULT_SPECIFICATIONS;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.getJdk8Jdk11;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleStreamPostPrefixTest extends DesugaredLibraryTestBase {

  private static final String EXPECTED_RESULT = StringUtils.lines("3");

  private final TestParameters parameters;
  private final CompilationSpecification compilationSpecification;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        getJdk8Jdk11(),
        DEFAULT_SPECIFICATIONS);
  }

  public SimpleStreamPostPrefixTest(
      TestParameters parameters,
      LibraryDesugaringSpecification libraryDesugaringSpecification,
      CompilationSpecification compilationSpecification) {
    this.parameters = parameters;
    this.compilationSpecification = compilationSpecification;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  @Test
  public void testSimpleStreamPostPrefix() throws Throwable {
    Assume.assumeTrue(libraryDesugaringSpecification.hasEmulatedInterfaceDesugaring(parameters));
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClasses(getClass())
        .addKeepMainRule(Executor.class)
        .setL8PostPrefix("j$$.")
        .compile()
        .inspectL8(this::allRewritten)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(EXPECTED_RESULT);
  }

  private void allRewritten(CodeInspector codeInspector) {
    assertTrue(
        codeInspector.allClasses().stream()
            .allMatch(c -> c.toString().startsWith("j$.j$$.") || c.toString().startsWith("java.")));
    Map<InstructionSubject, FoundMethodSubject> errors = new IdentityHashMap<>();
    codeInspector.forAllClasses(
        clazz ->
            clazz.forAllMethods(
                m -> {
                  if (m.hasCode()) {
                    for (InstructionSubject instruction : m.instructions()) {
                      if (instruction.isInvoke()) {
                        if (!isValidJ$$Type(instruction.getMethod().getHolderType())) {
                          errors.put(instruction, m);
                        }
                        for (DexType referencedType :
                            instruction.getMethod().getReferencedTypes()) {
                          if (!isValidJ$$Type(referencedType)) {
                            errors.put(instruction, m);
                          }
                        }
                      } else if (instruction.isFieldAccess()) {
                        if (!isValidJ$$Type(instruction.getField().getHolderType())) {
                          errors.put(instruction, m);
                        }
                        if (!isValidJ$$Type(instruction.getField().getType())) {
                          errors.put(instruction, m);
                        }
                      }
                    }
                  }
                }));
    assertTrue("Invalid invokes: " + errors, errors.isEmpty());
  }

  private boolean isValidJ$$Type(DexType type) {
    return !type.toString().startsWith("j$.") || type.toString().startsWith("j$.j$$.");
  }

  @SuppressWarnings("unchecked")
  static class Executor {

    public static void main(String[] args) {
      ArrayList<Integer> integers = new ArrayList<>();
      integers.add(1);
      integers.add(2);
      integers.add(3);
      List<Integer> collectedList = integers.stream().map(i -> i + 3).collect(Collectors.toList());
      System.out.println(collectedList.size());
    }
  }
}
