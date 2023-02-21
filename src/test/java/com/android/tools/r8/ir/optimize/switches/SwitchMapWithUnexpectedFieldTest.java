// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.ToolHelper.getClassFilesForInnerClasses;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This tests that we gracefully handle the case where a switch map refers to enum fields that are
 * not present in the enum definition.
 *
 * <p>This situation may happen due to separate compilation. For example, a project may depend on a
 * library that has been compiled against version X of a given enum but bundle version Y of the
 * enum.
 *
 * <p>See also b/154315490.
 */
@RunWith(Parameterized.class)
public class SwitchMapWithUnexpectedFieldTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchMapWithUnexpectedFieldTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramFiles(getSwitchMapProgramFile())
        .addProgramClassFileData(
            transformer(IncompleteEnum.class)
                .setClassDescriptor(descriptor(CompleteEnum.class))
                .transform())
        .addKeepMainRule(TestClass.class)
        .addDontObfuscate()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B", "D");
  }

  private static ClassReference getSwitchMapClassReference() {
    return Reference.classFromTypeName(SwitchMapWithUnexpectedFieldTest.class.getTypeName() + "$1");
  }

  private static Path getSwitchMapProgramFile() throws IOException {
    String switchMapFileName =
        StringUtils.join(File.separator, getSwitchMapClassReference().getBinaryName().split("/"))
            + ".class";
    return getClassFilesForInnerClasses(SwitchMapWithUnexpectedFieldTest.class).stream()
        .filter(file -> file.toString().endsWith(switchMapFileName))
        .findFirst()
        .get();
  }

  static class TestClass {

    public static void main(String[] args) {
      for (CompleteEnum value : CompleteEnum.values()) {
        switch (value) {
          case A:
            System.out.println("A");
            break;

          case B:
            System.out.println("B");
            break;

          case C:
            System.out.println("C");
            break;

          case D:
            System.out.println("D");
            break;

          case E:
            System.out.println("E");
            break;
        }
      }
    }
  }

  enum CompleteEnum {
    A,
    B,
    C,
    D,
    E;
  }

  enum IncompleteEnum {
    B,
    D;
  }
}
