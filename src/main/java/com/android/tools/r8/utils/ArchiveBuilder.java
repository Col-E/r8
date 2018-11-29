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
import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

public class ArchiveBuilder implements OutputBuilder {
  private final Path archive;
  private final Origin origin;
  private ZipOutputStream stream = null;
  private boolean closed = false;
  private int openCount = 0;
  private int classesFileIndex = 0;
  private Map<Integer, DelayedData> delayedClassesDexFiles = new HashMap<>();
  private SortedSet<DelayedData> delayedFiles = new TreeSet<>();
  private SortedSet<String> delayedDirectories = new TreeSet<>();

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
      writeDelayed(handler);
      closed = true;
      try {
        getStreamRaw().close();
        stream = null;
      } catch (IOException e) {
        handler.error(new ExceptionDiagnostic(e, origin));
      }
    }
  }

  private void writeDelayed(DiagnosticsHandler handler) {
    // We should never have any indexed files at this point
    assert delayedClassesDexFiles.isEmpty();
    for (String directory : delayedDirectories) {
      writeDirectoryNow(directory, handler);
    }
    for (DelayedData data : delayedFiles) {
      writeFileNow(data.name, data.content, handler);
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
  public synchronized void addDirectory(String name, DiagnosticsHandler handler) {
    delayedDirectories.add(name);
  }

  private void writeDirectoryNow(String name, DiagnosticsHandler handler) {
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
      ByteDataView view = ByteDataView.of(ByteStreams.toByteArray(in));
      synchronized (this) {
        delayedFiles.add(new DelayedData(name, view));
      }
    } catch (IOException e) {
      handleIOException(e, handler);
    } catch (ResourceException e) {
      handler.error(new StringDiagnostic("Failed to open input: " + e.getMessage(),
          content.getOrigin()));
    }
  }

  @Override
  public synchronized void addFile(String name, ByteDataView content, DiagnosticsHandler handler) {
    delayedFiles.add(new DelayedData(name,  ByteDataView.of(content.copyByteData())));
  }

  private void writeFileNow(String name, ByteDataView content, DiagnosticsHandler handler) {
    try {
      ZipUtils.writeToZipStream(getStream(handler), name, content, ZipEntry.STORED);
    } catch (IOException e) {
      handleIOException(e, handler);
    }
  }

  private void writeNextIfAvailable(DiagnosticsHandler handler) {
    DelayedData data = delayedClassesDexFiles.remove(classesFileIndex);
    while (data != null) {
      writeFileNow(data.name, data.content, handler);
      classesFileIndex++;
      data = delayedClassesDexFiles.remove(classesFileIndex);
    }
  }

  @Override
  public synchronized void addIndexedClassFile(
      int index, String name, ByteDataView content, DiagnosticsHandler handler) {
    if (index == classesFileIndex) {
      // Fast case, we got the file in order (or we only had one).
      writeFileNow(name, content, handler);
      classesFileIndex++;
      writeNextIfAvailable(handler);
    } else {
      // Data is released in the application writer, take a copy.
      delayedClassesDexFiles.put(index,
          new DelayedData(name, ByteDataView.of(content.copyByteData())));
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

  private static class DelayedData implements Comparable<DelayedData> {
    public final String name;
    public final ByteDataView content;
    public DelayedData(String name, ByteDataView content) {
      this.name = name;
      this.content = content;
    }

    @Override
    public int compareTo(DelayedData other) {
      if (other == null) {
        return name.compareTo(null);
      }
      return name.compareTo(other.name);
    }
  }
}
