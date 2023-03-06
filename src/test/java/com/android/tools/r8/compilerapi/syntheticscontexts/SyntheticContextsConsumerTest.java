// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.syntheticscontexts;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexFilePerClassFileConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.SyntheticInfoConsumer;
import com.android.tools.r8.SyntheticInfoConsumerData;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.junit.Test;

public class SyntheticContextsConsumerTest extends CompilerApiTestRunner {

  public SyntheticContextsConsumerTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void test() throws Exception {
    // First compile to CF such that we have an input class that has a synthetic context.
    ClassReference backport = SyntheticItemsTestUtils.syntheticBackportClass(UsesBackport.class, 0);
    Map<String, byte[]> outputs = new HashMap<>();
    testForD8(Backend.CF)
        .addProgramClasses(UsesBackport.class)
        .setIntermediate(true)
        .setMinApi(1)
        .setProgramConsumer(
            new ClassFileConsumer() {

              @Override
              public void accept(ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                outputs.put(descriptor, data.copyByteData());
              }

              @Override
              public void finished(DiagnosticsHandler handler) {}
            })
        .compile()
        .writeToZip();
    // Run using the API test to obtain the backport context.
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            outputs.get(backport.getDescriptor()),
            context -> assertEquals(descriptor(UsesBackport.class), context));
  }

  public static class UsesBackport {
    public static void foo() {
      Boolean.compare(true, false);
    }
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(byte[] input, Consumer<String> syntheticContext) throws Exception {
      Map<String, String> synthetic2context = new ConcurrentHashMap<>();
      D8.run(
          D8Command.builder()
              .addClassProgramData(input, Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMinApiLevel(1)
              .setSyntheticInfoConsumer(
                  new SyntheticInfoConsumer() {
                    @Override
                    public void acceptSyntheticInfo(SyntheticInfoConsumerData data) {
                      synthetic2context.put(
                          data.getSyntheticClass().getDescriptor(),
                          data.getSynthesizingContextClass().getDescriptor());
                    }

                    @Override
                    public void finished() {
                      // nothing to finish up.
                    }
                  })
              .setProgramConsumer(
                  new DexFilePerClassFileConsumer() {

                    @Override
                    public void accept(
                        String primaryClassDescriptor,
                        ByteDataView data,
                        Set<String> descriptors,
                        DiagnosticsHandler handler) {
                      syntheticContext.accept(synthetic2context.get(primaryClassDescriptor));
                    }

                    @Override
                    public void finished(DiagnosticsHandler handler) {
                      // nothing to finish up.
                    }
                  })
              .build());
    }

    @Test
    public void test() throws Exception {
      byte[] input = getBytesForClass(getMockClass());
      run(
          input,
          context -> {
            if (context != null) {
              throw new RuntimeException("unexpected");
            }
          });
    }
  }
}
