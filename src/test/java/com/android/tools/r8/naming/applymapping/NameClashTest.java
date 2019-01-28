// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

class LibraryClass {
  static String LIB_MSG = "LibraryClass::foo";
  void foo() {
    System.out.println(LIB_MSG);
  }
}

class AnotherLibraryClass {
  static String ANOTHERLIB_MSG = "AnotherLibraryClass::foo";
  void foo() {
    System.out.println(ANOTHERLIB_MSG);
  }
}

class ProgramClass extends LibraryClass {
  static String PRG_MSG = "ProgramClass::bar";
  void bar() {
    System.out.println(PRG_MSG);
  }

  public static void main(String[] args) {
    new AnotherLibraryClass().foo();
    ProgramClass instance = new ProgramClass();
    instance.foo();
    instance.bar();
  }
}

public class NameClashTest extends TestBase {

  @ClassRule
  public static TemporaryFolder temporaryFolder = ToolHelper.getTemporaryFolderForTest();

  private static Class<?> MAIN = ProgramClass.class;
  private static String EXPECTED_OUTPUT =
      StringUtils.lines(
          AnotherLibraryClass.ANOTHERLIB_MSG, LibraryClass.LIB_MSG, ProgramClass.PRG_MSG);

  private static Path prgJarThatUsesOriginalLib;
  private static Path prgJarThatUsesMinifiedLib;
  private static Path libJar;
  private Path mappingFile;

  @BeforeClass
  public static void setUpJars() throws Exception {
    prgJarThatUsesOriginalLib =
        temporaryFolder.newFile("prgOrginalLib.jar").toPath().toAbsolutePath();
    writeToJar(prgJarThatUsesOriginalLib, ImmutableList.of(ToolHelper.getClassAsBytes(MAIN)));
    prgJarThatUsesMinifiedLib =
        temporaryFolder.newFile("prgMinifiedLib.jar").toPath().toAbsolutePath();
    writeToJar(prgJarThatUsesMinifiedLib, ImmutableList.of(ProgramClassDump.dump()));
    libJar = temporaryFolder.newFile("lib.jar").toPath().toAbsolutePath();
    writeToJar(libJar, ImmutableList.of(
        ToolHelper.getClassAsBytes(LibraryClass.class),
        ToolHelper.getClassAsBytes(AnotherLibraryClass.class)));
  }

  @Before
  public void setUpMappingFile() throws Exception {
    mappingFile = temp.newFile("mapping.txt").toPath().toAbsolutePath();
  }

  private String invertedMapping() {
     return StringUtils.lines(
        "A -> " + LibraryClass.class.getTypeName() + ":",
        "  void a() -> foo",
        "B -> " + AnotherLibraryClass.class.getTypeName() + ":",
        "  void a() -> foo"
    );
  }

  // Note that all the test mappings below still need identity mappings for classes/memebers that
  // are not renamed, for some reasons:
  // 1) to mimic how R8 generates the mapping, where identity mappings are used in the same way.
  // 2) otherwise, those classes/members will be renamed if minification is enabled, resulting in
  //   no name clash, which is definitely not intended.

  private String mappingToExistingClassName() {
    return StringUtils.lines(
        LibraryClass.class.getTypeName()
            + " -> " + AnotherLibraryClass.class.getTypeName() + ":",
        AnotherLibraryClass.class.getTypeName()
            + " -> " + AnotherLibraryClass.class.getTypeName() + ":"
    );
  }

  private String mappingToTheSameClassName() {
    return StringUtils.lines(
        LibraryClass.class.getTypeName() + " -> Clash:",
        AnotherLibraryClass.class.getTypeName() + " -> Clash:"
    );
  }

  private String mappingToExistingMethodName() {
    return StringUtils.lines(
        LibraryClass.class.getTypeName() + " -> A:",
        "  void foo() -> bar",
        AnotherLibraryClass.class.getTypeName() + " -> B:",
        ProgramClass.class.getTypeName() + " -> " + ProgramClass.class.getTypeName() + ":",
        "  void bar() -> bar"
    );
  }

  private String mappingToTheSameMethodName() {
    return StringUtils.lines(
        LibraryClass.class.getTypeName() + " -> A:",
        "  void foo() -> clash",
        AnotherLibraryClass.class.getTypeName() + " -> B:",
        "  void foo() -> clash",
        ProgramClass.class.getTypeName() + " -> " + ProgramClass.class.getTypeName() + ":",
        "  void bar() -> clash"
    );
  }

  private void testProguard_inputJar(Path mappingFile) throws Exception {
    testForProguard()
        .addProgramFiles(libJar)
        .addProgramFiles(prgJarThatUsesOriginalLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void testR8_inputJar(Path mappingFile) throws Exception {
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addProgramFiles(libJar)
        .addProgramFiles(prgJarThatUsesOriginalLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void testProguard_originalLibraryJar(Path mappingFile) throws Exception {
    testForProguard()
        .addLibraryFiles(libJar)
        .addProgramFiles(prgJarThatUsesOriginalLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void testR8_originalLibraryJar(Path mappingFile) throws Exception {
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar(), libJar)
        .addProgramFiles(prgJarThatUsesOriginalLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void testProguard_minifiedLibraryJar(Path mappingFile) throws Exception {
    testForProguard()
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar(), libJar)
        .addProgramFiles(prgJarThatUsesMinifiedLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void testR8_minifiedLibraryJar(Path mappingFile) throws Exception {
    testForR8(Backend.DEX)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar(), libJar)
        .addProgramFiles(prgJarThatUsesMinifiedLib)
        .addKeepMainRule(MAIN)
        .addKeepRules("-applymapping " + mappingFile)
        .noTreeShaking()
        .compile()
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testProguard_prgClassRenamedToExistingPrgClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingClassName());
    try {
      testProguard_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("Duplicate jar entry"));
      assertThat(e.getMessage(), containsString("AnotherLibraryClass.class"));
    }
  }

  @Test
  public void testR8_prgClassRenamedToExistingPrgClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingClassName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Program type already present"));
      assertThat(e.getCause().getMessage(), containsString("AnotherLibraryClass"));
    }
  }

  @Test
  public void testProguard_originalLibClassRenamedToExistingLibClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingClassName());
    try {
      testProguard_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find referenced method"));
      assertThat(e.getMessage(), containsString("ProgramClass"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_originalLibClassRenamedToExistingLibClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingClassName());
    testR8_originalLibraryJar(mappingFile);
  }

  @Test
  public void testProguard_prgClassesRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameClassName());
    try {
      testProguard_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("Duplicate jar entry [Clash.class]"));
    }
  }

  @Test
  public void testR8_prgClassesRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameClassName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("Program type already present"));
      assertThat(e.getCause().getMessage(), containsString("Clash"));
    }
  }

  @Test
  public void testProguard_originalLibClassesRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameClassName());
    try {
      testProguard_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find referenced method"));
      assertThat(e.getMessage(), containsString("ProgramClass"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_originalLibClassesRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameClassName());
    testR8_originalLibraryJar(mappingFile);
  }

  @Test
  public void testProguard_prgMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    try {
      testProguard_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("method 'void bar()' can't be mapped to 'bar'"));
      assertThat(e.getMessage(), containsString("it would conflict with method 'foo'"));
      assertThat(e.getMessage(), containsString("which is already being mapped to 'bar'"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_prgMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("method 'void bar()' can't be mapped to 'bar'"));
      assertThat(e.getMessage(), containsString("it would conflict with method 'foo'"));
      assertThat(e.getMessage(), containsString("which is already being mapped to 'bar'"));
    }
  }

  @Test
  public void testProguard_originalLibMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    try {
      testProguard_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find referenced method"));
      assertThat(e.getMessage(), containsString("ProgramClass"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_originalLibMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    testR8_originalLibraryJar(mappingFile);
  }

  @Test
  public void testProguard_prgMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    try {
      testProguard_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("method 'void bar()' can't be mapped to 'clash'"));
      assertThat(e.getMessage(), containsString("it would conflict with method 'foo'"));
      assertThat(e.getMessage(), containsString("which is already being mapped to 'clash'"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_prgMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("method 'void bar()' can't be mapped to 'bar'"));
      assertThat(e.getMessage(), containsString("it would conflict with method 'foo'"));
      assertThat(e.getMessage(), containsString("which is already being mapped to 'bar'"));
    }
  }

  @Test
  public void testProguard_originalLibMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    try {
      testProguard_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find referenced method"));
      assertThat(e.getMessage(), containsString("ProgramClass"));
    }
  }

  @Ignore("b/123092153")
  @Test
  public void testR8_originalLibMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    testR8_originalLibraryJar(mappingFile);
  }

  @Test
  public void testProguard_minifiedLib() throws Exception {
    FileUtils.writeTextFile(mappingFile, invertedMapping());
    try {
      testProguard_minifiedLibraryJar(mappingFile);
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find superclass or interface A"));
    }
  }

  @Ignore("b/121305642")
  @Test
  public void testR8_minifiedLib() throws Exception {
    FileUtils.writeTextFile(mappingFile, invertedMapping());
    testR8_minifiedLibraryJar(mappingFile);
  }
}
