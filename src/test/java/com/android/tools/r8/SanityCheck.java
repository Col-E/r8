// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;

public class SanityCheck extends TestBase {

  private void checkJarContent(Path jar, boolean allowDirectories, Set<String> additionalEntries)
      throws Exception {
    ZipFile zipFile;
    try {
      zipFile = new ZipFile(jar.toFile(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      if (!Files.exists(jar)) {
        throw new NoSuchFileException(jar.toString());
      } else {
        throw e;
      }
    }
    boolean licenseSeen = false;
    final Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      ZipEntry entry = entries.nextElement();
      String name = entry.getName();
      if (ZipUtils.isClassFile(name) || name.endsWith(".kotlin_builtins")) {
        assertThat(name, startsWith("com/android/tools/r8/"));
      } else if (name.equals("META-INF/MANIFEST.MF")) {
        // Allow.
      } else if (name.equals("LICENSE")) {
        licenseSeen = true;
      } else if (additionalEntries.contains(name)) {
        // Allow.
      } else if (name.endsWith("/")) {
        assertTrue("Unexpected directory entry in" + jar, allowDirectories);
      } else {
        fail("Unexpected entry '" + name + "' in " + jar);
      }
    }
    assertTrue("No LICENSE entry found in " + jar, licenseSeen);
  }

  private void checkLibJarContent(Path jar) throws Exception {
    checkJarContent(jar, false, ImmutableSet.of());
  }

  private void checkJarContent(Path jar) throws Exception {
    checkJarContent(
        jar,
        true,
        ImmutableSet.of(
            "META-INF/services/"
                + "com.android.tools.r8."
                + "jetbrains.kotlinx.metadata.impl.extensions.MetadataExtensions"));
  }

  @Test
  public void testLibJarsContent() throws Exception {
    checkLibJarContent(ToolHelper.R8LIB_JAR);
    checkLibJarContent(ToolHelper.COMPATDXLIB_JAR);
    checkLibJarContent(ToolHelper.COMPATPROGUARDLIB_JAR);
  }

  @Test
  public void testJarsContent() throws Exception {
    if (Files.exists(ToolHelper.D8_JAR)) {
      checkJarContent(ToolHelper.D8_JAR);
    }
    checkJarContent(ToolHelper.R8_JAR);
    checkJarContent(ToolHelper.COMPATDX_JAR);
    checkJarContent(ToolHelper.COMPATPROGUARD_JAR);
  }
}
