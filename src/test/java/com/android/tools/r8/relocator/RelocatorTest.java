// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.relocator;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable;
import com.android.tools.r8.utils.codeinspector.LocalVariableTable.LocalVariableTableEntry;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
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
  public void testRelocatorIdentity()
      throws IOException, CompilationFailedException, ExecutionException {
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, new HashMap<>(), output);
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, output, "", "");
  }

  @Test
  public void testRelocatorEmptyToSomething() throws Exception {
    String originalPrefix = "";
    String newPrefix = "foo.bar.baz";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    // TODO(b/155618698): Extend relocator with a richer language such that java.lang.Object is not
    //   relocated.
    RuntimeException compilationError =
        assertThrows(
            RuntimeException.class,
            () ->
                inspectAllClassesRelocated(
                    ToolHelper.R8_WITH_DEPS_JAR, output, originalPrefix, newPrefix + "."));
    assertThat(compilationError.getMessage(), containsString("must extend class java.lang.Object"));
  }

  @Test
  public void testRelocatorSomethingToEmpty() throws IOException {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    // TODO(b/155047618): Fixup the type after rewriting.
    CompilationFailedException compilationFailedException =
        assertThrows(
            CompilationFailedException.class,
            () -> {
              runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
            });
  }

  @Test
  public void testRelocateKeepsDebugInfo() throws IOException, CompilationFailedException {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "com.android.tools.r8";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    // Assert that all classes are the same, have the same methods and debug info:
    CodeInspector originalInspector = new CodeInspector(ToolHelper.R8_WITH_DEPS_JAR);
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
  public void testRelocateAll() throws IOException, CompilationFailedException, ExecutionException {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "foo.bar.baz";
    Map<String, String> mapping = new HashMap<>();
    mapping.put("some.package.that.does.not.exist", "foo");
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, output, originalPrefix, newPrefix);
  }

  @Test
  public void testOrderingOfPrefixes() throws Exception {
    String originalPrefix = "com.android";
    String newPrefix = "foo.bar.baz";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    mapping.put("com.android.tools.r8", "qux");
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () -> runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output));
    assertThat(
        exception.getCause().getMessage(),
        containsString("can be relocated by multiple mappings."));
  }

  @Test
  public void testNoReEntry() throws IOException, CompilationFailedException, ExecutionException {
    // TODO(b/154909222): Check if this is the behavior we would like.
    String originalPrefix = "com.android";
    String newPrefix = "foo.bar.baz";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    mapping.put(newPrefix, "qux");
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, output, originalPrefix, newPrefix);
    // Assert that no mappings of com.android.tools.r8 -> qux exists.
    CodeInspector inspector = new CodeInspector(output);
    assertFalse(
        inspector.allClasses().stream().anyMatch(clazz -> clazz.getFinalName().startsWith("qux")));
  }

  @Test
  public void testMultiplePackages()
      throws IOException, ExecutionException, CompilationFailedException {
    Set<String> seenPackages = new HashSet<>();
    List<Pair<String, String>> packageMappings = new ArrayList<>();
    Map<String, String> mapping = new LinkedHashMap<>();
    CodeInspector inspector = new CodeInspector(ToolHelper.R8_WITH_DEPS_JAR);
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
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    for (Pair<String, String> packageMapping : packageMappings) {
      inspectAllClassesRelocated(
          ToolHelper.R8_WITH_DEPS_JAR,
          output,
          packageMapping.getFirst(),
          packageMapping.getSecond());
    }
  }

  @Test
  public void testPartialPrefix()
      throws CompilationFailedException, IOException, ExecutionException {
    String originalPrefix = "com.android.tools.r";
    String newPrefix = "i_cannot_w";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, output, originalPrefix, originalPrefix);
  }

  @Test
  public void testBootstrap() throws IOException, CompilationFailedException, ExecutionException {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "relocated_r8";
    Map<String, String> mapping = new LinkedHashMap<>();
    mapping.put(originalPrefix, newPrefix);
    Path output = temp.newFile("output.jar").toPath();
    runRelocator(ToolHelper.R8_WITH_DEPS_JAR, mapping, output);
    // Check that all classes has been remapped.
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, output, originalPrefix, newPrefix);
    inspectAllSignaturesNotContainingString(output, originalPrefix);
    // We should be able to call the relocated relocator.
    Path bootstrapOutput = temp.newFile("bootstrap.jar").toPath();
    ProcessResult processResult =
        ToolHelper.runJava(
            output,
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
    // Assert that this is infact an identity transformation.
    inspectAllClassesRelocated(ToolHelper.R8_WITH_DEPS_JAR, bootstrapOutput, "", "");
  }

  @Test
  public void testNest() throws IOException, CompilationFailedException {
    String originalPrefix = "com.android.tools.r8";
    String newPrefix = "com.android.tools.r8";
    Path output = temp.newFile("output.jar").toPath();
    Map<String, String> mapping = new HashMap<>();
    mapping.put(originalPrefix, newPrefix);
    runRelocator(ToolHelper.R8_WITH_DEPS_17_JAR, mapping, output);
    // Assert that all classes are the same, have the same methods and nest info.
    CodeInspector originalInspector = new CodeInspector(ToolHelper.R8_WITH_DEPS_17_JAR);
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

  private void runRelocator(Path input, Map<String, String> mapping, Path output)
      throws CompilationFailedException {
    if (external) {
      List<String> args = new ArrayList<>();
      args.add("--input");
      args.add(input.toString());
      args.add("--output");
      args.add(output.toString());
      mapping.forEach(
          (key, value) -> {
            args.add("--map");
            args.add(key + "->" + value);
          });
      RelocatorCommandLine.run(args.toArray(new String[0]));
    } else {
      RelocatorCommand.Builder builder =
          RelocatorCommand.builder().addProgramFiles(input).setOutputPath(output);
      mapping.forEach(
          (key, value) ->
              builder.addPackageMapping(
                  Reference.packageFromString(key), Reference.packageFromString(value)));
      Relocator.run(builder.build());
    }
  }

  private void inspectAllClassesRelocated(
      Path original, Path relocated, String originalPrefix, String newPrefix) throws IOException {
    CodeInspector originalInspector = new CodeInspector(original);
    CodeInspector relocatedInspector = new CodeInspector(relocated);
    for (FoundClassSubject clazz : originalInspector.allClasses()) {
      if (originalPrefix.isEmpty()
          || clazz
              .getFinalName()
              .startsWith(originalPrefix + DescriptorUtils.JAVA_PACKAGE_SEPARATOR)) {
        String relocatedName = newPrefix + clazz.getFinalName().substring(originalPrefix.length());
        ClassSubject relocatedClass = relocatedInspector.clazz(relocatedName);
        assertThat(relocatedClass, isPresent());
      }
    }
  }

  private void inspectAllSignaturesNotContainingString(Path relocated, String originalPrefix)
      throws IOException {
    CodeInspector relocatedInspector = new CodeInspector(relocated);
    for (FoundClassSubject clazz : relocatedInspector.allClasses()) {
      assertThat(clazz.getFinalSignatureAttribute(), not(containsString(originalPrefix)));
      for (FoundMethodSubject method : clazz.allMethods()) {
        assertThat(method.getJvmMethodSignatureAsString(), not(containsString(originalPrefix)));
      }
      for (FoundFieldSubject field : clazz.allFields()) {
        assertThat(field.getJvmFieldSignatureAsString(), not(containsString(originalPrefix)));
      }
    }
  }
}
