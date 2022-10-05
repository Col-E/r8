// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.sourcelibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.applymapping.sourcelibrary.Outer.InnerEnum;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InnerEnumValuesTest extends TestBase {

  private static final Class<?> MAIN = TestApp.class;
  private static final String RENAMED_NAME = "x.y.z$ie";
  private static final String EXPECTED_OUTPUT = StringUtils.lines(
      "STATE_A", "STATE_B", "STATE_A", "STATE_B");

  private static Path mappingFile;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public InnerEnumValuesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Before
  public void setup() throws Exception {
    // Mapping file that describes that inner enum has been renamed.
    mappingFile = temp.newFile("mapping.txt").toPath();
    FileUtils.writeTextFile(
        mappingFile,
        StringUtils.lines(
            Outer.class.getTypeName() + " -> " + "x.y.z:",
            "    void <init>() -> <init>",
            InnerEnum.class.getTypeName() + " -> " + RENAMED_NAME + ":",
            "    " + InnerEnum.class.getTypeName() + " STATE_A -> state_X",
            "    " + InnerEnum.class.getTypeName() + " STATE_B -> state_Y",
            "    " + InnerEnum.class.getTypeName() + "[] $VALUES -> XY",
            "    void <clinit>() -> <clinit>",
            "    void <init>(java.lang.String,int) -> <init>",
            "    " + InnerEnum.class.getTypeName() + " valueOf(java.lang.String) -> valueOf",
            "    " + InnerEnum.class.getTypeName() + "[] values() -> values"));
  }

  @Test
  public void b124177369() throws Exception {
    CodeInspector inspector = testForR8(parameters.getBackend())
        .addProgramClassesAndInnerClasses(
            Outer.class)
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile.toAbsolutePath())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspector();
    ClassSubject enumSubject = inspector.clazz(RENAMED_NAME);
    assertThat(enumSubject, isPresent());
    FieldSubject fieldX = enumSubject.uniqueFieldWithOriginalName("STATE_A");
    assertThat(fieldX, isPresent());
    assertEquals("state_X", fieldX.getFinalName());
    FieldSubject fieldY = enumSubject.uniqueFieldWithOriginalName("STATE_B");
    assertThat(fieldY, isPresent());
    assertEquals("state_Y", fieldY.getFinalName());
  }
}

class Outer {
  public enum InnerEnum {
    STATE_A,
    STATE_B
  }
}

class TestApp {
  public static void main(String[] args) {
    for (InnerEnum i : InnerEnum.values()) {
      System.out.println(i);
    }
    System.out.println(InnerEnum.STATE_A);
    System.out.println(InnerEnum.STATE_B);
  }
}
