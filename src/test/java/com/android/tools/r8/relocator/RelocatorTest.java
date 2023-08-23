// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.relocator.RelocatorUtils.inspectAllClassesRelocated;
import static com.android.tools.r8.relocator.RelocatorUtils.inspectAllSignaturesNotContainingString;
import static com.android.tools.r8.relocator.RelocatorUtils.runRelocator;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexClass;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, new HashMap<>(), output, external);
    inspectAllClassesRelocated(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, "", "");
  }

  @Test
  public void testRelocatorEmptyToSomething() throws Exception {
    String originalPrefix = "";
    String newPrefix = "foo.bar.baz";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    // TODO(b/155618698): Extend relocator with a richer language such that java.lang.Object is not
    //   relocated.
    RuntimeException compilationError =
        assertThrows(
            RuntimeException.class,
            () ->
                inspectAllClassesRelocated(
                    ToolHelper.CHECKED_IN_R8_17_WITH_DEPS,
                    output,
                    originalPrefix,
                    newPrefix + "."));
    assertThat(compilationError.getMessage(), containsString("must extend class java.lang.Object"));
  }

  @Test
  public void testRelocatorSomethingToEmpty() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    inspectAllSignaturesNotContainingString(output, originalPrefix);
  }

  @Test
  public void testRelocateKeepsDebugInfo() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "com.android.tools.r8";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    // Assert that all classes are the same, have the same methods and debug info:
    CodeInspector originalInspector = new CodeInspector(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS);
    CodeInspector relocatedInspector = new CodeInspector(output);
    for (FoundClassSubject clazz : originalInspector.allClasses()) {
      ClassSubject relocatedClass = relocatedInspector.clazz(clazz.getFinalName());
      assertThat(relocatedClass, isPresent());
      assertEquals(
          clazz.getDexProgramClass().sourceFile, relocatedClass.getDexProgramClass().sourceFile);
      for (FoundMethodSubject originalMethod : clazz.allMethods()) {
        MethodSubject relocatedMethod = relocatedClass.method(originalMethod.asMethodReference());
        assertThat(relocatedMethod, isPresent());
        assertEquals(originalMethod.hasLineNumberTable(), relocatedMethod.hasLineNumberTable());
        if (originalMethod.hasLineNumberTable()) {
          // TODO(b/155303677): Figure out why we cannot assert the same lines.
          // assertEquals(
          //     originalMethod.getLineNumberTable().getLines().size(),
          //     relocatedMethod.getLineNumberTable().getLines().size());
        }
        assertEquals(
            originalMethod.hasLocalVariableTable(), relocatedMethod.hasLocalVariableTable());
        if (originalMethod.hasLocalVariableTable()) {
          LocalVariableTable originalVariableTable = originalMethod.getLocalVariableTable();
          LocalVariableTable relocatedVariableTable = relocatedMethod.getLocalVariableTable();
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
  }

  @Test
  public void testRelocateAll() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "foo.bar.baz";
    Map<String, String> mapping = new HashMap<>();
    mapping.put("some.package.that.does.not.exist", "foo");
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    inspectAllClassesRelocated(
        ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, originalPrefix, newPrefix);
  }

  @Test
  public void testOrderingOfPrefixes() throws Exception {
    String originalPrefix = "com.android";
    String newPrefix = "foo.bar.baz";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    mapping.put("com.android.tools.r8", "qux");
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    // Because we see "com.android.tools.r8" before seeing "com.android" we always choose qux.
    inspectAllClassesRelocated(
        ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, "com.android.tools.r8", "qux");
    inspectAllSignaturesNotContainingString(output, "foo.bar.baz");
  }

  @Test
  public void testNoReEntry() throws Exception {
    // TODO(b/154909222): Check if this is the behavior we would like.
    String originalPrefix = "com.android";
    String newPrefix = "foo.bar.baz";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    mapping.put(newPrefix, "qux");
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    inspectAllClassesRelocated(
        ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, originalPrefix, newPrefix);
    // Assert that no mappings of com.android.tools.r8 -> qux exists.
    CodeInspector inspector = new CodeInspector(output);
    assertFalse(
        inspector.allClasses().stream().anyMatch(clazz -> clazz.getFinalName().startsWith("qux")));
  }

  @Test
  public void testMultiplePackages() throws Exception {
    Set<String> seenPackages = new HashSet<>();
    List<Pair<String, String>> packageMappings = new ArrayList<>();
    Map<String, String> mapping = new LinkedHashMap<>();
    CodeInspector inspector = new CodeInspector(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS);
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
          mapping.put(mappedPackageName, relocatedPackageName);
        }
      }
    }
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    for (Pair<String, String> packageMapping : packageMappings) {
      inspectAllClassesRelocated(
          ToolHelper.CHECKED_IN_R8_17_WITH_DEPS,
          output,
          packageMapping.getFirst(),
          packageMapping.getSecond());
    }
  }

  @Test
  public void testPartialPrefix() throws Exception {
    String originalPrefix = "com.android.tools.r";
    String newPrefix = "i_cannot_w";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    inspectAllClassesRelocated(
        ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, originalPrefix, originalPrefix);
  }

  @Test
  public void testBootstrap() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "relocated_r8";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    // Check that all classes has been remapped.
    inspectAllClassesRelocated(
        ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, output, originalPrefix, newPrefix);
    inspectAllSignaturesNotContainingString(output, originalPrefix);
    // We should be able to call the relocated relocator.
    Path bootstrapOutput = temp.newFile("bootstrap.jar").toPath();
    List<Path> classPath = new ArrayList<>();
    classPath.add(output);
    ProcessResult processResult =
        ToolHelper.runJava(
            CfRuntime.getCheckedInJdk17(),
            classPath,
            newPrefix + ".SwissArmyKnife",
            "relocator",
            "--input",
            output.toString(),
            "--output",
            bootstrapOutput.toString(),
            "--map",
            newPrefix + "->" + originalPrefix);
    System.out.println(processResult.stderr);
    assertEquals(0, processResult.exitCode);
    inspectAllClassesRelocated(output, bootstrapOutput, newPrefix, originalPrefix);
    inspectAllSignaturesNotContainingString(bootstrapOutput, newPrefix);
    // Assert that this is in fact an identity transformation.
    inspectAllClassesRelocated(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, bootstrapOutput, "", "");
  }

  @Test
  public void testNest() throws Exception {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "com.android.tools.r8";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS, mapping, output, external);
    // Assert that all classes are the same, have the same methods and nest info.
    CodeInspector originalInspector = new CodeInspector(ToolHelper.CHECKED_IN_R8_17_WITH_DEPS);
    CodeInspector relocatedInspector = new CodeInspector(output);
    for (FoundClassSubject originalSubject : originalInspector.allClasses()) {
      ClassSubject relocatedSubject = relocatedInspector.clazz(originalSubject.getFinalName());
      assertThat(relocatedSubject, isPresent());
      DexClass originalClass = originalSubject.getDexProgramClass();
      DexClass relocatedClass = relocatedSubject.getDexProgramClass();
      assertEquals(originalClass.isNestHost(), relocatedClass.isNestHost());
      assertEquals(originalClass.isNestMember(), relocatedClass.isNestMember());
      if (originalClass.isInANest()) {
        assertEquals(
            originalClass.getNestHost().descriptor, relocatedClass.getNestHost().descriptor);
      }
    }
  }

}
