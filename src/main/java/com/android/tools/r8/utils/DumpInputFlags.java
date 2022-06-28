// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.errors.Unreachable;
import java.nio.file.Path;
import java.nio.file.Paths;

public abstract class DumpInputFlags {

  private static final String DUMP_INPUT_TO_FILE_PROPERTY = "com.android.tools.r8.dumpinputtofile";
  private static final String DUMP_INPUT_TO_DIRECTORY_PROPERTY =
      "com.android.tools.r8.dumpinputtodirectory";

  public static DumpInputFlags getDefault() {
    String dumpInputToFile = System.getProperty(DUMP_INPUT_TO_FILE_PROPERTY);
    if (dumpInputToFile != null) {
      return dumpToFile(Paths.get(dumpInputToFile));
    }
    String dumpInputToDirectory = System.getProperty(DUMP_INPUT_TO_DIRECTORY_PROPERTY);
    if (dumpInputToDirectory != null) {
      return dumpToDirectory(Paths.get(dumpInputToDirectory));
    }
    return noDump();
  }

  public static DumpInputFlags noDump() {
    return new DumpInputFlags() {

      @Override
      public Path getDumpPath() {
        throw new Unreachable();
      }

      @Override
      public boolean isNoDump() {
        return true;
      }

      @Override
      public boolean shouldFailCompilation() {
        throw new Unreachable();
      }
    };
  }

  public static DumpInputFlags dumpToFile(Path file) {
    return new DumpInputFlags() {

      @Override
      public Path getDumpPath() {
        return file;
      }

      @Override
      public boolean shouldFailCompilation() {
        return true;
      }
    };
  }

  public static DumpInputFlags dumpToDirectory(Path directory) {
    return new DumpInputFlags() {

      @Override
      public Path getDumpPath() {
        return directory.resolve("dump" + System.nanoTime() + ".zip");
      }

      @Override
      public boolean shouldFailCompilation() {
        return false;
      }
    };
  }

  public abstract Path getDumpPath();

  public boolean isNoDump() {
    return false;
  }

  public abstract boolean shouldFailCompilation();
}
