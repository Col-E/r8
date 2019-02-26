// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.applymapping.Outer.InnerEnum;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class InnerEnumValuesTest extends TestBase {
  private static Class<?> MAIN = TestApp.class;
  private static String EXPECTED_OUTPUT = StringUtils.lines("state_X", "state_Y");

  private static Path mappingFile;

  @Before
  public void setup() throws Exception {
    // Mapping file that describes that inner enum has been renamed.
    mappingFile = temp.newFile("mapping.txt").toPath();
    FileUtils.writeTextFile(
        mappingFile,
        StringUtils.lines(
            Outer.class.getTypeName() + " -> " + "x.y.z:",
            "    void <init>() -> <init>",
            InnerEnum.class.getTypeName() + " -> " + "x.y.z$ie:",
            "    " + InnerEnum.class.getTypeName() + " STATE_A -> state_X",
            "    " + InnerEnum.class.getTypeName() + " STATE_B -> state_Y",
            "    " + InnerEnum.class.getTypeName() + "[] $VALUES -> XY",
            "    void <clinit>() -> <clinit>",
            "    void <init>(java.lang.String,int) -> <init>",
            "    " + InnerEnum.class.getTypeName() + " valueOf(java.lang.String) -> valueOf",
            "    " + InnerEnum.class.getTypeName() + "[] values() -> values"));
  }

  @Ignore("b/124177369")
  @Test
  public void b124177369() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClassesAndInnerClasses(Outer.class)
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontoptimize")
        .addKeepRules("-applymapping " + mappingFile.toAbsolutePath())
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
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
  }
}
