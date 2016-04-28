package edu.usm.cs.sparkMLTweet

import java.io.File

//import com.google.common.base.CharMatcher
import com.google.gson.Gson
import org.apache.spark.streaming.twitter.TwitterUtils
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.storage.StorageLevel

/**
 * Collect at least the specified number of tweets into json text files.
 */
object TweetCollect {
  private var numTweetsCollected = 0L
  private var partNum = 0
  private var gson = new Gson()

  def main(args: Array[String]) {
    // Process program arguments and set properties
    if (args.length < 3) {
      System.err.println("Usage: " + this.getClass.getSimpleName +
        "<outputDirectory> <numTweetsToCollect> <intervalInSeconds> <partitionsEachInterval>")
      System.exit(1)
    }
    val Array(outputDirectory, Utils.IntParam(numTweetsToCollect),  Utils.IntParam(intervalSecs), Utils.IntParam(partitionsEachInterval)) =
      Utils.parseCommandLineWithTwitterCredentials(args)
    val outputDir = new File(outputDirectory.toString)
    if (outputDir.exists()) {
      System.err.println("ERROR - %s already exists: delete or specify another directory".format(
        outputDirectory))
      System.exit(1)
    }
    outputDir.mkdirs()


    println("Initializing Streaming Spark Context...")
    val conf = new SparkConf().setAppName(this.getClass.getSimpleName)
    val sc = new SparkContext(conf)
    val ssc = new StreamingContext(sc, Seconds(intervalSecs))

    // start twitter streaming and filter tweets so that we will only have english tweets
    val tweetStream = TwitterUtils.createStream(ssc, Utils.getAuth)
      .filter{status => Option(status.getUser).flatMap[String] {u => Option(u.getLang)}
        .getOrElse("").startsWith("en")}
        .map(gson.toJson(_))  //&& CharMatcher.ASCII.matchesAllOf(status.getText)
      .persist(StorageLevel.MEMORY_AND_DISK_SER)

    /*val tweets = tweetStream.map(status => {(
      status.getUser.getName,
      status.getCreatedAt.toString,
      status.getUser.getLang,
      status.getText,
      Option(status.getGeoLocation).map{_.getLatitude}.getOrElse(0.0),
      Option(status.getGeoLocation).map{_.getLongitude}.getOrElse(0.0)
      )}).map(gson.toJson(_)).persist(StorageLevel.MEMORY_AND_DISK_SER)*/

   //persist tweets
    tweetStream.foreachRDD((rdd, time) => {
      val count = rdd.count()
      if (count > 0) {
        val outputRDD = rdd.repartition(partitionsEachInterval)
        outputRDD.saveAsTextFile(outputDirectory + "/tweets_" + time.milliseconds.toString)
        numTweetsCollected += count
        if (numTweetsCollected > numTweetsToCollect) {
          System.exit(0)
        }
      }
    })

    ssc.start()
    ssc.awaitTermination()
  }
}
