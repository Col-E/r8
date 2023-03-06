// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.dex.Marker;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8ModeMarkerTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public R8ModeMarkerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  interface GetMarker {
    Marker getMarker();
  }

  static class ExtractDexMarkerConsumer extends DexIndexedConsumer.ForwardingConsumer
      implements GetMarker {
    private Marker marker;

    ExtractDexMarkerConsumer() {
      super(null);
    }

    @Override
    public void accept(
        int fileIndex, ByteDataView data, Set<String> descriptors, DiagnosticsHandler handler) {
      try {
        Collection<Marker> markers =
            ExtractMarker.extractMarkerFromDexProgramData(data.copyByteData());
        assertEquals(1, markers.size());
        marker = markers.iterator().next();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Marker getMarker() {
      return marker;
    }
  }

  static class ExtractClassFileMarkerConsumer extends ClassFileConsumer.ForwardingConsumer
      implements GetMarker {
    private Marker marker;

    ExtractClassFileMarkerConsumer() {
      super(null);
    }

    @Override
    public void accept(ByteDataView data, String descriptors, DiagnosticsHandler handler) {
      try {
        Collection<Marker> markers =
            ExtractMarker.extractMarkerFromClassProgramData(data.copyByteData());
        assertEquals(1, markers.size());
        marker = markers.iterator().next();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    public Marker getMarker() {
      return marker;
    }
  }

  @Test
  public void testFullMode() throws Exception {
    ProgramConsumer consumer =
        parameters.getBackend() == Backend.DEX
            ? new ExtractDexMarkerConsumer()
            : new ExtractClassFileMarkerConsumer();
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .setProgramConsumer(consumer)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    assertEquals("full", ((GetMarker) consumer).getMarker().getR8Mode());
  }

  @Test
  public void testCompatMode() throws Exception {
    ProgramConsumer consumer =
        parameters.getBackend() == Backend.DEX
            ? new ExtractDexMarkerConsumer()
            : new ExtractClassFileMarkerConsumer();
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .setProgramConsumer(consumer)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world");
    assertEquals("compatibility", ((GetMarker) consumer).getMarker().getR8Mode());
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.println("Hello, world");
    }
  }
}
