// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.nio.channels;

import java.adapter.AndroidVersionTest;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileAttribute;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class DesugarChannels {

  /** Special conversion for Channel to answer a converted FileChannel if required. */
  public static Channel convertMaybeLegacyChannelFromLibrary(Channel raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof FileChannel) {
      return convertMaybeLegacyFileChannelFromLibrary((FileChannel) raw);
    }
    return raw;
  }

  /**
   * Below Api 24 FileChannel does not implement SeekableByteChannel. When we get one from the
   * library, we wrap it to implement the interface.
   */
  public static FileChannel convertMaybeLegacyFileChannelFromLibrary(FileChannel raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof SeekableByteChannel) {
      return raw;
    }
    return new WrappedFileChannel(raw);
  }

  /**
   * We unwrap when going to the library since we cannot intercept the calls to final methods in the
   * library.
   */
  public static FileChannel convertMaybeLegacyFileChannelToLibrary(FileChannel raw) {
    if (raw == null) {
      return null;
    }
    if (raw instanceof WrappedFileChannel) {
      return ((WrappedFileChannel) raw).delegate;
    }
    return raw;
  }

  static class WrappedFileChannel extends FileChannel implements SeekableByteChannel {

    final FileChannel delegate;

    private WrappedFileChannel(FileChannel delegate) {
      this.delegate = delegate;
    }

    FileChannel convert(FileChannel raw) {
      return new WrappedFileChannel(raw);
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
      return delegate.read(dst);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length) throws IOException {
      return delegate.read(dsts, offset, length);
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
      return delegate.write(src);
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
      return delegate.write(srcs, offset, length);
    }

    @Override
    public long position() throws IOException {
      return delegate.position();
    }

    @Override
    public FileChannel position(long newPosition) throws IOException {
      return convert(delegate.position(newPosition));
    }

    @Override
    public long size() throws IOException {
      return delegate.size();
    }

    @Override
    public FileChannel truncate(long size) throws IOException {
      return convert(delegate.truncate(size));
    }

    @Override
    public void force(boolean metaData) throws IOException {
      delegate.force(metaData);
    }

    @Override
    public long transferTo(long position, long count, WritableByteChannel target)
        throws IOException {
      return delegate.transferTo(position, count, target);
    }

    @Override
    public long transferFrom(ReadableByteChannel src, long position, long count)
        throws IOException {
      return delegate.transferFrom(src, position, count);
    }

    @Override
    public int read(ByteBuffer dst, long position) throws IOException {
      return delegate.read(dst, position);
    }

    @Override
    public int write(ByteBuffer src, long position) throws IOException {
      return delegate.write(src, position);
    }

    @Override
    public MappedByteBuffer map(MapMode mode, long position, long size) throws IOException {
      return delegate.map(mode, position, size);
    }

    @Override
    public FileLock lock(long position, long size, boolean shared) throws IOException {
      return delegate.lock(position, size, shared);
    }

    @Override
    public FileLock tryLock(long position, long size, boolean shared) throws IOException {
      return delegate.tryLock(position, size, shared);
    }

    @Override
    public void implCloseChannel() throws IOException {
      // We cannot call the protected method, this should be effectively equivalent.
      delegate.close();
    }
  }

  /** The 2 open methods are present to be retargeted from FileChannel#open. */
  public static FileChannel open(Path path, OpenOption... openOptions) throws IOException {
    Set<OpenOption> openOptionSet = new HashSet<>();
    Collections.addAll(openOptionSet, openOptions);
    return open(path, openOptionSet);
  }

  public static FileChannel open(
      Path path, Set<? extends OpenOption> openOptions, FileAttribute<?>... attrs)
      throws IOException {
    if (AndroidVersionTest.is26OrAbove) {
      return FileChannel.open(path, openOptions, attrs);
    }
    return openEmulatedFileChannel(path, openOptions, attrs);
  }

  /**
   * All FileChannel creation go through the FileSystemProvider which then comes here if the Api is
   * strictly below 26, and to the plaform FileSystemProvider if the Api is above or equal to 26.
   *
   * <p>Below Api 26 there is no way to create a FileChannel, so we create instead an emulated
   * version using RandomAccessFile which tries, with a best effort, to support all settings.
   *
   * <p>The FileAttributes are ignored.
   */
  public static FileChannel openEmulatedFileChannel(
      Path path, Set<? extends OpenOption> openOptions, FileAttribute<?>... attrs)
      throws IOException {

    validateOpenOptions(path, openOptions);

    RandomAccessFile randomAccessFile =
        new RandomAccessFile(path.toFile(), getFileAccessModeText(openOptions));
    if (openOptions.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
      randomAccessFile.setLength(0);
    }

    if (!openOptions.contains(StandardOpenOption.APPEND)) {
      // This one may be retargeted, below 24, to support SeekableByteChannel.
      return randomAccessFile.getChannel();
    }

    // TODO(b/259056135): Consider subclassing UnsupportedOperationException for desugared library.
    // RandomAccessFile does not support APPEND.
    // We could hack a wrapper to support APPEND in simple cases such as Files.write().
    throw new UnsupportedOperationException();
  }

  private static void validateOpenOptions(Path path, Set<? extends OpenOption> openOptions)
      throws NoSuchFileException {
    // Validations that resemble sun.nio.fs.UnixChannelFactory#newFileChannel.
    if (openOptions.contains(StandardOpenOption.READ)
        && openOptions.contains(StandardOpenOption.APPEND)) {
      throw new IllegalArgumentException("READ + APPEND not allowed");
    }
    if (openOptions.contains(StandardOpenOption.APPEND)
        && openOptions.contains(StandardOpenOption.TRUNCATE_EXISTING)) {
      throw new IllegalArgumentException("APPEND + TRUNCATE_EXISTING not allowed");
    }
    if (openOptions.contains(StandardOpenOption.APPEND) && !path.toFile().exists()) {
      throw new NoSuchFileException(path.toString());
    }
  }

  private static String getFileAccessModeText(Set<? extends OpenOption> options) {
    if (!options.contains(StandardOpenOption.WRITE)) {
      return "r";
    }
    if (options.contains(StandardOpenOption.SYNC)) {
      return "rws";
    }
    if (options.contains(StandardOpenOption.DSYNC)) {
      return "rwd";
    }
    return "rw";
  }
}
