package com.cds.utils.es.io;

import java.io.IOException;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable.Reader;

/**
 * Created by chendongsheng5 on 2017/5/22.
 */
public interface Writeable {

  /**
   * Write this into the {@linkplain StreamOutput}.
   */
  void writeTo(StreamOutput out) throws IOException;

  /**
   * Reference to a method that can write some object to a {@link StreamOutput}.
   * <p>
   * By convention this is a method from {@link StreamOutput} itself (e.g., {@link StreamOutput#writeString}). If the value can be
   * {@code null}, then the "optional" variant of methods should be used!
   * <p>
   * Most classes should implement {@link org.elasticsearch.common.io.stream.Writeable} and the {@link org.elasticsearch.common.io.stream.Writeable#writeTo(StreamOutput)} method should <em>use</em>
   * {@link StreamOutput} methods directly or this indirectly:
   * <pre><code>
   * public void writeTo(StreamOutput out) throws IOException {
   *     out.writeVInt(someValue);
   *     out.writeMapOfLists(someMap, StreamOutput::writeString, StreamOutput::writeString);
   * }
   * </code></pre>
   */
  @FunctionalInterface
  interface Writer<V> {

    /**
     * Write {@code V}-type {@code value} to the {@code out}put stream.
     *
     * @param out Output to write the {@code value} too
     * @param value The value to add
     */
    void write(StreamOutput out, V value) throws IOException;

  }

  /**
   * Reference to a method that can read some object from a stream. By convention this is a constructor that takes
   * {@linkplain StreamInput} as an argument for most classes and a static method for things like enums. Returning null from one of these
   * is always wrong - for that we use methods like {@link StreamInput#readOptionalWriteable(org.elasticsearch.common.io.stream.Writeable.Reader)}.
   * <p>
   * As most classes will implement this via a constructor (or a static method in the case of enumerations), it's something that should
   * look like:
   * <pre><code>
   * public MyClass(final StreamInput in) throws IOException {
   *     this.someValue = in.readVInt();
   *     this.someMap = in.readMapOfLists(StreamInput::readString, StreamInput::readString);
   * }
   * </code></pre>
   */
  @FunctionalInterface
  interface Reader<V> {

    /**
     * Read {@code V}-type value from a stream.
     *
     * @param in Input to read the value from
     */
    V read(StreamInput in) throws IOException;

  }

}
