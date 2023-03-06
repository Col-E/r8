// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.syntheticBackportClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepeatedCompilationSyntheticsTest extends TestBase {

  private final String EXPECTED = StringUtils.lines("-2", "254");

  private final TestParameters parameters;
  private final Backend intermediateBackend;

  private final AndroidApiLevel API_WITH_BYTE_COMPARE = AndroidApiLevel.K;

  @Parameterized.Parameters(name = "{0}, intermediate: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withMinimumApiLevel().build(),
        Backend.values());
  }

  public RepeatedCompilationSyntheticsTest(TestParameters parameters, Backend intermediateBackend) {
    this.parameters = parameters;
    this.intermediateBackend = intermediateBackend;
  }

  @Test
  public void testReference() throws Exception {
    testForD8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addProgramClassFileData(getTransformedUsesBackport())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void test() throws Exception {
    assertEquals(Backend.DEX, parameters.getBackend());

    Map<String, byte[]> firstCompilation = new HashMap<>();
    testForD8(Backend.CF)
        // High API level such that only the compareUnsigned is desugared.
        .setMinApi(API_WITH_BYTE_COMPARE)
        .setIntermediate(true)
        .addProgramClassFileData(getTransformedUsesBackport())
        .setProgramConsumer(
            new ClassFileConsumer() {
              @Override
              public synchronized void accept(
                  ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                byte[] old = firstCompilation.put(descriptor, data.copyByteData());
                assertNull("Duplicate " + descriptor, old);
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            })
        .compile();
    assertEquals(
        ImmutableSet.of(
            descriptor(UsesBackport.class),
            syntheticBackportClass(UsesBackport.class, 0).getDescriptor()),
        firstCompilation.keySet());

    List<String> secondCompilation = new ArrayList<>();
    for (Entry<String, byte[]> entry : firstCompilation.entrySet()) {
      byte[] bytes = entry.getValue();
      testForD8(intermediateBackend)
          .setMinApi(parameters)
          .setIntermediate(true)
          .addProgramClassFileData(bytes)
          .applyIf(
              intermediateBackend == Backend.CF,
              b ->
                  b.setProgramConsumer(
                      new ClassFileConsumer() {
                        @Override
                        public synchronized void accept(
                            ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                          secondCompilation.add(descriptor);
                        }

                        @Override
                        public void finished(DiagnosticsHandler handler) {}
                      }),
              b ->
                  b.setProgramConsumer(
                      new DexFilePerClassFileConsumer() {
                        @Override
                        public void accept(
                            String primaryClassDescriptor,
                            ByteDataView data,
                            Set<String> descriptors,
                            DiagnosticsHandler handler) {
                          secondCompilation.addAll(descriptors);
                        }

                        @Override
                        public void finished(DiagnosticsHandler handler) {}
                      }))
          .compile();
    }

    // TODO(b/271235788): The repeated compilation is unsound and we have duplicate definitions of
    //  the backports both using the same type name.
    secondCompilation.sort(String::compareTo);
    assertEquals(
        ImmutableList.of(
            syntheticBackportClass(UsesBackport.class, 0).getDescriptor(),
            syntheticBackportClass(UsesBackport.class, 0).getDescriptor(),
            descriptor(UsesBackport.class)),
        secondCompilation);
  }

  private byte[] getTransformedUsesBackport() throws Exception {
    return transformer(UsesBackport.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals("compare", name);
              visitor.visitMethodInsn(opcode, owner, "compareUnsigned", descriptor, isInterface);
            })
        .transform();
  }

  static class UsesBackport {
    public static int foo(byte[] bs) {
      return Byte.compare(bs[0], bs[1]);
    }

    public static int bar(byte[] bs) {
      return Byte.compare /*Unsigned*/(bs[0], bs[1]);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(UsesBackport.foo(new byte[] {-1, 1}));
      System.out.println(UsesBackport.bar(new byte[] {-1, 1}));
    }
  }
}
