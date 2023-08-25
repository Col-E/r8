// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.ToolHelper.CHECKED_IN_R8_17_WITH_DEPS;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.RelocatorTestBuilder;
import com.android.tools.r8.RelocatorTestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RelocatorTest extends TestBase {

  private final boolean external;

  @Parameters(name = "{0}, external: {1}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  public RelocatorTest(TestParameters parameters, boolean external) {
    parameters.assertNoneRuntime();
    this.external = external;
  }

  @Test
  public void testRelocatorIdentity() throws Exception {
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .run()
        .inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, "", "");
  }

  @Test
  public void testRelocatorIdentityPackage() throws Exception {
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageMapping("com.android.tools.r8", "com.android.tools.r8")
        .run()
        .inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, "", "");
  }

  @Test
  public void testRelocatorEmptyToSomething() throws Exception {
    String originalPrefix = "";
    String newPrefix = "foo.bar.baz";
    testForRelocator(external)
        .addClassMapping(
            Reference.classFromClass(Object.class), Reference.classFromClass(Object.class))
        .addClassMapping(
            Reference.classFromClass(String.class), Reference.classFromClass(String.class))
        .addPackageAndAllSubPackagesMapping(
            Reference.packageFromString(originalPrefix), Reference.packageFromString(newPrefix))
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .run()
        .inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, originalPrefix, newPrefix + ".");
  }

  @Test
  public void testRelocatorSomethingToEmpty() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping(
            Reference.packageFromString(originalPrefix), Reference.packageFromString(""))
        .run()
        .inspectAllSignaturesNotContainingString(originalPrefix);
  }

  @Test
  public void testRelocateKeepsDebugInfo() throws Exception {
    PackageReference pkg = Reference.packageFromString("com.android.tools.r8");
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping(pkg, pkg)
        .run()
        .inspect(
            inspector -> {
              // Assert that all classes are the same, have the same methods and debug info:
              CodeInspector originalInspector = new CodeInspector(CHECKED_IN_R8_17_WITH_DEPS);
              for (FoundClassSubject clazz : originalInspector.allClasses()) {
                ClassSubject relocatedClass = inspector.clazz(clazz.getFinalName());
                assertThat(relocatedClass, isPresent());
                assertEquals(
                    clazz.getDexProgramClass().sourceFile,
                    relocatedClass.getDexProgramClass().sourceFile);
                for (FoundMethodSubject originalMethod : clazz.allMethods()) {
                  MethodSubject relocatedMethod =
                      relocatedClass.method(originalMethod.asMethodReference());
                  assertThat(relocatedMethod, isPresent());
                  assertEquals(
                      originalMethod.hasLineNumberTable(), relocatedMethod.hasLineNumberTable());
                  if (originalMethod.hasLineNumberTable()) {
                    // TODO(b/155303677): Figure out why we cannot assert the same lines.
                    // assertEquals(
                    //     originalMethod.getLineNumberTable().getLines().size(),
                    //     relocatedMethod.getLineNumberTable().getLines().size());
                  }
                  assertEquals(
                      originalMethod.hasLocalVariableTable(),
                      relocatedMethod.hasLocalVariableTable());
                  if (originalMethod.hasLocalVariableTable()) {
                    LocalVariableTable originalVariableTable =
                        originalMethod.getLocalVariableTable();
                    LocalVariableTable relocatedVariableTable =
                        relocatedMethod.getLocalVariableTable();
                    assertEquals(originalVariableTable.size(), relocatedVariableTable.size());
                    for (int i = 0; i < originalVariableTable.getEntries().size(); i++) {
                      LocalVariableTableEntry originalEntry = originalVariableTable.get(i);
                      LocalVariableTableEntry relocatedEntry = relocatedVariableTable.get(i);
                      assertEquals(originalEntry.name, relocatedEntry.name);
                      assertEquals(originalEntry.signature, relocatedEntry.signature);
                      assertEquals(originalEntry.type.toString(), relocatedEntry.type.toString());
                    }
                  }
                }
              }
            });
  }

  @Test
  public void testRelocateAll() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "foo.bar.baz";
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping(
            Reference.packageFromString(originalPrefix), Reference.packageFromString(newPrefix))
        .addPackageAndAllSubPackagesMapping("some.package.that.does.not.exist", "foo")
        .run()
        .inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, originalPrefix, newPrefix);
  }

  @Test
  public void testOrderingOfPrefixes() throws Exception {
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping("com.android", "foo.bar.baz")
        .addPackageAndAllSubPackagesMapping("com.android.tools.r8", "qux")
        .run()
        // Because we see "com.android.tools.r8" before seeing "com.android" we always choose qux.
        .inspectAllClassesRelocated(
            ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, "com.android.tools.r8", "qux")
        .inspectAllSignaturesNotContainingString("foo.bar.baz");
  }

  @Test
  public void testNoReEntry() throws Exception {
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping("com.android", "foo.bar.baz")
        .addPackageAndAllSubPackagesMapping("foo.bar.baz", "qux")
        .run()
        .inspect(
            inspector -> {
              // Assert that no mappings of com.android.tools.r8 -> qux exists.
              assertTrue(
                  inspector.allClasses().stream()
                      .noneMatch(clazz -> clazz.getFinalName().startsWith("qux")));
            });
  }

  @Test
  public void testMultiplePackages() throws Exception {
    RelocatorTestBuilder testBuilder =
        testForRelocator(external).addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS);
    Set<String> seenPackages = new HashSet<>();
    List<Pair<String, String>> packageMappings = new ArrayList<>();
    CodeInspector inspector = new CodeInspector(CHECKED_IN_R8_17_WITH_DEPS);
    int packageNameCounter = 0;
    // Generate a mapping for each package name directly below com.android.tools.r8.
    for (FoundClassSubject clazz : inspector.allClasses()) {
      String packageName = clazz.getDexProgramClass().getType().getPackageName();
      String prefix = "com.android.tools.r8.";
      if (!packageName.startsWith(prefix)) {
        continue;
      }
      int nextPackageNameIndex = packageName.indexOf('.', prefix.length());
      if (nextPackageNameIndex > prefix.length()) {
        String mappedPackageName =
            prefix + packageName.substring(prefix.length(), nextPackageNameIndex);
        if (seenPackages.add(mappedPackageName)) {
          String relocatedPackageName = "number" + packageNameCounter++;
          packageMappings.add(new Pair<>(mappedPackageName, relocatedPackageName));
          testBuilder.addPackageAndAllSubPackagesMapping(mappedPackageName, relocatedPackageName);
        }
      }
    }
    RelocatorTestCompileResult result = testBuilder.run();
    for (Pair<String, String> packageMapping : packageMappings) {
      result.inspectAllClassesRelocated(
          CHECKED_IN_R8_17_WITH_DEPS, packageMapping.getFirst(), packageMapping.getSecond());
    }
  }

  @Test
  public void testPartialPrefix() throws Exception {
    String originalPrefix = "com.android.tools.r";
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping(originalPrefix, "i_cannot_w")
        .run()
        .inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, originalPrefix, originalPrefix);
  }

  @Test
  public void testBootstrap() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "relocated_r8";
    RelocatorTestCompileResult result =
        testForRelocator(external)
            .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
            .addPackageAndAllSubPackagesMapping(originalPrefix, newPrefix)
            .run();
    // Check that all classes has been remapped.
    result.inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, originalPrefix, newPrefix);
    result.inspectAllSignaturesNotContainingString(originalPrefix);
    // We should be able to call the relocated relocator.
    Path bootstrapOutput = temp.newFile("bootstrap.jar").toPath();
    List<Path> classPath = new ArrayList<>();
    classPath.add(result.getOutput());
    ProcessResult processResult =
        ToolHelper.runJava(
            CfRuntime.getCheckedInJdk17(),
            classPath,
            newPrefix + ".SwissArmyKnife",
            "relocator",
            "--input",
            result.getOutput().toString(),
            "--output",
            bootstrapOutput.toString(),
            "--map",
            newPrefix + "->" + originalPrefix);
    System.out.println(processResult.stderr);
    assertEquals(0, processResult.exitCode);
    RelocatorTestCompileResult bootstrapResult = new RelocatorTestCompileResult(bootstrapOutput);
    bootstrapResult.inspectAllClassesRelocated(result.getOutput(), newPrefix, originalPrefix);
    bootstrapResult.inspectAllSignaturesNotContainingString(newPrefix);
    // Assert that this is in fact an identity transformation.
    bootstrapResult.inspectAllClassesRelocated(CHECKED_IN_R8_17_WITH_DEPS, "", "");
  }

  @Test
  public void testNest() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "com.android.tools.r8";
    CodeInspector originalInspector = new CodeInspector(CHECKED_IN_R8_17_WITH_DEPS);
    // Assert that all classes are the same, have the same methods and nest info.
    testForRelocator(external)
        .addProgramFiles(CHECKED_IN_R8_17_WITH_DEPS)
        .addPackageAndAllSubPackagesMapping(originalPrefix, newPrefix)
        .run()
        .inspect(
            relocatedInspector -> {
              for (FoundClassSubject originalSubject : originalInspector.allClasses()) {
                ClassSubject relocatedSubject =
                    relocatedInspector.clazz(originalSubject.getFinalName());
                assertThat(relocatedSubject, isPresent());
                DexClass originalClass = originalSubject.getDexProgramClass();
                DexClass relocatedClass = relocatedSubject.getDexProgramClass();
                assertEquals(originalClass.isNestHost(), relocatedClass.isNestHost());
                assertEquals(originalClass.isNestMember(), relocatedClass.isNestMember());
                if (originalClass.isInANest()) {
                  assertEquals(
                      originalClass.getNestHost().descriptor,
                      relocatedClass.getNestHost().descriptor);
                }
              }
            });
  }
}
