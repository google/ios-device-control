// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.iosdevicecontrol.util;

import java.util.Optional;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class for a fluent logger. Used as a factory of instances to build log statements via method
 * chaining.
 */
public final class FluentLogger {
  /** Returns a generic fluent logger for a class. */
  public static FluentLogger forEnclosingClass() {
    StackTraceElement caller = new Throwable().getStackTrace()[1];
    Logger logger = Logger.getLogger(caller.getClassName());
    logger.setUseParentHandlers(false);
    logger.addHandler(new ConsoleHandler());
    return new FluentLogger(logger, Level.OFF, Optional.empty());
  }

  private final Logger logger;
  private final Level level;
  private final Optional<Throwable> cause;

  private FluentLogger(Logger logger, Level level, Optional<Throwable> cause) {
    this.level = level;
    this.logger = logger;
    this.cause = cause;
  }

  /** Returns a fluent logger with the specified cause. */
  public FluentLogger withCause(Throwable cause) {
    return new FluentLogger(logger, level, Optional.of(cause));
  }

  /** Convenience method for at({@link Level#INFO}). */
  public FluentLogger atInfo() {
    return at(Level.INFO);
  }

  /** Convenience method for at({@link Level#SEVERE}). */
  public FluentLogger atSevere() {
    return at(Level.SEVERE);
  }

  /** Convenience method for at({@link Level#WARNING}). */
  public FluentLogger atWarning() {
    return at(Level.WARNING);
  }

  /** Convenience method for at({@link Level#FINE}). */
  public FluentLogger atFine() {
    return at(Level.FINE);
  }

  /** Returns a fluent logger with the specified logging level. */
  public FluentLogger at(Level level) {
    return new FluentLogger(logger, level, cause);
  }

  /** Print a log of the {@link FluentLogger#cause}. */
  public void log() {
    log("");
  }

  /** Print a formatted log message. */
  public void log(String message, Object... params) {
    StackTraceElement caller = new Throwable().getStackTrace()[1];
    String formatMsg = String.format(message, params);
    if (cause.isPresent()) {
      logger.logp(level, logger.getName(), caller.getMethodName(), formatMsg, cause.get());
    } else {
      logger.logp(level, logger.getName(), caller.getMethodName(), formatMsg);
    }
  }
}
