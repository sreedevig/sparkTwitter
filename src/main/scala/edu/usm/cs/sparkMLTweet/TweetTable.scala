package edu.usm.cs.sparkMLTweet

import com.google.gson.{GsonBuilder, JsonParser}
import org.apache.spark.sql.SQLContext
import org.apache.spark.{SparkConf, SparkContext}

object TweetTable {
  val jsonParser = new JsonParser()
  val gson = new GsonBuilder().setPrettyPrinting().create()

  def main(args: Array[String]) {
    // Process program arguments and set properties
    if (args.length < 2) {
      System.err.println("Usage: " + this.getClass.getSimpleName +
        " <tweetInput> <outputFile>")
      System.exit(1)
    }
    val Array(tweetInput, outputFile) = args

    val conf = new SparkConf().setAppName(this.getClass.getSimpleName)
    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)

    // Pretty print some of the tweets.
    val tweets = sc.textFile(tweetInput)
    println("------------Sample JSON Tweets-------")
    for (tweet <- tweets.take(5)) {
      println(gson.toJson(jsonParser.parse(tweet)))
    }

    val tweetTable = sqlContext.read.json(tweetInput).cache()
    tweetTable.registerTempTable("tweetTable")

    println("------Tweet table Schema---")
    tweetTable.printSchema()

    /*println("----Sample Tweet Text-----")
    sqlContext.sql("SELECT text FROM tweetTable LIMIT 10").collect().foreach(println)

    println("------Sample Lang, Name, text---")
    sqlContext.sql("SELECT user.lang, user.name, text FROM tweetTable LIMIT 1000").collect().foreach(println)*/

    sqlContext.sql("SELECT user.name, createdAt, text FROM tweetTable").toDF().write.format("com.databricks.spark.csv").save(outputFile)
  }
}
