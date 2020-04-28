// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
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
  private final StringConsumer consumer;
  private final InternalOptions options;
  private final Reporter reporter;

  private ProguardMapSupplier(ClassNameMapper classNameMapper, InternalOptions options) {
    assert classNameMapper != null;
    assert !classNameMapper.isEmpty();
    this.classNameMapper = classNameMapper.sorted();
    this.consumer =
        InternalOptions.assertionsEnabled()
            ? new ProguardMapChecker(options.proguardMapConsumer)
            : options.proguardMapConsumer;
    this.options = options;
    this.reporter = options.reporter;
  }

  public static ProguardMapSupplier create(
      ClassNameMapper classNameMapper, InternalOptions options) {
    return classNameMapper.isEmpty() ? null : new ProguardMapSupplier(classNameMapper, options);
  }

  public ProguardMapId writeProguardMap() {
    ProguardMapId id = computeProguardMapId();
    writeMarker(id);
    writeBody();
    ExceptionUtils.withFinishedResourceHandler(reporter, consumer);
    return id;
  }

  private ProguardMapId computeProguardMapId() {
    ProguardMapIdBuilder builder = new ProguardMapIdBuilder();
    classNameMapper.write(builder);
    return builder.build();
  }

  private void writeBody() {
    classNameMapper.write(new ProguardMapWriter());
  }

  private void writeMarker(ProguardMapId id) {
    StringBuilder builder = new StringBuilder();
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
    consumer.accept(builder.toString(), reporter);
  }

  static class ProguardMapIdBuilder implements ChainableStringConsumer {

    private final Hasher hasher = Hashing.murmur3_32().newHasher();

    @Override
    public ProguardMapIdBuilder accept(String string) {
      for (int i = 0; i < string.length(); i++) {
        char c = string.charAt(i);
        if (!Character.isWhitespace(c)) {
          hasher.putInt(c);
        }
      }
      return this;
    }

    public ProguardMapId build() {
      return new ProguardMapId(hasher.hash().toString().substring(0, PG_MAP_ID_LENGTH));
    }
  }

  class ProguardMapWriter implements ChainableStringConsumer {

    @Override
    public ProguardMapWriter accept(String string) {
      consumer.accept(string, reporter);
      return this;
    }
  }

  static class ProguardMapChecker implements StringConsumer {

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
      assert validateProguardMapParses(contents.toString());
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
}
