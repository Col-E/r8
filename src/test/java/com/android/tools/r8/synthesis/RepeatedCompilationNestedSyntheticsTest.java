// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticBackportClass;
import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticLambdaClass;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ClassFileConsumerData;
import com.android.tools.r8.DesugarGraphConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumerData;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanBox;
import com.google.common.collect.ImmutableSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepeatedCompilationNestedSyntheticsTest extends TestBase {

  private final TestParameters parameters;
  private final Backend intermediateBackend;

  @Parameterized.Parameters(name = "{0}, intermediate: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build(),
        Backend.values());
  }

  public RepeatedCompilationNestedSyntheticsTest(
      TestParameters parameters, Backend intermediateBackend) {
    this.parameters = parameters;
    this.intermediateBackend = intermediateBackend;
  }

  @Test
  public void test() throws Exception {
    assertEquals(Backend.DEX, parameters.getBackend());

    ClassReference syntheticLambdaClass = syntheticLambdaClass(UsesBackport.class, 0);
    ImmutableSet<String> expectedClassOutputs =
        ImmutableSet.of(descriptor(UsesBackport.class), syntheticLambdaClass.getDescriptor());

    Map<String, byte[]> firstCompilation = new HashMap<>();
    testForD8(Backend.CF)
        // High API level such that only the lambda is desugared.
        .setMinApi(AndroidApiLevel.S)
        .setIntermediate(true)
        .addClasspathClasses(I.class)
        .addProgramClasses(UsesBackport.class)
        .setProgramConsumer(
            new ClassFileConsumer() {
              @Override
              public void acceptClassFile(ClassFileConsumerData data) {
                firstCompilation.put(data.getClassDescriptor(), data.getByteDataCopy());
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            })
        .compile();
    assertEquals(expectedClassOutputs, firstCompilation.keySet());

    Map<String, byte[]> secondCompilation = new HashMap<>();
    ImmutableSet.Builder<String> allDescriptors = ImmutableSet.builder();
    BooleanBox matched = new BooleanBox(false);
    for (Entry<String, byte[]> entry : firstCompilation.entrySet()) {
      byte[] bytes = entry.getValue();
      Origin origin =
          new Origin(Origin.root()) {
            @Override
            public String part() {
              return entry.getKey();
            }
          };
      testForD8(intermediateBackend)
          .setMinApi(parameters)
          .setIntermediate(true)
          .addClasspathClasses(I.class)
          .apply(b -> b.getBuilder().addClassProgramData(bytes, origin))
          .apply(
              b ->
                  b.getBuilder()
                      .setDesugarGraphConsumer(
                          new DesugarGraphConsumer() {

                            @Override
                            public void accept(Origin dependent, Origin dependency) {
                              assertThat(
                                  dependency.toString(), containsString(binaryName(I.class)));
                              assertThat(
                                  dependent.toString(),
                                  containsString(syntheticLambdaClass.getBinaryName()));
                              matched.set(true);
                            }

                            @Override
                            public void finished() {}
                          }))
          .applyIf(
              intermediateBackend == Backend.CF,
              b ->
                  b.setProgramConsumer(
                      new ClassFileConsumer() {
                        @Override
                        public void acceptClassFile(ClassFileConsumerData data) {
                          secondCompilation.put(data.getClassDescriptor(), data.getByteDataCopy());
                          allDescriptors.add(data.getClassDescriptor());
                        }

                        @Override
                        public void finished(DiagnosticsHandler handler) {}
                      }),
              b ->
                  b.setProgramConsumer(
                      new DexFilePerClassFileConsumer() {
                        @Override
                        public synchronized void acceptDexFile(
                            DexFilePerClassFileConsumerData data) {
                          secondCompilation.put(
                              data.getPrimaryClassDescriptor(), data.getByteDataCopy());
                          allDescriptors.addAll(data.getClassDescriptors());
                        }

                        @Override
                        public void finished(DiagnosticsHandler handler) {}
                      }))
          .compile();
    }
    assertTrue(matched.get());
    // The dex file per class file output should maintain the exact same set of primary descriptors.
    if (intermediateBackend == Backend.DEX) {
      assertEquals(expectedClassOutputs, secondCompilation.keySet());
    }
    // The total set of classes should also include the backport. The backport should be
    // hygienically placed under the synthetic lambda (not the context of the lambda!).
    assertEquals(
        ImmutableSet.<String>builder()
            .addAll(expectedClassOutputs)
            .add(syntheticBackportClass(syntheticLambdaClass, 0).getDescriptor())
            .build(),
        allDescriptors.build());

    testForD8(Backend.DEX)
        .setMinApi(parameters)
        .addProgramClasses(I.class, TestClass.class)
        .applyIf(
            intermediateBackend == Backend.CF,
            b -> b.addProgramClassFileData(secondCompilation.values()),
            b -> b.addProgramDexFileData(secondCompilation.values()))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("1")
        .inspect(
            inspector -> {
              Set<String> descriptors =
                  inspector.allClasses().stream()
                      .map(c -> c.getFinalReference().getDescriptor())
                      .collect(Collectors.toSet());
              assertEquals(
                  ImmutableSet.of(
                      descriptor(I.class),
                      descriptor(TestClass.class),
                      descriptor(UsesBackport.class),
                      // The merge step will reestablish the original contexts, thus both the lambda
                      // and the backport are placed under the non-synthetic input class
                      // UsesBackport.
                      syntheticBackportClass(UsesBackport.class, 0).getDescriptor(),
                      syntheticLambdaClass(UsesBackport.class, 1).getDescriptor()),
                  descriptors);
            });
  }

  interface I {
    int compare(boolean b1, boolean b2);
  }

  static class UsesBackport {
    public static I foo() {
      return Boolean::compare;
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(UsesBackport.foo().compare(true, false));
    }
  }
}
