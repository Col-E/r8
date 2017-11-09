// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.apiusagesample;

import com.android.tools.r8.ArchiveClassFileProvider;
import com.android.tools.r8.ClassFileResourceProvider;
import com.android.tools.r8.Resource;
import com.android.tools.r8.utils.DirectoryClassFileProvider;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;

public class CachingArchiveClassFileProvider extends ArchiveClassFileProvider {

  private ConcurrentHashMap<String, Resource> resources = new ConcurrentHashMap<>();

  private CachingArchiveClassFileProvider(Path archive) throws IOException {
    super(archive);
  }

  @Override
  public Resource getResource(String descriptor) {
    return resources.computeIfAbsent(descriptor, desc -> super.getResource(desc));
  }

  public static ClassFileResourceProvider getProvider(Path entry)
      throws IOException {
    if (Files.isRegularFile(entry)) {
      return new CachingArchiveClassFileProvider(entry);
    } else if (Files.isDirectory(entry)) {
      return DirectoryClassFileProvider.fromDirectory(entry);
    } else {
      throw new FileNotFoundException(entry.toString());
    }
  }
}
