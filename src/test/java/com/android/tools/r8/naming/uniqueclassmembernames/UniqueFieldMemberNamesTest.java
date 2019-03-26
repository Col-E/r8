// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.uniqueclassmembernames;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UniqueFieldMemberNamesTest extends TestBase {

  @ClassRule public static TemporaryFolder tempFolder = ToolHelper.getTemporaryFolderForTest();

  private static Path programClassA;
  private static Path programClassB;

  @BeforeClass
  public static void setup() throws Exception {
    {
      JasminBuilder builder = new JasminBuilder();
      ClassBuilder classBuilder = builder.addClass("A", "java/lang/Object");
      classBuilder.addField("public", "a", "Ljava/lang/Object;", null);
      classBuilder.addField("public", "f", "Ljava/lang/Object;", null);
      programClassA = tempFolder.getRoot().toPath().resolve("a.jar");
      builder.writeJar(programClassA);
    }
    {
      JasminBuilder builder = new JasminBuilder();
      ClassBuilder classBuilder = builder.addClass("B", "java/lang/Object");
      classBuilder.addField("public", "f", "Ljava/lang/Object;", null);
      classBuilder.addField("public", "f", "Ljava/lang/String;", null);
      programClassB = tempFolder.getRoot().toPath().resolve("b.jar");
      builder.writeJar(programClassB);
    }
  }

  @Test
  public void testR8() throws Exception {
    runTest(testForR8(Backend.DEX), true);
  }

  @Test
  public void testR8WithLibrary() throws Exception {
    runTestWithLibrary(testForR8(Backend.DEX), true);
  }

  @Test
  public void testProguard() throws Exception {
    runTest(testForProguard(), false);
  }

  @Test
  public void testProguardWithLibrary() throws Exception {
    runTestWithLibrary(testForProguard(), false);
  }

  private void runTest(TestShrinkerBuilder<?, ?, ?, ?, ?> builder, boolean isR8) throws Exception {
    CodeInspector inspector =
        builder
            .addProgramFiles(programClassA, programClassB)
            .addKeepRules("-useuniqueclassmembernames")
            .addKeepRules("-keep class A { java.lang.Object a; }")
            .noTreeShaking()
            .compile()
            .inspector();

    ClassSubject aClassSubject = inspector.clazz("A");
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = inspector.clazz("B");
    assertThat(bClassSubject, isPresent());

    FieldSubject fieldSubject1 = aClassSubject.field("java.lang.Object", "f");
    assertThat(fieldSubject1, isPresent());
    assertThat(fieldSubject1, isRenamed());
    assertNotEquals("a", fieldSubject1.getFinalName());

    FieldSubject fieldSubject2 = bClassSubject.field("java.lang.Object", "f");
    assertThat(fieldSubject2, isPresent());
    assertThat(fieldSubject2, isRenamed());
    assertEquals(fieldSubject2.getFinalName(), fieldSubject1.getFinalName());

    FieldSubject fieldSubject3 = bClassSubject.field("java.lang.String", "f");
    assertThat(fieldSubject3, isPresent());
    assertThat(fieldSubject3, isRenamed());
    if (isR8) {
      // TODO(b/128973195): Fields should be given distinct names due to -useuniqueclassmembernames.
      assertEquals(fieldSubject2.getFinalName(), fieldSubject3.getFinalName());
    } else {
      assertNotEquals(fieldSubject2.getFinalName(), fieldSubject3.getFinalName());
    }
  }

  private void runTestWithLibrary(TestShrinkerBuilder<?, ?, ?, ?, ?> builder, boolean isR8)
      throws Exception {
    CodeInspector inspector =
        builder
            .addProgramFiles(programClassB)
            .addLibraryFiles(programClassA, runtimeJar(Backend.DEX))
            .addKeepRules("-useuniqueclassmembernames")
            .addKeepRules("-keep class A { java.lang.Object a; }")
            .noTreeShaking()
            .compile()
            .inspector();

    ClassSubject bClassSubject = inspector.clazz("B");
    assertThat(bClassSubject, isPresent());

    FieldSubject fieldSubject2 = bClassSubject.field("java.lang.Object", "f");
    assertThat(fieldSubject2, isPresent());
    assertThat(fieldSubject2, not(isRenamed()));

    FieldSubject fieldSubject3 = bClassSubject.field("java.lang.String", "f");
    assertThat(fieldSubject3, isPresent());
    if (isR8) {
      // TODO(b/128973195): Fields should be given distinct names due to -useuniqueclassmembernames.
      assertThat(fieldSubject3, not(isRenamed()));
    } else {
      assertThat(fieldSubject3, isRenamed());
    }
  }
}
