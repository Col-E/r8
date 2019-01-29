// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf_segments;

import com.google.classlib.parser.ClassFileParser;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MeasureLib {

  public Metrics run(File jar) throws IOException {
    ZipFile zip = new ZipFile(jar);
    Enumeration zipEntries = zip.entries();
    Metrics metrics = new Metrics();
    while (zipEntries.hasMoreElements()) {
      ZipEntry entry = (ZipEntry) zipEntries.nextElement();
      if (entry.isDirectory()
          || !entry.getName().endsWith(".class")
          || entry.getName().endsWith("-info.class")) {
        continue;
      }
      Metrics entryMetrics = parse(zip.getInputStream(entry));
      long size = entry.getSize();
      long sum =
          entryMetrics.asList().stream().mapToLong(s -> s.contributeToSize ? s.getSize() : 0).sum();
      assert sum == size;
      metrics.increment(entryMetrics);
      metrics.size.increment(0, size);
    }
    return metrics;
  }

  public Metrics parse(InputStream input) throws IOException {
    Metrics metrics = new Metrics();
    MeasureClassEventHandler handler = new MeasureClassEventHandler(metrics);
    ClassFileParser parser = ClassFileParser.getInstance(input, handler);
    parser.parseClassFile();
    return metrics;
  }

  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      System.out.println("Usage: cfSegments <path_to_file>");
      return;
    }
    System.out.println(new MeasureLib().run(Paths.get(args[0]).toFile()));
  }
}
