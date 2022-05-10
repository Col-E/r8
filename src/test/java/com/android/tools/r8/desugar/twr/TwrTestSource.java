// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.twr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.jar.JarFile;

public class TwrTestSource {

  public static void main(String[] args) throws IOException {
    try (JarFile f = new JarFile(args.length > 0 ? args[0] : "")) {
      System.out.println(f.stream().count());
    } catch (FileNotFoundException e) {
      System.out.println("no file");
    }
    try (JarFile f = new JarFile(args.length > 0 ? args[0] : "")) {
      System.out.println(f.stream().count());
    } catch (FileNotFoundException e) {
      System.out.println("no file");
    }
  }
}
