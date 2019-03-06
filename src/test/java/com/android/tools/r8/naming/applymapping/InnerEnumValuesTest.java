// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.applymapping.Outer.InnerEnum;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InnerEnumValuesTest extends TestBase {
  private static final Class<?> MAIN = TestApp.class;
  private static final String RENAMED_NAME = "x.y.z$ie";
  private static final String EXPECTED_OUTPUT = StringUtils.lines("STATE_A", "STATE_B");

  private static Path mappingFile;
  private final Backend backend;
  private final boolean minification;

  @Parameterized.Parameters(name = "Backend: {0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values());
  }

  public InnerEnumValuesTest(Backend backend, boolean minification) {
    this.backend = backend;
    this.minification = minification;
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
    testForR8(backend)
        .addProgramClassesAndInnerClasses(Outer.class)
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile.toAbsolutePath())
        .minification(minification)
        .compile()
        .inspect(inspector -> {
          ClassSubject enumSubject = inspector.clazz(RENAMED_NAME);
          assertThat(enumSubject, isPresent());
          assertEquals(minification, enumSubject.isRenamed());
          String fieldName =
              minification
                  ? "a"        // minified name
                  : "state_X"; // mapped name without minification
          FieldSubject stateA = enumSubject.uniqueFieldWithName(fieldName);
          assertThat(stateA, isPresent());
        });
    // TODO(b/124177369): method signature Object Outer$InnerEnum[]#clone() left in values().
    //  .run(MAIN)
    //  .assertSuccessWithOutput(EXPECTED_OUTPUT);
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
