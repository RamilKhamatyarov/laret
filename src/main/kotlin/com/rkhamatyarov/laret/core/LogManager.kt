package com.rkhamatyarov.laret.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import org.slf4j.LoggerFactory

/**
 * Manages logging behavior for CLI application
 */
class LogManager {
    fun disableLogging() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
        loggerContext?.getLogger(Logger.ROOT_LOGGER_NAME)?.level = Level.OFF
    }

    fun enableLogging() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
        loggerContext?.getLogger(Logger.ROOT_LOGGER_NAME)?.level = Level.INFO
    }

    fun setLogLevel(level: Level) {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext
        loggerContext?.getLogger(Logger.ROOT_LOGGER_NAME)?.level = level
    }
}
