// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** Information present in a given marker. */
@Keep
public interface MarkerInfo {

  /** Get the tool that has generated the marker. */
  String getTool();

  /** True of the generating tool was the R8 compiler. */
  boolean isR8();

  /** True of the generating tool was the D8 compiler. */
  boolean isD8();

  /** True of the generating tool was the L8 compiler. */
  boolean isL8();

  /** Get the min-api specified by the marker, or -1 if not defined. */
  int getMinApi();

  /**
   * Get the raw encoding of the marker used by the compilers.
   *
   * <p>Warning: The format of this encoding may be subject to change and its concrete encoding
   * should not be assumed. For usages of concrete information in the marker the structured API on
   * {@link MarkerInfo} should be used.
   */
  String getRawEncoding();
}
