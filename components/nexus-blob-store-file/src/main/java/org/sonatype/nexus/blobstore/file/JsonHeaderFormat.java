package org.sonatype.nexus.blobstore.file;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Reads and writes blob headers as UTF8-encoded JSON.
 *
 * @since 3.0
 */
public class JsonHeaderFormat
    implements HeaderFileFormat
{
  private static final Charset HEADER_FILE_ENCODING = Charset.forName("UTF-8");

  @Override
  public void write(final Map<String, String> headers, final OutputStream outputStream) throws IOException {
    writeString(outputStream, new JSONObject(headers).toString());
  }


  @Override
  public Map<String, String> read(final InputStream inputStream) throws IOException {
    StringWriter writer = new StringWriter();
    IOUtils.copy(inputStream, writer, Charset.forName("UTF-8"));

    try {
      return toMap(new JSONObject(writer.toString()));
    }
    catch (JSONException e) {
      throw new IOException(e);
    }
  }

  private Map<String, String> toMap(final JSONObject json) throws JSONException {
    Map<String, String> map = new HashMap<String, String>();
    for (Iterator<String> it = json.keys(); it.hasNext(); ) {
      final String key = it.next();
      map.put(key, json.getString(key));
    }
    return map;
  }

  private void writeString(final OutputStream outputStream, final String string) throws IOException {
    BufferedWriter b = new BufferedWriter(new OutputStreamWriter(outputStream, Charset.forName("UTF-8")));
    b.write(string);
    b.flush();
    // Don't close the stream, that's done elsewhere
  }
}
