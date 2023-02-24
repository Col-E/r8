// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping.shared;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.applymapping.shared.ProgramWithLibraryClasses.AnotherLibraryClass;
import com.android.tools.r8.naming.applymapping.shared.ProgramWithLibraryClasses.LibraryClass;
import com.android.tools.r8.naming.applymapping.shared.ProgramWithLibraryClasses.ProgramClass;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

public class NameClashTest extends TestBase {

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
        getStaticTemp().newFile("prgOrginalLib.jar").toPath().toAbsolutePath();
    writeClassFileDataToJar(
        prgJarThatUsesOriginalLib, ImmutableList.of(ToolHelper.getClassAsBytes(MAIN)));
    prgJarThatUsesMinifiedLib =
        getStaticTemp().newFile("prgMinifiedLib.jar").toPath().toAbsolutePath();
    writeClassFileDataToJar(prgJarThatUsesMinifiedLib, ImmutableList.of(ProgramClassDump.dump()));
    libJar = getStaticTemp().newFile("lib.jar").toPath().toAbsolutePath();
    writeClassesToJar(
        libJar,
        ImmutableList.of(
            ProgramWithLibraryClasses.class, LibraryClass.class, AnotherLibraryClass.class));
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

  private String mappingToAlreadyMappedName() {
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

  private String mappingToExistingClassName() {
    return StringUtils.lines(
        LibraryClass.class.getTypeName() + " -> " + ProgramClass.class.getTypeName() + ":",
        "  void foo() -> bar",
        AnotherLibraryClass.class.getTypeName() + " -> B:");
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
        .addDontObfuscate()
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

  @Test
  public void testProguard_prgClassRenamedToExistingPrgClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToAlreadyMappedName());
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
    FileUtils.writeTextFile(mappingFile, mappingToAlreadyMappedName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("map to same name"));
      assertThat(e.getCause().getMessage(), containsString("$AnotherLibraryClass"));
      assertThat(e.getCause().getMessage(), containsString("$LibraryClass"));
    }
  }

  @Test
  public void testProguard_originalLibClassRenamedToExistingLibClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToAlreadyMappedName());
    try {
      testProguard_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getMessage(), containsString("can't find referenced method"));
      assertThat(e.getMessage(), containsString("ProgramClass"));
    }
  }

  @Test
  public void testR8_originalLibClassRenamedToSameLibClass() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToAlreadyMappedName());
    try {
      testR8_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("map to same name"));
      assertThat(e.getCause().getMessage(), containsString("$AnotherLibraryClass"));
      assertThat(e.getCause().getMessage(), containsString("$LibraryClass"));
    }
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
      assertThat(e.getCause().getMessage(), containsString("map to same name"));
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

  @Test
  public void testR8_originalLibClassesRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameClassName());
    try {
      testR8_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("map to same name"));
      assertThat(e.getCause().getMessage(), containsString("Clash"));
    }
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

  @Test
  @Ignore("b/136697829")
  public void testR8_prgMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("cannot be mapped to 'bar'"));
      assertThat(
          e.getCause().getMessage(),
          containsString(
              "because it is in conflict with an existing member with the same signature."));
      assertThat(
          e.getCause().getMessage(), containsString(ProgramClass.class.getTypeName() + ".bar()"));
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

  @Test
  @Ignore("b/136697829")
  public void testR8_originalLibMethodRenamedToExistingName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToExistingMethodName());
    try {
      testR8_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("cannot be mapped to 'bar'"));
      assertThat(
          e.getCause().getMessage(),
          containsString(
              "because it is in conflict with an existing member with the same signature."));
      assertThat(
          e.getCause().getMessage(), containsString(ProgramClass.class.getTypeName() + ".bar()"));
    }
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

  @Test
  @Ignore("b/136697829")
  public void testR8_prgMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    try {
      testR8_inputJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("cannot be mapped to 'clash'"));
      assertThat(
          e.getCause().getMessage(),
          containsString(
              "because it is in conflict with an existing member with the same signature."));
      assertThat(
          e.getCause().getMessage(), containsString(ProgramClass.class.getTypeName() + ".bar()"));
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

  @Test
  @Ignore("b/136697829")
  public void testR8_originalLibMethodRenamedToSameName() throws Exception {
    FileUtils.writeTextFile(mappingFile, mappingToTheSameMethodName());
    try {
      testR8_originalLibraryJar(mappingFile);
      fail("Expect compilation failure.");
    } catch (CompilationFailedException e) {
      assertThat(e.getCause().getMessage(), containsString("cannot be mapped to 'clash'"));
      assertThat(
          e.getCause().getMessage(),
          containsString(
              "because it is in conflict with an existing member with the same signature."));
      assertThat(
          e.getCause().getMessage(), containsString(ProgramClass.class.getTypeName() + ".bar()"));
    }
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
}
