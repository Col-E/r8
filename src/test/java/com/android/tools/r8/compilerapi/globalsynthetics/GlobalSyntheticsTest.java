// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.globalsynthetics;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.GlobalSyntheticsConsumer;
import com.android.tools.r8.GlobalSyntheticsResourceProvider;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class GlobalSyntheticsTest extends CompilerApiTestRunner {

  public GlobalSyntheticsTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testGlobalSynthetics() throws Exception {
    new ApiTest(ApiTest.PARAMETERS).run();
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run() throws Exception {
      GlobalSyntheticsResourceProvider provider =
          new GlobalSyntheticsResourceProvider() {
            @Override
            public Origin getOrigin() {
              return Origin.unknown();
            }

            @Override
            public InputStream getByteStream() {
              throw new IllegalStateException();
            }
          };
      List<GlobalSyntheticsResourceProvider> providers = new ArrayList<>();
      // Don't actually add the provider as we don't have any bytes to return.
      if (false) {
        providers.add(provider);
      }
      D8.run(
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .setIntermediate(true)
              .addGlobalSyntheticsFiles()
              .addGlobalSyntheticsFiles(new ArrayList<>())
              .addGlobalSyntheticsResourceProviders()
              .addGlobalSyntheticsResourceProviders(providers)
              .setGlobalSyntheticsConsumer(
                  new GlobalSyntheticsConsumer() {
                    @Override
                    public void accept(
                        ByteDataView data, ClassReference context, DiagnosticsHandler handler) {
                      // Nothing is actually received here as MockClass does not give rise to
                      // globals.
                    }

                    @Override
                    public void finished(DiagnosticsHandler handler) {
                      // Nothing to do, just checking we can override finished.
                    }
                  })
              .build());
    }

    @Test
    public void testGlobalSynthetics() throws Exception {
      run();
    }
  }
}
