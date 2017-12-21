// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.shaking.FilteredClassPath;
import java.nio.file.Path;

/** Provider for archives of program resources. */
public class ArchiveProgramResourceProvider extends FilteredArchiveProgramResourceProvider
    implements ProgramResourceProvider {

  ArchiveProgramResourceProvider(Path archive, boolean ignoreDexInArchive) {
    super(FilteredClassPath.unfiltered(archive), ignoreDexInArchive);
  }
}
