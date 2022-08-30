// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

public class ArtProfileMethodRuleFlagsUtils {

  private static final int FLAG_HOT = 1;
  private static final int FLAG_STARTUP = 2;
  private static final int FLAG_POST_STARTUP = 4;

  // Getters.

  public static boolean isHot(int flags) {
    return isFlagSet(flags, FLAG_HOT);
  }

  public static boolean isStartup(int flags) {
    return isFlagSet(flags, FLAG_STARTUP);
  }

  public static boolean isPostStartup(int flags) {
    return isFlagSet(flags, FLAG_POST_STARTUP);
  }

  private static boolean isFlagSet(int flags, int flag) {
    return (flags & flag) != 0;
  }

  // Setters.

  public static int setIsHot(int flags, boolean isHot) {
    return isHot ? setFlag(flags, FLAG_HOT) : unsetFlag(flags, FLAG_HOT);
  }

  public static int setIsStartup(int flags, boolean isStartup) {
    return isStartup ? setFlag(flags, FLAG_STARTUP) : unsetFlag(flags, FLAG_STARTUP);
  }

  public static int setIsPostStartup(int flags, boolean isPostStartup) {
    return isPostStartup ? setFlag(flags, FLAG_POST_STARTUP) : unsetFlag(flags, FLAG_POST_STARTUP);
  }

  private static int setFlag(int flags, int flag) {
    return flags | flag;
  }

  private static int unsetFlag(int flags, int flag) {
    return flags & ~flag;
  }
}
