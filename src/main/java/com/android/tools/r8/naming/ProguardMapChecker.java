// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.ProguardMapMarkerInfo.MARKER_KEY_PG_MAP_HASH;
import static com.android.tools.r8.naming.ProguardMapMarkerInfo.SHA_256_KEY;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProguardMapChecker implements StringConsumer {

  private final StringConsumer inner;
  private final StringBuilder contents = new StringBuilder();

  ProguardMapChecker(StringConsumer inner) {
    if (!InternalOptions.assertionsEnabled()) {
      // Make sure we never get here without assertions enabled.
      throw new Unreachable();
    }
    this.inner = inner;
  }

  @Override
  public void accept(String string, DiagnosticsHandler handler) {
    inner.accept(string, handler);
    contents.append(string);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    inner.finished(handler);
    String stringContent = contents.toString();
    assert validateProguardMapParses(stringContent);
    assert validateProguardMapHash(stringContent).isOk();
  }

  private static boolean validateProguardMapParses(String content) {
    try {
      ClassNameMapper.mapperFromString(content);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public static class VerifyMappingFileHashResult {

    private final boolean error;
    private final String message;

    public static VerifyMappingFileHashResult createOk() {
      return new VerifyMappingFileHashResult(false, null);
    }

    public static VerifyMappingFileHashResult createInfo(String message) {
      return new VerifyMappingFileHashResult(false, message);
    }

    public static VerifyMappingFileHashResult createError(String message) {
      return new VerifyMappingFileHashResult(true, message);
    }

    private VerifyMappingFileHashResult(boolean error, String message) {
      this.error = error;
      this.message = message;
    }

    public boolean isOk() {
      return !error && message == null;
    }

    public boolean isError() {
      return error;
    }

    public String getMessage() {
      assert message != null;
      return message;
    }
  }

  public static VerifyMappingFileHashResult validateProguardMapHash(String content) {
    int lineEnd = -1;
    while (true) {
      int lineStart = lineEnd + 1;
      lineEnd = content.indexOf('\n', lineStart);
      if (lineEnd < 0) {
        return VerifyMappingFileHashResult.createInfo("Failure to find map hash");
      }
      String line = content.substring(lineStart, lineEnd).trim();
      if (line.isEmpty()) {
        // Ignore empty lines in the header.
        continue;
      }
      if (line.charAt(0) != '#') {
        // At the first non-empty non-comment line we assume that the file has no hash marker.
        return VerifyMappingFileHashResult.createInfo("Failure to find map hash in header");
      }
      String headerLine = line.substring(1).trim();
      if (headerLine.startsWith(MARKER_KEY_PG_MAP_HASH)) {
        int shaIndex = headerLine.indexOf(SHA_256_KEY + " ", MARKER_KEY_PG_MAP_HASH.length());
        if (shaIndex < 0) {
          return VerifyMappingFileHashResult.createError(
              "Unknown map hash function: '" + headerLine + "'");
        }
        String headerHash = headerLine.substring(shaIndex + SHA_256_KEY.length()).trim();
        // We are on the hash line. Everything past this line is the hashed content.
        Hasher hasher = Hashing.sha256().newHasher();
        String hashedContent = content.substring(lineEnd + 1);
        hasher.putString(hashedContent, StandardCharsets.UTF_8);
        String computedHash = hasher.hash().toString();
        return headerHash.equals(computedHash)
            ? VerifyMappingFileHashResult.createOk()
            : VerifyMappingFileHashResult.createError(
                "Mismatching map hash: '" + headerHash + "' != '" + computedHash + "'");
      }
    }
  }
}
