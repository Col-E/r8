// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResource;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class ArchiveBuilder implements OutputBuilder {
  private final Path archive;
  private final Origin origin;
  private ZipOutputStream stream = null;
  private boolean closed = false;
  private int openCount = 0;

  public ArchiveBuilder(Path archive) {
    this.archive = archive;
    origin = new PathOrigin(archive);
  }

  @Override
  public synchronized void open() {
    assert !closed;
    openCount++;
  }

  @Override
  public synchronized void close(DiagnosticsHandler handler)  {
    assert !closed;
    openCount--;
    if (openCount == 0) {
      closed = true;
      try {
        getStreamRaw().close();
        stream = null;
      } catch (IOException e) {
        handler.error(new ExceptionDiagnostic(e, origin));
      }
    }
  }

  private ZipOutputStream getStreamRaw() throws IOException {
    if (stream != null) {
      return stream;
    }
    stream = new ZipOutputStream(Files.newOutputStream(
        archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING));
    return stream;
  }

  /** Get or open the zip output stream. */
  private synchronized ZipOutputStream getStream(DiagnosticsHandler handler) {
    assert !closed;
    try {
      getStreamRaw();
    } catch (IOException e) {
      handler.error(new ExceptionDiagnostic(e, origin));
    }
    return stream;
  }

  private void handleIOException(IOException e, DiagnosticsHandler handler) {
    if (e instanceof ZipException && e.getMessage().startsWith("duplicate entry")) {
      // For now we stick to the Proguard behaviour, see section "Warning: can't write resource ...
      // Duplicate zip entry" on https://www.guardsquare.com/en/proguard/manual/troubleshooting.
      handler.warning(new ExceptionDiagnostic(e, origin));
    } else {
      handler.error(new ExceptionDiagnostic(e, origin));
    }
  }

  @Override
  public void addDirectory(String name, DiagnosticsHandler handler) {
    if (name.charAt(name.length() - 1) != DataResource.SEPARATOR) {
      name += DataResource.SEPARATOR;
    }
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(0);
    ZipOutputStream zip = getStream(handler);
    synchronized (this) {
      try {
        zip.putNextEntry(entry);
        zip.closeEntry();
      } catch (IOException e) {
        handleIOException(e, handler);
      }
    }
  }

  @Override
  public void addFile(String name, DataEntryResource content, DiagnosticsHandler handler) {
    try (InputStream in = content.getByteStream()) {
      addFile(name, ByteDataView.of(ByteStreams.toByteArray(in)), handler);
    } catch (IOException e) {
      handleIOException(e, handler);
    } catch (ResourceException e) {
      handler.error(new StringDiagnostic("Failed to open input: " + e.getMessage(),
          content.getOrigin()));
    }
  }

  @Override
  public synchronized void addFile(String name, ByteDataView content, DiagnosticsHandler handler) {
    try {
      ZipUtils.writeToZipStream(getStream(handler), name, content, ZipEntry.STORED);
    } catch (IOException e) {
      handleIOException(e, handler);
    }
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Path getPath() {
    return archive;
  }
}
