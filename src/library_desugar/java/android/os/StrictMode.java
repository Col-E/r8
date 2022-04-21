// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package android.os;

public class StrictMode {

  public static ThreadPolicy getThreadPolicy() {
    return null;
  }

  public static void setThreadPolicy(ThreadPolicy policy) {}

  public static class ThreadPolicy {
    public static class Builder {
      public Builder(ThreadPolicy policy) {}

      public Builder permitDiskReads() {
        return null;
      }

      public ThreadPolicy build() {
        return null;
      }
    }
  }
}
