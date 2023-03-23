// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.MapIdEnvironment;
import com.android.tools.r8.MapIdProvider;
import com.android.tools.r8.ProguardMapConsumer;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.ChainableStringConsumer;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;

public class ProguardMapSupplier {

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
  private final InternalOptions options;
  private final ProguardMapConsumer consumer;
  private final Reporter reporter;
  private final Tool compiler;

  private ProguardMapSupplier(ClassNameMapper classNameMapper, Tool tool, InternalOptions options) {
    assert classNameMapper != null;
    this.classNameMapper = classNameMapper.sorted();
    // TODO(b/217111432): Validate Proguard using ProguardMapChecker without building the entire
    //  Proguard map in memory.
    this.consumer = options.proguardMapConsumer;
    this.options = options;
    this.reporter = options.reporter;
    this.compiler = tool;
  }

  public static ProguardMapSupplier create(
      ClassNameMapper classNameMapper, InternalOptions options) {
    assert options.tool != null;
    return new ProguardMapSupplier(classNameMapper, options.tool, options);
  }

  public ProguardMapId writeProguardMap() {
    ProguardMapId proguardMapId = computeProguardMapId();
    consumer.accept(
        ProguardMapMarkerInfo.builder()
            .setCompilerName(compiler.name())
            .setProguardMapId(proguardMapId)
            .setGeneratingDex(options.isGeneratingDex())
            .setApiLevel(options.getMinApiLevel())
            .setMapVersion(options.getMapFileVersion())
            .build(),
        classNameMapper);
    ExceptionUtils.withConsumeResourceHandler(reporter, this.consumer::finished);
    return proguardMapId;
  }

  private ProguardMapId computeProguardMapId() {
    ProguardMapIdBuilder builder = new ProguardMapIdBuilder();
    classNameMapper.write(builder);
    return builder.build(options.mapIdProvider);
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

}
