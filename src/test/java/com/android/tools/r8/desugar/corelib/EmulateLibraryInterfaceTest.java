// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EmulateLibraryInterfaceTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public EmulateLibraryInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDispatchClasses() throws Exception {
    CodeInspector inspector = new CodeInspector(buildDesugaredLibrary(parameters.getRuntime()));
    List<FoundClassSubject> dispatchClasses =
        inspector.allClasses().stream()
            .filter(
                clazz ->
                    clazz
                        .getOriginalName()
                        .contains(InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX))
            .collect(Collectors.toList());
    int numDispatchClasses =
        this.parameters.getRuntime().asDex().getMinApiLevel().getLevel()
                < AndroidApiLevel.N.getLevel()
            ? 5
            : 0;
    assertEquals(numDispatchClasses, dispatchClasses.size());
    for (FoundClassSubject clazz : dispatchClasses) {
      assertTrue(
          clazz.allMethods().stream()
              .allMatch(
                  method ->
                      method.isStatic()
                          && method
                              .streamInstructions()
                              .anyMatch(InstructionSubject::isInstanceOf)));
    }
    if (this.parameters.getRuntime().asDex().getMinApiLevel().getLevel()
        < AndroidApiLevel.N.getLevel()) {
      DexClass collectionDispatch = inspector.clazz("java.util.Collection$-EL").getDexClass();
      for (DexEncodedMethod method : collectionDispatch.methods()) {
        int numCheckCast =
            (int)
                Stream.of(method.getCode().asDexCode().instructions)
                    .filter(Instruction::isCheckCast)
                    .count();
        if (method.qualifiedName().contains("spliterator")) {
          assertTrue(numCheckCast > 1);
        } else {
          assertEquals(1, numCheckCast);
        }
      }
    }
  }
}
