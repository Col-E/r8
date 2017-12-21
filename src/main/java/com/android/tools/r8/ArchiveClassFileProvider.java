// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.utils.FilteredArchiveClassFileProvider;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Lazy Java class file resource provider loading class files from a zip archive.
 *
 * <p>The descriptor index is built eagerly upon creating the provider and subsequent requests for
 * resources in the descriptor set will then force the read of zip entry contents.
 */
public class ArchiveClassFileProvider extends FilteredArchiveClassFileProvider
    implements ClassFileResourceProvider, Closeable {

  public ArchiveClassFileProvider(Path archive) throws IOException {
    super(FilteredClassPath.unfiltered(archive));
  }
}
