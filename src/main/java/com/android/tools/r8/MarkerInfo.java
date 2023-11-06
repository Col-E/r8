// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

/** Information present in a given marker. */
@Keep
public interface MarkerInfo {

  /** Get the tool that has generated the marker. */
  String getTool();

  /** Get the version of the tool that has generated the marker. */
  String getVersion();

  /** True of the generating tool was the R8 compiler. */
  boolean isR8();

  /** True of the generating tool was the D8 compiler. */
  boolean isD8();

  /** True of the generating tool was the L8 compiler. */
  boolean isL8();

  /** Get the min-api specified by the marker, or -1 if not defined. */
  int getMinApi();

  /** True if the "backend" information is present or can be inferred for the tool. */
  boolean hasBackend();

  /** True if the tool is known to be targeting class files as backend output. */
  boolean isBackendClassFiles();

  /** True if the tool is known to be targeting DEX as backend output. */
  boolean isBackendDexFiles();

  /** True if the tool has a known compilation mode (i.e., release or debug). */
  boolean hasCompilationMode();

  /** True if the tool has a known compilation mode and the mode is "debug". */
  boolean isCompilationModeDebug();

  /** True if the tool has a known compilation mode and the mode is "release". */
  boolean isCompilationModeRelease();

  /** True if the tool is R8 using compatibility mode. */
  boolean isR8ModeCompatibility();

  /** True if the tool is R8 using full mode (e.g., not in compatibility mode). */
  boolean isR8ModeFull();

  /**
   * Get the raw encoding of the marker used by the compilers.
   *
   * <p>Warning: The format of this encoding may be subject to change and its concrete encoding
   * should not be assumed. For usages of concrete information in the marker the structured API on
   * {@link MarkerInfo} should be used.
   */
  String getRawEncoding();
}
