// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.MapIdEnvironment;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.Version;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.VersionProperties;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class ProguardMapSupplier {

  public static final String MARKER_KEY_COMPILER = "compiler";
  public static final String MARKER_VALUE_COMPILER = "R8";
  public static final String MARKER_KEY_COMPILER_VERSION = "compiler_version";
  public static final String MARKER_KEY_COMPILER_HASH = "compiler_hash";
  public static final String MARKER_KEY_MIN_API = "min_api";
  public static final String MARKER_KEY_PG_MAP_ID = "pg_map_id";
  public static final String MARKER_KEY_PG_MAP_HASH = "pg_map_hash";
  public static final String SHA_256_KEY = "SHA-256";

  public static int PG_MAP_ID_LENGTH = 7;

  // Hash of the Proguard map (excluding the header up to and including the hash marker).
  public static class ProguardMapId {
    private final String id;
    private final String hash;

    private ProguardMapId(String id, String hash) {
      assert id != null;
      assert hash != null;
      this.id = id;
      this.hash = hash;
    }

    /** Id for the map file (user defined or a truncated prefix of the content hash). */
    public String getId() {
      return id;
    }

    /** The actual content hash. */
    public String getHash() {
      return hash;
    }
  }

  private final ClassNameMapper classNameMapper;
  private final StringConsumer consumer;
  private final InternalOptions options;
  private final Reporter reporter;

  private ProguardMapSupplier(ClassNameMapper classNameMapper, InternalOptions options) {
    assert classNameMapper != null;
    this.classNameMapper = classNameMapper.sorted();
    // TODO(b/217111432): Validate Proguard using ProguardMapChecker without building the entire
    //  Proguard map in memory.
    this.consumer = options.proguardMapConsumer;
    this.options = options;
    this.reporter = options.reporter;
  }

  public static ProguardMapSupplier create(
      ClassNameMapper classNameMapper, InternalOptions options) {
    return new ProguardMapSupplier(classNameMapper, options);
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
    return builder.build(options.mapIdProvider);
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
      builder.append("# " + MARKER_KEY_MIN_API + ": " + options.getMinApiLevel().getLevel() + "\n");
    }
    if (Version.isDevelopmentVersion()) {
      builder.append(
          "# " + MARKER_KEY_COMPILER_HASH + ": " + VersionProperties.INSTANCE.getSha() + "\n");
    }
    // Turn off linting of the mapping file in some build systems.
    builder.append("# common_typos_disable" + "\n");
    // Emit the R8 specific map-file version.
    MapVersion mapVersion = options.getMapFileVersion();
    if (mapVersion.isGreaterThan(MapVersion.MAP_VERSION_NONE)) {
      builder
          .append("# ")
          .append(mapVersion.toMapVersionMappingInformation().serialize())
          .append("\n");
    }
    builder.append("# " + MARKER_KEY_PG_MAP_ID + ": " + id.getId() + "\n");
    // Place the map hash as the last header item. Everything past this line is the hashed content.
    builder
        .append("# ")
        .append(MARKER_KEY_PG_MAP_HASH)
        .append(": ")
        .append(SHA_256_KEY)
        .append(" ")
        .append(id.getHash())
        .append("\n");
    consumer.accept(builder.toString(), reporter);
  }

  static class ProguardMapIdBuilder implements ChainableStringConsumer {

    private final Hasher hasher = Hashing.sha256().newHasher();

    private MapIdProvider getProviderOrDefault(MapIdProvider provider) {
      return provider != null
          ? provider
          : environment -> environment.getMapHash().substring(0, PG_MAP_ID_LENGTH);
    }

    private MapIdEnvironment getEnvironment(String hash) {
      return new MapIdEnvironment() {
        @Override
        public String getMapHash() {
          return hash;
        }
      };
    }

    @Override
    public ProguardMapIdBuilder accept(String string) {
      hasher.putString(string, StandardCharsets.UTF_8);
      return this;
    }

    public ProguardMapId build(MapIdProvider mapIdProvider) {
      String hash = hasher.hash().toString();
      String id = getProviderOrDefault(mapIdProvider).get(getEnvironment(hash));
      return new ProguardMapId(id, hash);
    }
  }

  class ProguardMapWriter implements ChainableStringConsumer {

    @Override
    public ProguardMapWriter accept(String string) {
      consumer.accept(string, reporter);
      return this;
    }
  }

  public static class ProguardMapChecker implements StringConsumer {

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
}
