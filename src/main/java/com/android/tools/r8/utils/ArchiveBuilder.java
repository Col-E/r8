// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DataResource;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.google.common.io.ByteStreams;
import java.io.BufferedOutputStream;
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
  private SortedSet<DelayedData> delayedWrites = new TreeSet<>();

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
    for (DelayedData data : delayedWrites) {
      if (data.isDirectory) {
        assert data.content == null;
        writeDirectoryNow(data.name, handler);
      } else {
        assert data.content != null;
        writeFileNow(data.name, data.content, handler, data.storeCompressed);
      }
    }
  }

  private ZipOutputStream getStreamRaw() throws IOException {
    if (stream != null) {
      return stream;
    }
    stream =
        new ZipOutputStream(
            new BufferedOutputStream(
                Files.newOutputStream(
                    archive, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)));
    return stream;
  }

  /** Get or open the zip output stream. */
  private synchronized ZipOutputStream getStream() throws IOException {
    assert !closed;
    return getStreamRaw();
  }

  private void handleIOException(IOException e, DiagnosticsHandler handler) {
    ExceptionDiagnostic diagnostic = new ExceptionDiagnostic(e, origin);
    if (e instanceof ZipException && e.getMessage().startsWith("duplicate entry")) {
      // For now we stick to the Proguard behaviour, see section "Warning: can't write resource ...
      // Duplicate zip entry" on https://www.guardsquare.com/en/proguard/manual/troubleshooting.
      handler.warning(diagnostic);
    } else {
      handler.error(diagnostic);
    }
  }

  @Override
  public synchronized void addDirectory(String name, DiagnosticsHandler handler) {
    delayedWrites.add(DelayedData.createDirectory(name));
  }

  private void writeDirectoryNow(String name, DiagnosticsHandler handler) {
    if (name.charAt(name.length() - 1) != DataResource.SEPARATOR) {
      name += DataResource.SEPARATOR;
    }
    ZipEntry entry = new ZipEntry(name);
    entry.setTime(0);
    synchronized (this) {
      try {
        ZipOutputStream zip = getStream();
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
        if (AndroidApiDataAccess.isApiDatabaseEntry(name)) {
          writeFileNow(name, view, handler, true);
        } else {
          delayedWrites.add(DelayedData.createFile(name, view, true));
        }
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
    addFile(name, content, handler, true);
  }

  public synchronized void addFile(
      String name, ByteDataView content, DiagnosticsHandler handler, boolean storeCompressed) {
    delayedWrites.add(
        DelayedData.createFile(name, ByteDataView.of(content.copyByteData()), storeCompressed));
  }

  private void writeFileNow(
      String name, ByteDataView content, DiagnosticsHandler handler, boolean compressed) {
    try {
      ZipUtils.writeToZipStream(
          getStream(),
          name,
          content,
          AndroidApiDataAccess.isApiDatabaseEntry(name) || !compressed
              ? ZipEntry.STORED
              : ZipEntry.DEFLATED);
    } catch (IOException e) {
      handleIOException(e, handler);
    }
  }

  private void writeNextIfAvailable(DiagnosticsHandler handler) {
    DelayedData data = delayedClassesDexFiles.remove(classesFileIndex);
    while (data != null) {
      writeFileNow(data.name, data.content, handler, data.storeCompressed);
      classesFileIndex++;
      data = delayedClassesDexFiles.remove(classesFileIndex);
    }
  }

  @Override
  public synchronized void addIndexedClassFile(
      int index, String name, ByteDataView content, DiagnosticsHandler handler) {
    if (index == classesFileIndex) {
      // Fast case, we got the file in order (or we only had one).
      writeFileNow(name, content, handler, true);
      classesFileIndex++;
      writeNextIfAvailable(handler);
    } else {
      // Data is released in the application writer, take a copy.
      delayedClassesDexFiles.put(
          index, new DelayedData(name, ByteDataView.of(content.copyByteData()), false, true));
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
    public final boolean isDirectory;
    public final boolean storeCompressed;

    public static DelayedData createFile(
        String name, ByteDataView content, boolean storeCompressed) {
      return new DelayedData(name, content, false, storeCompressed);
    }

    public static DelayedData createDirectory(String name) {
      return new DelayedData(name, null, true, true);
    }

    private DelayedData(
        String name, ByteDataView content, boolean isDirectory, boolean storeCompressed) {
      this.name = name;
      this.content = content;
      this.isDirectory = isDirectory;
      this.storeCompressed = storeCompressed;
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
