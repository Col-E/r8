// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsChecker;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferenceStaticFieldIndirectionTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferenceStaticFieldIndirectionTest(TestParameters parameters) {}

  static class MissingReferencesConsumer implements TraceReferencesConsumer {

    private Set<FieldReference> seenFields = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
      seenFields.add(tracedField.getReference());
    }

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {}
  }

  @Test
  public void traceStaticFields() throws Throwable {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(SuperClass.class),
                ToolHelper.getClassFileForTestClass(SubClass.class))
            .build();
    Path sourceJar =
        ZipBuilder.builder(dir.resolve("source.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(), ToolHelper.getClassFileForTestClass(Main.class))
            .build();
    DiagnosticsChecker diagnosticsChecker = new DiagnosticsChecker();
    MissingReferencesConsumer consumer = new MissingReferencesConsumer();

    TraceReferences.run(
        TraceReferencesCommand.builder(diagnosticsChecker)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());

    ImmutableSet<FieldReference> expectedSet =
        ImmutableSet.of(
            Reference.field(
                Reference.classFromClass(SuperClass.class),
                "field1",
                Reference.typeFromTypeName("int")),
            Reference.field(
                Reference.classFromClass(SubClass.class),
                "field2",
                Reference.typeFromTypeName("int")));
    assertEquals(expectedSet, consumer.seenFields);
  }

  static class SuperClass {

    static int field1 = 1;
  }

  static class SubClass extends SuperClass {

    static int field2 = 2;
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(SubClass.field1);
      System.out.println(SubClass.field2);
    }
  }
}
