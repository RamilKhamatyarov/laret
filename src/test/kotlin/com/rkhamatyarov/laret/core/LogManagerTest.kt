package com.rkhamatyarov.laret.core

import ch.qos.logback.classic.Level
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LogManagerTest {
    private lateinit var logManager: LogManager

    @Before
    fun setup() {
        logManager = LogManager()
    }

    @Test
    fun `disableLogging sets log level to OFF`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.disableLogging()

        // then
        assertEquals(Level.OFF, rootLogger.level)
    }

    @Test
    fun `enableLogging sets log level to INFO`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // First disable logging
        logManager.disableLogging()
        assertEquals(Level.OFF, rootLogger.level, "Log level should be OFF")

        // when
        logManager.enableLogging()

        // then
        assertEquals(Level.INFO, rootLogger.level)
    }

    @Test
    fun `setLogLevel sets custom log level`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.setLogLevel(Level.DEBUG)

        // then
        assertEquals(Level.DEBUG, rootLogger.level)
    }

    @Test
    fun `setLogLevel with WARN level`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.setLogLevel(Level.WARN)

        // then
        assertEquals(Level.WARN, rootLogger.level)
    }

    @Test
    fun `setLogLevel with ERROR level`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.setLogLevel(Level.ERROR)

        // then
        assertEquals(Level.ERROR, rootLogger.level)
    }

    @Test
    fun `disableLogging and then enableLogging restores INFO level`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.disableLogging()
        assertEquals(Level.OFF, rootLogger.level, "Log level should be OFF")

        logManager.enableLogging()

        // then
        assertEquals(Level.INFO, rootLogger.level)
    }

    @Test
    fun `multiple setLogLevel calls last one wins`() {
        // given
        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager.setLogLevel(Level.DEBUG)
        assertEquals(Level.DEBUG, rootLogger.level)

        logManager.setLogLevel(Level.ERROR)

        // then
        assertEquals(Level.ERROR, rootLogger.level)
    }

    @Test
    fun `logManager can be instantiated multiple times independently`() {
        // given
        val logManager1 = LogManager()
        val logManager2 = LogManager()

        val loggerContext = LoggerFactory.getILoggerFactory() as? ch.qos.logback.classic.LoggerContext
        assertNotNull(loggerContext, "LoggerContext should not be null")

        val rootLogger = loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)
        assertNotNull(rootLogger, "Root logger should not be null")

        // when
        logManager1.disableLogging()
        assertEquals(Level.OFF, rootLogger.level)

        logManager2.enableLogging()

        // then
        assertEquals(Level.INFO, rootLogger.level)
    }
}
