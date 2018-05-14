// Copyright (c) 2017, the Rex project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Simple test that compiles framework.jar with D8 a number of times with various number of threads
 * available to the compiler. This test also tests the hidden marker inserted into classes.dex.
 */
@RunWith(Parameterized.class)
public class D8FrameworkDexPassthroughMarkerTest {

  private static final Path FRAMEWORK_JAR =
      Paths.get("tools/linux/art-5.1.1/product/mako/system/framework/framework.jar");

  @Rule
  public TemporaryFolder output = ToolHelper.getTemporaryFolderForTest();

  @Parameters(name = "Min api = {0}")
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][]{
        {AndroidApiLevel.N.getLevel()},
        {AndroidApiLevel.O.getLevel()},
        {AndroidApiLevel.O_MR1.getLevel()}
    });
  }

  private final int minApi;

  public D8FrameworkDexPassthroughMarkerTest(int minApi) {
    this.minApi = minApi;
  }

  @Test
  public void compile() throws Exception {
    D8Command.Builder command =
        D8Command.builder().setMinApiLevel(minApi).addProgramFiles(FRAMEWORK_JAR);
    Marker marker = new Marker(Tool.D8)
        .setVersion("1.0.0")
        .setMinApi(minApi);
    InternalOptions options = new InternalOptions();
    DexString markerDexString = options.itemFactory.createString(marker.toString());
    Marker selfie = Marker.parse(markerDexString);
    assert marker.equals(selfie);
    AndroidApp app = ToolHelper.runD8(command, opts -> opts.setMarker(marker));
    DexApplication dexApp =
        new ApplicationReader(
                app, options, new Timing("D8FrameworkDexPassthroughMarkerTest"))
            .read();
    Marker readMarker = dexApp.dexItemFactory.extractMarker();
    assertEquals(marker, readMarker);
  }
}
