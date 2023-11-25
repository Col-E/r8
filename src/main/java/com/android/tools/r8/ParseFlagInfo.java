// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.util.List;

/**
 * Descriptive information about a command-line flag.
 *
 * <p>Note that this information is purly for usage information and is not an exact semantics of
 * flags.
 */
@KeepForApi
public interface ParseFlagInfo {

  /** Get the primary format description of the flag (including arguments). */
  String getFlagFormat();

  /** Get the alternative format descriptions of the flag. Empty if there are none. */
  List<String> getFlagFormatAlternatives();

  /** Get the help lines for the flag. */
  List<String> getFlagHelp();
}
