// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceFieldResolutionWithLibraryAndProgramClassTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  static class SeenReferencesConsumer implements TraceReferencesConsumer {

    private final Set<FieldReference> seenFields = new HashSet<>();
    private final Set<FieldReference> seenMissingFields = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      if (tracedField.isMissingDefinition()) {
        seenMissingFields.add(tracedField.getReference());
      } else {
        seenFields.add(tracedField.getReference());
      }
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {}
  }

  @Test
  public void testInvalidResolution() throws Exception {
    Path dir = temp.newFolder().toPath();
    Path libJar =
        ZipBuilder.builder(dir.resolve("lib.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(A.class))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(B.class),
                transformer(B.class).removeFields(FieldPredicate.all()).transform())
            .build();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addBytes(
                DescriptorUtils.getPathFromJavaType(A.class),
                transformer(A.class).removeFields(FieldPredicate.all()).transform())
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(B.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(Main.class))
            .build();
    SeenReferencesConsumer consumer = new SeenReferencesConsumer();
    TraceReferences.run(
        TraceReferencesCommand.builder()
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addLibraryFiles(libJar)
            .addTargetFiles(targetJar)
            .addSourceFiles(sourceJar)
            .setConsumer(consumer)
            .build());
    ImmutableSet<FieldReference> expectedSet =
        ImmutableSet.of(
            Reference.field(
                Reference.classFromClass(A.class), "foo", Reference.primitiveFromDescriptor("I")),
            Reference.field(
                Reference.classFromClass(B.class), "bar", Reference.primitiveFromDescriptor("I")));
    assertEquals(Collections.emptySet(), consumer.seenMissingFields);
    assertEquals(expectedSet, consumer.seenFields);
  }

  // A is added to both library and program, but the program one is missing the field foo.
  public static class A {

    public static int foo = 42;
  }

  // B is added to both library and program, but the library one is missing the field bar.
  public static class B {

    public static int bar = 42;
  }

  public static class Main {

    public static void main(String[] args) {
      int value = A.foo + B.bar;
    }
  }
}
