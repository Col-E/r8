// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.Version;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.utils.VersionProperties;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;

public class ProguardMapSupplier {

  public static final String MARKER_KEY_COMPILER = "compiler";
  public static final String MARKER_VALUE_COMPILER = "R8";
  public static final String MARKER_KEY_COMPILER_VERSION = "compiler_version";
  public static final String MARKER_KEY_COMPILER_HASH = "compiler_hash";
  public static final String MARKER_KEY_MIN_API = "min_api";

  public static ProguardMapSupplier fromClassNameMapper(
      ClassNameMapper classNameMapper, int minApiLevel) {
    return new ProguardMapSupplier(classNameMapper, minApiLevel);
  }

  public static ProguardMapSupplier fromNamingLens(
      NamingLens namingLens, DexApplication dexApplication, int minApiLevel) {
    return new ProguardMapSupplier(namingLens, dexApplication, minApiLevel);
  }

  private ProguardMapSupplier(ClassNameMapper classNameMapper, int minApiLevel) {
    this.useClassNameMapper = true;
    this.classNameMapper = classNameMapper;
    this.namingLens = null;
    this.application = null;
    this.minApiLevel = minApiLevel;
  }

  private ProguardMapSupplier(
      NamingLens namingLens, DexApplication dexApplication, int minApiLevel) {
    this.useClassNameMapper = false;
    this.classNameMapper = null;
    this.namingLens = namingLens;
    this.application = dexApplication;
    this.minApiLevel = minApiLevel;
  }

  private final boolean useClassNameMapper;
  private final ClassNameMapper classNameMapper;
  private final NamingLens namingLens;
  private final DexApplication application;
  private final int minApiLevel;

  public String get() {
    String shaLine = "";
    if (Version.isDev()) {
      shaLine = "# " + MARKER_KEY_COMPILER_HASH + ": " + VersionProperties.INSTANCE.getSha() + "\n";
    }
    return "# "
        + MARKER_KEY_COMPILER
        + ": "
        + MARKER_VALUE_COMPILER
        + "\n"
        + "# "
        + MARKER_KEY_COMPILER_VERSION
        + ": "
        + Version.LABEL
        + "\n"
        + "# "
        + MARKER_KEY_MIN_API
        + ": "
        + minApiLevel
        + "\n"
        + shaLine
        + getBody();
  }

  private String getBody() {
    if (useClassNameMapper) {
      assert classNameMapper != null;
      return classNameMapper.toString();
    }
    assert namingLens != null && application != null;
    // TODO(herhut): Should writing of the proguard-map file be split like this?
    if (!namingLens.isIdentityLens()) {
      StringBuilder map = new StringBuilder();
      new MinifiedNameMapPrinter(application, namingLens).write(map);
      return map.toString();
    }
    if (application.getProguardMap() != null) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      Writer writer = new PrintWriter(bytes);
      try {
        application.getProguardMap().write(writer);
        writer.flush();
      } catch (IOException e) {
        throw new RuntimeException("IOException while creating Proguard-map output: " + e);
      }
      return bytes.toString();
    }
    return "# This Proguard-map is intentionally empty"
        + " because no names or line numbers have been changed.\n";
  }
}
