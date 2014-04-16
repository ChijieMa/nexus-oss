package org.sonatype.nexus.blobstore.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;

import org.sonatype.nexus.util.DigesterUtils;

import org.apache.commons.io.IOUtils;

/**
 * @since 3.0
 */
public class SimpleFileOperations
    implements FileOperations
{
  @Override
  public void create(final Path path, final InputStream data) throws IOException {
    ensureDirectoryExists(path.getParent());

    try (final OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)) {
      IOUtils.copy(data, outputStream);
      data.close();
    }
  }

  @Override
  public void create(final Path path, final byte[] data) throws IOException {
    ensureDirectoryExists(path.getParent());
    Files.write(path, data, StandardOpenOption.CREATE_NEW);
  }

  @Override
  public boolean exists(final Path path) {
    return Files.exists(path);
  }

  @Override
  public InputStream openInputStream(final Path path) throws IOException {
    return Files.newInputStream(path, StandardOpenOption.READ);
  }

  @Override
  public Date fileCreationDate(final Path path) throws IOException {
    BasicFileAttributes attr = Files.readAttributes(path, BasicFileAttributes.class);
    return new Date(attr.creationTime().toMillis());
  }

  @Override
  public String computeSha1Hash(final Path path) throws IOException {
    try (InputStream inputStream = openInputStream(path)) {
      return DigesterUtils.getSha1Digest(inputStream);
    }
  }

  @Override
  public boolean delete(final Path path) throws IOException {
    try {
      Files.delete(path);
      return true;
    }
    catch (NoSuchFileException e) {
      return false;
    }
  }

  @Override
  public long fileSize(final Path path) throws IOException {
    return Files.size(path);
  }

  /**
   * Creates directories if they don't exist.  This method is synchronized as a simple way of ensuring that multiple
   * threads aren't fighting to create the same directories at the same time.
   */
  private synchronized void ensureDirectoryExists(final Path directory) throws IOException {
    if (!exists(directory)) {
      Files.createDirectories(directory);
    }
  }
}
