// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.DescriptorUtils.getSimpleClassNameFromDescriptor;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AvoidRTest extends JasminTestBase {
  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public AvoidRTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test_withObfuscationDictionary() throws Exception {
    Path dictionary = temp.newFile("dictionary.txt").toPath();
    FileUtils.writeTextFile(dictionary, StringUtils.lines("P", "Q", "R", "S", "T"));
    Set<String> expectedNames = ImmutableSet.of("P", "Q", "S", "T");

    JasminBuilder jasminBuilder = new JasminBuilder();
    R8TestBuilder builder = testForR8(backend);
    for (int i = 0; i < 4; i++) {
      jasminBuilder.addClass("TopLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 4; i++) {
      jasminBuilder.addClass("p1/SecondLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 4; i++) {
      jasminBuilder.addClass("p1/p2/ThirdLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 4; i++) {
      jasminBuilder.addClass("p2/SecondLevel" + Integer.toString(i));
    }
    builder.addProgramClassFileData(jasminBuilder.buildClasses());
    Set<String> usedDescriptors = new HashSet<>();
    builder.noTreeShaking()
        .addKeepRules("-classobfuscationdictionary " + dictionary)
        .compile()
        .inspect(codeInspector -> {
          codeInspector.forAllClasses(classSubject -> {
            assertThat(classSubject, isRenamed());
            String renamedDescriptor = classSubject.getFinalDescriptor();
            assertTrue(usedDescriptors.add(renamedDescriptor));
            assertNotEquals("R", getSimpleClassNameFromDescriptor(renamedDescriptor));
            assertTrue(expectedNames.contains(getSimpleClassNameFromDescriptor(renamedDescriptor)));
          });
        });
  }

  @Test
  public void test_withoutPackageHierarchy() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    R8TestBuilder builder = testForR8(backend);
    for (int i = 0; i < 26 * 2; i++) {
      jasminBuilder.addClass("TestClass" + Integer.toString(i));
    }
    builder.addProgramClassFileData(jasminBuilder.buildClasses());
    Set<String> usedNames = new HashSet<>();
    builder.noTreeShaking()
        .compile()
        .inspect(codeInspector -> {
          codeInspector.forAllClasses(classSubject -> {
            assertThat(classSubject, isRenamed());
            assertTrue(usedNames.add(classSubject.getFinalName()));
            assertNotEquals("R", classSubject.getFinalName());
          });
        });
    assertTrue(usedNames.contains("Q"));
    assertTrue(usedNames.contains("S"));
  }

  private void test_withPackageHierarchy(String keepRule) throws Exception {
    R8TestBuilder builder = testForR8(backend);
    JasminBuilder jasminBuilder = new JasminBuilder();
    for (int i = 0; i < 26 * 2; i++) {
      jasminBuilder.addClass("TopLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 26 * 2; i++) {
      jasminBuilder.addClass("p1/SecondLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 26 * 2; i++) {
      jasminBuilder.addClass("p1/p2/ThirdLevel" + Integer.toString(i));
    }
    for (int i = 0; i < 26 * 2; i++) {
      jasminBuilder.addClass("p2/SecondLevel" + Integer.toString(i));
    }
    builder.addProgramClassFileData(jasminBuilder.buildClasses());
    Set<String> usedDescriptors = new HashSet<>();
    builder.noTreeShaking()
        .addKeepRules(keepRule)
        .compile()
        .inspect(codeInspector -> {
          codeInspector.forAllClasses(classSubject -> {
            assertThat(classSubject, isRenamed());
            String renamedDescriptor = classSubject.getFinalDescriptor();
            assertTrue(usedDescriptors.add(renamedDescriptor));
            assertNotEquals("R", getSimpleClassNameFromDescriptor(renamedDescriptor));
          });
        });
  }

  @Test
  public void test_withPackageHierarchy_default() throws Exception {
    test_withPackageHierarchy("");
  }

  @Test
  public void test_withPackageHierarchy_repackage() throws Exception {
    // Repackage every class to the top-level.
    test_withPackageHierarchy("-repackageclasses");
  }

  @Test
  public void test_withPackageHierarchy_flatten() throws Exception {
    // Repackage every package to the top-level.
    test_withPackageHierarchy("-flattenpackagehierarchy");
  }

}
