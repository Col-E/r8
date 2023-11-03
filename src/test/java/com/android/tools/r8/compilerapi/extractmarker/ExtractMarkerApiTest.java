// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.extractmarker;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.ExtractMarkerCommand;
import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.MarkerInfoConsumer;
import com.android.tools.r8.MarkerInfoConsumerData;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IntBox;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.Test;

public class ExtractMarkerApiTest extends CompilerApiTestRunner {

  public ExtractMarkerApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  public static class SomeClass {}

  public static class SomeOrigin extends Origin {
    private final String name;

    public SomeOrigin(String name) {
      super(Origin.root());
      this.name = name;
    }

    @Override
    public String part() {
      return name;
    }
  }

  @Test
  public void test() throws Exception {

    Path inputFile = ToolHelper.getClassFileForTestClass(SomeClass.class);
    byte[] inputCf = ToolHelper.getClassAsBytes(SomeClass.class);
    Box<byte[]> inputDex = new Box<>();

    // Compile DEX code for use as byte input.
    testForD8(Backend.DEX)
        .addProgramClasses(SomeClass.class)
        .setMinApi(1)
        .setProgramConsumer(
            new DexIndexedConsumer() {
              byte[] bytes = null;

              @Override
              public void accept(
                  int fileIndex,
                  ByteDataView data,
                  Set<String> descriptors,
                  DiagnosticsHandler handler) {
                assertEquals(0, fileIndex);
                assertNull(bytes);
                bytes = data.copyByteData();
              }

              @Override
              public void finished(DiagnosticsHandler handler) {
                assertNotNull(bytes);
                inputDex.set(bytes);
              }
            })
        .compile();

    assertNotNull(inputDex.get());

    Origin originDex = new SomeOrigin("dex bytes");
    Origin originCf = new SomeOrigin("cf bytes");
    IntBox calls = new IntBox(0);
    new ApiTest(ApiTest.PARAMETERS)
        .run(
            inputFile,
            inputDex.get(),
            originDex,
            inputCf,
            originCf,
            (origin, marker) -> {
              calls.increment();
              if (origin == originDex) {
                assertEquals("D8", marker.getTool());
                assertTrue(marker.isD8());
                assertFalse(marker.isR8());
                assertFalse(marker.isL8());
                assertEquals(1, marker.getMinApi());
                assertThat(marker.getRawEncoding(), startsWith("~~D8{"));
              } else {
                assertTrue(origin == originCf || origin.equals(new PathOrigin(inputFile)));
                // The CF input file and bytes have no marker as they are javac generated.
                assertNull(marker);
              }
            });
    assertEquals(4, calls.get());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void run(
        Path inputFile,
        byte[] inputDex,
        Origin dexOrigin,
        byte[] inputCf,
        Origin cfOrigin,
        BiConsumer<Origin, MarkerInfo> consumer)
        throws Exception {
      ExtractMarkerCommand.Builder builder = ExtractMarkerCommand.builder();
      if (inputFile != null) {
        builder.addProgramFiles(inputFile).addProgramFiles(Collections.singleton(inputFile));
      }
      if (inputDex != null) {
        builder.addDexProgramData(inputDex, dexOrigin);
      }
      if (inputCf != null) {
        builder.addClassProgramData(inputCf, cfOrigin);
      }
      builder.setMarkerInfoConsumer(
          new MarkerInfoConsumer() {
            @Override
            public void acceptMarkerInfo(MarkerInfoConsumerData data) {
              if (data.hasMarkers()) {
                data.getMarkers()
                    .forEach(
                        marker -> {
                          consumer.accept(data.getInputOrigin(), marker);
                        });
              } else {
                consumer.accept(data.getInputOrigin(), null);
              }
            }

            @Override
            public void finished() {}
          });
      ExtractMarker.run(builder.build());
    }

    @Test
    public void test() throws Exception {
      byte[] inputCf = getBytesForClass(getMockClass());
      run(
          null,
          null,
          null,
          inputCf,
          Origin.root(),
          (origin, marker) -> {
            // Marker is null here but use all currently defined methods on marker info to force
            // binary compatible usage.
            if (marker != null) {
              String tool = marker.getTool();
              boolean d8 = marker.isD8();
              boolean r8 = marker.isR8();
              boolean l8 = marker.isL8();
              int minApi = marker.getMinApi();
              String raw = marker.getRawEncoding();
            }
          });
    }
  }
}
