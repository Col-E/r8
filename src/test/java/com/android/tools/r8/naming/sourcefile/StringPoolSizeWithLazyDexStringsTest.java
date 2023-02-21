// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.sourcefile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.DexSegments;
import com.android.tools.r8.DexSegments.Command;
import com.android.tools.r8.DexSegments.SegmentInfo;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringPoolSizeWithLazyDexStringsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  public StringPoolSizeWithLazyDexStringsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(StringPoolSizeWithLazyDexStringsTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        // Ensure we have a source file that depends on the hash.
        .apply(
            b ->
                b.getBuilder()
                    .setSourceFileProvider(
                        new SourceFileProvider() {
                          @Override
                          public String get(SourceFileEnvironment environment) {
                            return environment.getMapHash();
                          }
                        }))
        .compile()
        // Ensure we are computing a mapping file.
        .apply(r -> assertFalse(r.getProguardMap().isEmpty()))
        .apply(this::checkStringSegmentSize)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("");
  }

  private void checkStringSegmentSize(R8TestCompileResult result) throws Exception {
    Map<Integer, SegmentInfo> segments =
        DexSegments.run(Command.builder().addProgramFiles(result.writeToZip()).build());
    SegmentInfo stringInfo = segments.get(Constants.TYPE_STRING_ID_ITEM);
    assertEquals(8, stringInfo.getItemCount());
  }

  static class TestClass {

    public static void main(String[] args) {
      // Empty program to reduce string count.
    }
  }
}
