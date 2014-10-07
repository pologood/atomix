/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * State machine command annotation.<p>
 *
 * This annotation is used to identify commands within a {@link net.kuujo.copycat.StateMachine} implementation. Copycat
 * uses the {@code Command} annotation to locate command methods and type information.<p>
 *
 * State machine commands can be one of three types: {@code READ}, {@code WRITE}, or {@code READ_WRITE}, and
 * Copycat's behavior is altered by the type of a given command. Command types are identified by a
 * {@link net.kuujo.copycat.Command.Type} enum value of the {@code type} field of this annotation. By default, all
 * commands are {@code READ_WRITE} unless otherwise specified. However, it is highly recommended that command types be
 * explicitly annotated. Copycat can provide significant performance optimizations given proper type information. For
 * instance, for read-only commands, Copycat will forgo unnecessary replication of command submissions.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

  /**
   * The command name.
   */
  String name() default "";

  /**
   * The command type.
   */
  Type type() default Type.READ_WRITE;

  /**
   * State machine command type.
   *
   * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
   */
  public static enum Type {

    /**
     * A read-only command.
     */
    READ("read"),

    /**
     * A write-only command.
     */
    WRITE("write"),

    /**
     * A read/write command.
     */
    READ_WRITE("read-write");

    private final String name;

    private Type(String name) {
      this.name = name;
    }

    /**
     * Returns the command name.
     *
     * @return The command name.
     */
    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return getName();
    }

  }

}
