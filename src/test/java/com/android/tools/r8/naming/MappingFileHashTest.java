// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.ProguardMapChecker.VerifyMappingFileHashResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MappingFileHashTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public MappingFileHashTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testUnixLineEnd() throws Exception {
    String newline = "\n";
    // Hash as given by sha256 command on the content using unix-style newlines.
    String hash = "539a6b5614c65c7473a995507163a4559cea3229d3cada164a2ca4935bbbd597";
    verifyHash(newline, hash);
  }

  @Test
  public void testWindowsLineEnd() throws Exception {
    // R8 will only use \n in mapping files, but if a file is written using dos-style newlines,
    // those newlines will be part of the content hashing. This is a regression test that the file
    // will compute the right hash in such cases.
    String newline = "\r\n";
    // Hash as given by sha256 command on the content using dos-style newlines.
    String hash = "6ef47f7b9b6d5013628073ebdadad27dd2e7e57ef911e9d6cbee0f92c434e48a";
    verifyHash(newline, hash);
  }

  private void verifyHash(String newline, String hash) {
    // Some header info. This should never affect the hash.
    String header =
        String.join(
            newline,
            "# compiler: R8",
            "", // Empty header line.
            "# junk header info: " + System.nanoTime(),
            "# pg_map_hash: SHA-256 " + hash);
    // Actually hashed content (modulo newlines).
    String hashed =
        String.join(
            newline,
            "triviæl.Triviål -> a.œ:",
            "    void main(java.lang.String[]) -> a",
            "" // Always end with newline.
            );
    String content = String.join(newline, header, hashed);
    VerifyMappingFileHashResult result = ProguardMapChecker.validateProguardMapHash(content);
    assertTrue(result.isOk());
  }
}
