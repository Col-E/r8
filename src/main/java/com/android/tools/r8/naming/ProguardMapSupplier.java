// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.Version;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.VersionProperties;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;

public class ProguardMapSupplier {

  public static final String MARKER_KEY_COMPILER = "compiler";
  public static final String MARKER_VALUE_COMPILER = "R8";
  public static final String MARKER_KEY_COMPILER_VERSION = "compiler_version";
  public static final String MARKER_KEY_COMPILER_HASH = "compiler_hash";
  public static final String MARKER_KEY_MIN_API = "min_api";
  public static final String MARKER_KEY_PG_MAP_ID = "pg_map_id";

  public static int PG_MAP_ID_LENGTH = 7;

  // Truncated murmur hash of the non-whitespace codepoints of the Proguard map (excluding the
  // marker).
  public static class ProguardMapId extends Box<String> {
    private ProguardMapId(String id) {
      super(id);
      assert id != null;
      assert id.length() == PG_MAP_ID_LENGTH;
    }
  }

  private final ClassNameMapper classNameMapper;
  private final InternalOptions options;

  public ProguardMapSupplier(ClassNameMapper classNameMapper, InternalOptions options) {
    this.classNameMapper = classNameMapper;
    this.options = options;
  }

  public static ProguardMapSupplier create(
      ClassNameMapper classNameMapper, InternalOptions options) {
    return classNameMapper.isEmpty() ? null : new ProguardMapSupplier(classNameMapper, options);
  }

  public ProguardMapId writeProguardMap() {
    String body = classNameMapper.toString();
    assert body != null;
    assert !body.trim().isEmpty();
    ProguardMapId id = computeProguardMapId(body);
    StringBuilder builder = new StringBuilder();
    writeMarker(builder, id);
    writeBody(builder, body);
    String proguardMapContent = builder.toString();
    assert validateProguardMapParses(proguardMapContent);
    ExceptionUtils.withConsumeResourceHandler(
        options.reporter, options.proguardMapConsumer, proguardMapContent);
    ExceptionUtils.withFinishedResourceHandler(options.reporter, options.proguardMapConsumer);
    return id;
  }

  private ProguardMapId computeProguardMapId(String body) {
    Hasher hasher = Hashing.murmur3_32().newHasher();
    body.codePoints().filter(c -> !Character.isWhitespace(c)).forEach(hasher::putInt);
    return new ProguardMapId(hasher.hash().toString().substring(0, PG_MAP_ID_LENGTH));
  }

  private void writeBody(StringBuilder builder, String body) {
    builder.append(body);
  }

  private void writeMarker(StringBuilder builder, ProguardMapId id) {
    builder.append(
        "# "
            + MARKER_KEY_COMPILER
            + ": "
            + MARKER_VALUE_COMPILER
            + "\n"
            + "# "
            + MARKER_KEY_COMPILER_VERSION
            + ": "
            + Version.LABEL
            + "\n");
    if (options.isGeneratingDex()) {
      builder.append("# " + MARKER_KEY_MIN_API + ": " + options.minApiLevel + "\n");
    }
    if (Version.isDevelopmentVersion()) {
      builder.append(
          "# " + MARKER_KEY_COMPILER_HASH + ": " + VersionProperties.INSTANCE.getSha() + "\n");
    }
    builder.append("# " + MARKER_KEY_PG_MAP_ID + ": " + id.get() + "\n");
    // Turn off linting of the mapping file in some build systems.
    builder.append("# common_typos_disable" + "\n");
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
}
