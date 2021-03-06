package com.cds.utils.common

import java.io.{File, IOException}
import java.nio.file.{Files, Paths}

import scala.collection.mutable.ArrayBuffer
import scala.util.control.ControlThrowable

/**
  * Created by chendongsheng5 on 2017/6/12.
  */
/** CallSite represents a place in user code. It can have a short and a long form. */
private[common] case class CallSite(shortForm: String, longForm: String)

private[common] object CallSite {
  val SHORT_FORM = "callSite.short"
  val LONG_FORM = "callSite.long"
  val empty = CallSite("", "")
}

object Utils extends Logging {

  /**
    * Define a default value for driver memory here since this value is referenced across the code
    * base and nearly all files already use Utils.scala
    */
  val DEFAULT_DRIVER_MEM_MB = JavaUtils.DEFAULT_DRIVER_MEM_MB.toInt

  private val MAX_DIR_CREATION_ATTEMPTS: Int = 10
  @volatile private var localRootDirs: Array[String] = null


  /**
    * When called inside a class in the spark package, returns the name of the user code class
    * (outside the spark package) that called into Spark, as well as which Spark method they called.
    * This is used, for example, to tell users where in their code each RDD got created.
    *
    * @param skipClass Function that is used to exclude non-user-code classes.
    */
  def getCallSite(skipClass: String => Boolean = sparkInternalExclusionFunction): CallSite = {
    // Keep crawling up the stack trace until we find the first function not inside of the spark
    // package. We track the last (shallowest) contiguous Spark method. This might be an RDD
    // transformation, a SparkContext function (such as parallelize), or anything else that leads
    // to instantiation of an RDD. We also track the first (deepest) user method, file, and line.
    var lastSparkMethod = "<unknown>"
    var firstUserFile = "<unknown>"
    var firstUserLine = 0
    var insideSpark = true
    var callStack = new ArrayBuffer[String]() :+ "<unknown>"

    Thread.currentThread.getStackTrace().foreach { ste: StackTraceElement =>
      // When running under some profilers, the current stack trace might contain some bogus
      // frames. This is intended to ensure that we don't crash in these situations by
      // ignoring any frames that we can't examine.
      if (ste != null && ste.getMethodName != null
        && !ste.getMethodName.contains("getStackTrace")) {
        if (insideSpark) {
          if (skipClass(ste.getClassName)) {
            lastSparkMethod = if (ste.getMethodName == "<init>") {
              // Spark method is a constructor; get its class name
              ste.getClassName.substring(ste.getClassName.lastIndexOf('.') + 1)
            } else {
              ste.getMethodName
            }
            callStack(0) = ste.toString // Put last Spark method on top of the stack trace.
          } else {
            if (ste.getFileName != null) {
              firstUserFile = ste.getFileName
              if (ste.getLineNumber >= 0) {
                firstUserLine = ste.getLineNumber
              }
            }
            callStack += ste.toString
            insideSpark = false
          }
        } else {
          callStack += ste.toString
        }
      }
    }
    val callStackDepth = System.getProperty("spark.callstack.depth", "20").toInt
    val shortForm =
      if (firstUserFile == "HiveSessionImpl.java") {
        // To be more user friendly, show a nicer string for queries submitted from the JDBC
        // server.
        "Spark JDBC Server Query"
      } else {
        s"$lastSparkMethod at $firstUserFile:$firstUserLine"
      }
    val longForm = callStack.take(callStackDepth).mkString("\n")

    CallSite(shortForm, longForm)
  }

  private def sparkInternalExclusionFunction(className: String): Boolean = {
    // A regular expression to match classes of the internal Spark API's
    // that we want to skip when finding the call site of a method.
    val SPARK_CORE_CLASS_REGEX =
    """^org\.apache\.spark(\.api\.java)?(\.util)?(\.rdd)?(\.broadcast)?\.[A-Z]""".r
    val SPARK_SQL_CLASS_REGEX = """^com\.cds\.utils\.common.*""".r
    val SCALA_CORE_CLASS_PREFIX = "scala"
    val isSparkClass = SPARK_CORE_CLASS_REGEX.findFirstIn(className).isDefined ||
      SPARK_SQL_CLASS_REGEX.findFirstIn(className).isDefined
    val isScalaClass = className.startsWith(SCALA_CORE_CLASS_PREFIX)
    // If the class is a Spark internal class or a Scala class, then exclude.
    isSparkClass || isScalaClass
  }

  /**
    * Execute the given block, logging and re-throwing any uncaught exception.
    * This is particularly useful for wrapping code that runs in a thread, to ensure
    * that exceptions are printed, and to avoid having to catch Throwable.
    */
  def logUncaughtExceptions[T](f: => T): T = {
    try {
      f
    } catch {
      case ct: ControlThrowable =>
        throw ct
      case t: Throwable =>
        logError(s"Uncaught exception in thread ${Thread.currentThread().getName}", t)
        throw t
    }
  }


  /**
    * Delete a file or directory and its contents recursively.
    * Don't follow directories if they are symlinks.
    * Throws an exception if deletion is unsuccessful.
    */
  def deleteRecursively(file: File) {
    if (file != null) {
      try {
        if (file.isDirectory && !isSymlink(file)) {
          var savedIOException: IOException = null
          for (child <- listFilesSafely(file)) {
            try {
              deleteRecursively(child)
            } catch {
              // In case of multiple exceptions, only last one will be thrown
              case ioe: IOException => savedIOException = ioe
            }
          }
          if (savedIOException != null) {
            throw savedIOException
          }
          ShutdownHookManager.removeShutdownDeleteDir(file)
        }
      } finally {
        if (!file.delete()) {
          // Delete can also fail if the file simply did not exist
          if (file.exists()) {
            throw new IOException("Failed to delete: " + file.getAbsolutePath)
          }
        }
      }
    }
  }

  /**
    * Check to see if file is a symbolic link.
    */
  def isSymlink(file: File): Boolean = {
    return Files.isSymbolicLink(Paths.get(file.toURI))
  }

  private def listFilesSafely(file: File): Seq[File] = {
    if (file.exists()) {
      val files = file.listFiles()
      if (files == null) {
        throw new IOException("Failed to list files for dir: " + file)
      }
      files
    } else {
      List()
    }
  }

  def main(args: Array[String]): Unit = {
    println("a")
    val creationSite: CallSite = getCallSite()
    println(creationSite.shortForm)
    println()
    println()
    println(creationSite.longForm)
  }
}
