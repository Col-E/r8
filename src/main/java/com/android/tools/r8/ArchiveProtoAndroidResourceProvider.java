// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.AndroidResourceInput.Kind;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.ArchiveEntryOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Provides android resources from an archive.
 *
 * <p>The descriptor index is built eagerly upon creating the provider and subsequent requests for
 * resources in the descriptor set will then force the read of zip entry contents.
 */
@KeepForApi
public class ArchiveProtoAndroidResourceProvider implements AndroidResourceProvider {
  private final Path archive;
  private final Origin origin;

  private static final String MANIFEST_NAME = "AndroidManifest.xml";
  private static final String RESOURCE_TABLE = "resources.pb";
  private static final String RES_FOLDER = "res/";
  private static final String XML_SUFFIX = ".xml";

  /**
   * Creates an android resource provider from an archive.
   *
   * @param archive Zip archive to provide resources from.
   * @param origin Origin of the archive.
   */
  public ArchiveProtoAndroidResourceProvider(Path archive, Origin origin) {
    this.archive = archive;
    this.origin = origin;
  }

  @Override
  public Collection<AndroidResourceInput> getAndroidResources() throws ResourceException {
    try (ZipFile zipFile = FileUtils.createZipFile(archive.toFile(), StandardCharsets.UTF_8)) {
      List<AndroidResourceInput> resources = new ArrayList<>();
      final Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        String name = entry.getName();
        ByteAndroidResourceInput resource =
            new ByteAndroidResourceInput(
                name,
                getKindFromName(name),
                ByteStreams.toByteArray(zipFile.getInputStream(entry)),
                new ArchiveEntryOrigin(name, origin));
        resources.add(resource);
      }
      return resources;
    } catch (IOException e) {
      throw new ResourceException(origin, e);
    }
  }

  private Kind getKindFromName(String name) {
    if (name.equals(MANIFEST_NAME)) {
      return Kind.MANIFEST;
    }
    if (name.equals(RESOURCE_TABLE)) {
      return Kind.RESOURCE_TABLE;
    }
    if (!name.startsWith(RES_FOLDER)) {
      return Kind.UNKNOWN;
    }
    if (name.endsWith(XML_SUFFIX)) {
      return Kind.XML_FILE;
    }
    return Kind.RES_FOLDER_FILE;
  }

  private static class ByteAndroidResourceInput implements AndroidResourceInput {

    private final String name;
    private final Kind kind;
    private final byte[] bytes;
    private final Origin origin;

    public ByteAndroidResourceInput(String name, Kind kind, byte[] bytes, Origin origin) {
      this.name = name;
      this.kind = kind;
      this.bytes = bytes;
      this.origin = origin;
    }

    @Override
    public ResourcePath getPath() {
      return () -> name;
    }

    @Override
    public Kind getKind() {
      return kind;
    }

    @Override
    public InputStream getByteStream() throws ResourceException {
      return new ByteArrayInputStream(bytes);
    }

    @Override
    public Origin getOrigin() {
      return origin;
    }
  }
}
