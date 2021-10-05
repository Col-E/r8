// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.mapid;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.MapIdEnvironment;
import com.android.tools.r8.MarkerMatcher;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Function;
import org.junit.Test;

public class CustomMapIdTest extends CompilerApiTestRunner {

  public CustomMapIdTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testDefaultMapId() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runDefaultMapId, hash -> hash.substring(0, 7));
  }

  @Test
  public void testCustomMapId() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runCustomMapId, hash -> hash);
  }

  private String getMapHash(String mapping) {
    String lineHeader = "# pg_map_hash: SHA-256 ";
    int i = mapping.indexOf(lineHeader);
    assertTrue(i >= 0);
    int start = i + lineHeader.length();
    int end = mapping.indexOf('\n', start);
    return mapping.substring(start, end);
  }

  private void runTest(
      ThrowingBiConsumer<ProgramConsumer, StringConsumer, Exception> test,
      Function<String, String> hashToId)
      throws Exception {
    Path output = temp.newFolder().toPath().resolve("out.jar");
    StringBuilder mappingBuilder = new StringBuilder();
    BooleanBox didGetMappingContent = new BooleanBox(false);
    test.accept(
        new DexIndexedConsumer.ArchiveConsumer(output),
        (mappingContent, handler) -> {
          mappingBuilder.append(mappingContent);
          didGetMappingContent.set(true);
        });
    assertTrue(didGetMappingContent.get());

    // Extract the map hash from the file. This is always set by R8 to a SHA 256 hash.
    String mappingContent = mappingBuilder.toString();
    String mapHash = getMapHash(mappingContent);
    assertEquals(64, mapHash.length());

    // Check the map id is also defined in the map file.
    String mapId = hashToId.apply(mapHash);
    assertThat(mappingContent, containsString("pg_map_id: " + mapId + "\n"));

    // Check that the map id is also present in the markers.
    CodeInspector inspector = new CodeInspector(output);
    Collection<Marker> markers = inspector.getMarkers();
    MarkerMatcher.assertMarkersMatch(markers, MarkerMatcher.markerPgMapId(equalTo(mapId)));
    assertEquals(1, markers.size());
  }

  public static class ApiTest extends CompilerApiTest {

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runDefaultMapId(ProgramConsumer programConsumer, StringConsumer mappingConsumer)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setProgramConsumer(programConsumer)
              .setProguardMapConsumer(mappingConsumer)
              .build());
    }

    public void runCustomMapId(ProgramConsumer programConsumer, StringConsumer mappingConsumer)
        throws Exception {
      R8.run(
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(getKeepMainRules(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMapIdProvider(MapIdEnvironment::getMapHash)
              .setProgramConsumer(programConsumer)
              .setProguardMapConsumer(mappingConsumer)
              .build());
    }

    @Test
    public void testDefaultMapId() throws Exception {
      runDefaultMapId(DexIndexedConsumer.emptyConsumer(), StringConsumer.emptyConsumer());
    }

    @Test
    public void testCustomMapId() throws Exception {
      runCustomMapId(DexIndexedConsumer.emptyConsumer(), StringConsumer.emptyConsumer());
    }
  }
}
