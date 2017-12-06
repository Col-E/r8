// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexApplication;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;

public class ProguardMapSupplier {
  public static ProguardMapSupplier fromClassNameMapper(ClassNameMapper classNameMapper) {
    return new ProguardMapSupplier(classNameMapper);
  }

  public static ProguardMapSupplier fromNamingLens(
      NamingLens namingLens, DexApplication dexApplication) {
    return new ProguardMapSupplier(namingLens, dexApplication);
  }

  private ProguardMapSupplier(ClassNameMapper classNameMapper) {
    this.useClassNameMapper = true;
    this.classNameMapper = classNameMapper;
    this.namingLens = null;
    this.application = null;
  }

  private ProguardMapSupplier(NamingLens namingLens, DexApplication dexApplication) {
    this.useClassNameMapper = false;
    this.classNameMapper = null;
    this.namingLens = namingLens;
    this.application = dexApplication;
  }

  private final boolean useClassNameMapper;
  private final ClassNameMapper classNameMapper;
  private final NamingLens namingLens;
  private final DexApplication application;

  public String get() {
    if (useClassNameMapper) {
      assert classNameMapper != null;
      return classNameMapper.toString();
    }
    assert namingLens != null && application != null;
    // TODO(herhut): Should writing of the proguard-map file be split like this?
    if (!namingLens.isIdentityLens()) {
      ByteArrayOutputStream bytes = new ByteArrayOutputStream();
      PrintStream stream = new PrintStream(bytes);
      new MinifiedNameMapPrinter(application, namingLens).write(stream);
      stream.flush();
      return bytes.toString();
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
