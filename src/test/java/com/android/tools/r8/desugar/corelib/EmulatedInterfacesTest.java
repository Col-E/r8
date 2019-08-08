// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmulatedInterfacesTest extends CoreLibDesugarTestBase {

  private final TestParameters parameters;
  private final boolean shrinkCoreLibrary;

  @Parameters(name = "{1}, shrinkCoreLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public EmulatedInterfacesTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkCoreLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testEmulatedInterface() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    CodeInspector inspector =
        new CodeInspector(
            buildDesugaredLibrary(
                parameters.getApiLevel(), "-keep class **$-EL", shrinkCoreLibrary));
    assertEmulateInterfaceClassesPresentWithDispatchMethods(inspector);
    assertCollectionMethodsPresentWithCorrectDispatch(inspector);
  }

  private void assertEmulateInterfaceClassesPresentWithDispatchMethods(CodeInspector inspector) {
    List<FoundClassSubject> dispatchClasses =
        inspector.allClasses().stream()
            .filter(
                clazz ->
                    clazz
                        .getOriginalName()
                        .contains(InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX))
            .collect(Collectors.toList());
    int numDispatchClasses = 9;
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
  }

  private void assertCollectionMethodsPresentWithCorrectDispatch(CodeInspector inspector) {
    DexClass collectionDispatch = inspector.clazz("j$.util.Collection$-EL").getDexClass();
    for (DexEncodedMethod method : collectionDispatch.methods()) {
      int numCheckCast =
          (int)
              Stream.of(method.getCode().asDexCode().instructions)
                  .filter(Instruction::isCheckCast)
                  .count();
      if (method.qualifiedName().contains("spliterator")) {
        assertEquals(5, numCheckCast);
      } else {
        assertEquals(1, numCheckCast);
      }
    }
  }
}
