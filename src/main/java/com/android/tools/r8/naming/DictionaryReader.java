// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DictionaryReader implements AutoCloseable {

  private BufferedReader reader;

  public DictionaryReader(Path path) throws IOException {
    this.reader = Files.newBufferedReader(path);
  }

  public String readName() throws IOException {
    assert reader != null;

    StringBuilder name = new StringBuilder();
    int readCharAsInt;

    while ((readCharAsInt = reader.read()) != -1) {
      char readChar = (char) readCharAsInt;

      if ((name.length() != 0 && Character.isJavaIdentifierPart(readChar))
          || (name.length() == 0 && Character.isJavaIdentifierStart(readChar))) {
        name.append(readChar);
      } else {
        if (readChar == '#') {
          reader.readLine();
        }

        if (name.length() != 0) {
          return name.toString();
        }
      }
    }

    return name.toString();
  }

  @Override
  public void close() throws IOException {
    if (reader != null) {
      reader.close();
    }
  }

  public static ImmutableList<String> readAllNames(Path path, Reporter reporter) {
    if (path != null) {
      Builder<String> namesBuilder = new ImmutableList.Builder<String>();
      try (DictionaryReader reader = new DictionaryReader(path);) {
        String name = reader.readName();
        while (!name.isEmpty()) {
          namesBuilder.add(name);
          name = reader.readName();
        }
      } catch (IOException e) {
        reporter.error(new StringDiagnostic(
            "Unable to create dictionary from file " + path.toString(),
            new PathOrigin(path)));
      }
      return namesBuilder.build();
    } else {
      return ImmutableList.of();
    }
  }
}
