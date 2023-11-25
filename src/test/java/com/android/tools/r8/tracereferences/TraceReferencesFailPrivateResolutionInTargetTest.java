// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TraceReferencesFailPrivateResolutionInTargetTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TraceReferencesFailPrivateResolutionInTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  static class SeenReferencesConsumer implements TraceReferencesConsumer {

    private Set<MethodReference> seenMethods = new HashSet<>();

    @Override
    public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {}

    @Override
    public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {}

    @Override
    public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
      seenMethods.add(tracedMethod.getReference());
    }
  }

  @Test
  public void testInvalidResolution() throws Exception {
    Path dir = temp.newFolder().toPath();
    Path targetJar =
        ZipBuilder.builder(dir.resolve("target.jar"))
            .addFilesRelative(
                ToolHelper.getClassPathForTests(),
                ToolHelper.getClassFileForTestClass(I.class),
                ToolHelper.getClassFileForTestClass(A.class),
                ToolHelper.getClassFileForTestClass(B.class))
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
            .addSourceFiles(sourceJar)
            .addTargetFiles(targetJar)
            .setConsumer(consumer)
            .build());
    ImmutableSet<MethodReference> expectedSet =
        ImmutableSet.of(
            Reference.method(Reference.classFromClass(A.class), "f", Collections.emptyList(), null),
            Reference.method(
                Reference.classFromClass(B.class), "<init>", Collections.emptyList(), null));
    assertEquals(expectedSet, consumer.seenMethods);
  }

  public interface I {
    default void f() {
      System.out.println("I::f");
    }
  }

  public static class A {
    private static void f() {
      System.out.println("A::f");
    }
  }

  public static class B extends A implements I {}

  public static class Main {

    public static void main(String[] args) {
      new B().f();
    }
  }
}
