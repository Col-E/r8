// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

// This code is not run directly. It needs to be compiled to dex code.
// 'arithmetic.dex' is what is run.

package dataresource.lib;

import dataresource.ResourceTest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Properties;
import java.util.Random;

public class LibClass {
  static final String name;
  static {
    name = "dataresource.lib.LibClass";
  }

  public static String getConstString() {
    return "dataresource.lib.LibClass";
  }

  public static String getConcatConstString() throws IOException {
    return "dataresource.lib.LibClass" + getLibClassProperty();
  }

  public static String getConstField() {
    return name;
  }

  public static URL getThisDir() {
    return LibClass.class.getResource("");
  }

  public static URL getLibClassProperties() {
    return LibClass.class.getResource(LibClass.class.getSimpleName() + ".properties");
  }

  public static String getLibClassProperty() throws IOException {
    Properties properties = new Properties();
    properties.load(LibClass.class.getResourceAsStream(
        LibClass.class.getSimpleName() + ".properties"));
    return "" + properties.get(LibClass.class.getName());
  }

  public static String getText() throws IOException {
    byte[] buffer = new byte[1000];
    StringBuilder sb = new StringBuilder();
    try (InputStream stream = ResourceTest.class.getResourceAsStream("resource.txt")) {
      int size = stream.read(buffer);
      while (size != -1) {
        sb.append(new String(buffer, 0, size));
        size = stream.read(buffer);
      }
    }
    return sb.toString();
  }
}
