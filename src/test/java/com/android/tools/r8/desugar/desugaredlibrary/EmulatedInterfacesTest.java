// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

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
public class EmulatedInterfacesTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;

  @Parameters(name = "{1}, shrinkDesugaredLibrary: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimes().withAllApiLevels().build());
  }

  public EmulatedInterfacesTest(boolean shrinkDesugaredLibrary, TestParameters parameters) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testEmulatedInterface() throws Exception {
    Assume.assumeTrue(requiresEmulatedInterfaceCoreLibDesugaring(parameters));
    CodeInspector inspector =
        new CodeInspector(
            buildDesugaredLibrary(
                parameters.getApiLevel(), "-keep class **$-EL", shrinkDesugaredLibrary));
    assertEmulateInterfaceClassesPresentWithDispatchMethods(inspector);
    assertCollectionMethodsPresentWithCorrectDispatch(inspector);
  }

  private void assertEmulateInterfaceClassesPresentWithDispatchMethods(CodeInspector inspector) {
    List<FoundClassSubject> emulatedInterfaces = getEmulatedInterfaces(inspector);
    int numDispatchClasses = 9;
    assertThat(inspector.clazz("j$.util.Map$Entry$-EL"), not(isPresent()));
    assertEquals(numDispatchClasses, emulatedInterfaces.size());
    for (FoundClassSubject clazz : emulatedInterfaces) {
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

  private List<FoundClassSubject> getEmulatedInterfaces(CodeInspector inspector) {
    return inspector.allClasses().stream()
        .filter(
            clazz ->
                clazz
                    .getOriginalName()
                    .contains(InterfaceMethodRewriter.EMULATE_LIBRARY_CLASS_NAME_SUFFIX))
        .collect(Collectors.toList());
  }

  private void assertCollectionMethodsPresentWithCorrectDispatch(CodeInspector inspector) {
    DexClass collectionDispatch = inspector.clazz("j$.util.Collection$-EL").getDexProgramClass();
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
