package com.holdenkarau.predict.pr.comments.sparkProject

/**
 * A simple test for fetching github patches
 */

import com.holdenkarau.spark.testing.{SharedSparkContext, Utils}
import org.apache.spark.sql._
import org.scalatest.FunSuite
import org.scalatest.Matchers._

class DataFetchTest extends FunSuite with SharedSparkContext {
  val standardInputList = List(
    "pull_request_url,pull_patch_url,comments_positions_space_delimited,comments_original_positions_space_delimited",
    "https://api.github.com/repos/mick-warehime/sixth_corp/pulls/61,https://github.com/mick-warehime/sixth_corp/pull/61.patch,4 37 35 35 38 4 37 35 38,4 37 35 35 38 4 37 35 38")


  test("calling with a local file fetches a result") {
    val session = SparkSession.builder().getOrCreate()
    import session.implicits._
    val inputRDD = sc.parallelize(standardInputList)
    val inputData = session.read.option("header", "true").csv(session.createDataset(inputRDD)).as[InputData]
    val cachedData = session.emptyDataset[StoredPatch]
    val dataFetch = new DataFetch(sc)
    val result = dataFetch.fetchPatches(inputData, cachedData)
    result.count() should be (1)
  }

  test("cache records are filtered out") {
    val session = SparkSession.builder().getOrCreate()
    import session.implicits._
    val inputRDD = sc.parallelize(standardInputList)
    val inputData = session.read.option("header", "true").csv(session.createDataset(inputRDD)).as[InputData]
    val basicCached = StoredPatch(
      "https://api.github.com/repos/mick-warehime/sixth_corp/pulls/61",
      "notreal",
      "stillnotreal")
    val cachedData = session.createDataset(sc.parallelize(List(basicCached)))
    val dataFetch = new DataFetch(sc)
    val result = dataFetch.fetchPatches(inputData, cachedData)
    result.count() should be (0)
  }

  test("test the main entry point - no cache") {
    val tempDir = Utils.createTempDir()
    val tempPath = tempDir.toPath().toAbsolutePath().toString()
    val session = SparkSession.builder().getOrCreate()
    import session.implicits._
    val inputRDD = sc.parallelize(standardInputList, 1)
    val inputPath = s"$tempPath/input.csv"
    val outputPath = s"$tempPath/output.csv"
    inputRDD.saveAsTextFile(inputPath)
    val dataFetch = new DataFetch(sc)
    dataFetch.fetch(inputPath, outputPath, None)
    val result = session.read.format("parquet").load(outputPath)
    result.count() should be (1)
  }

  test("test the main entry point - with cache") {
    val tempDir = Utils.createTempDir()
    val tempPath = tempDir.toPath().toAbsolutePath().toString()
    val session = SparkSession.builder().getOrCreate()
    import session.implicits._
    val inputRDD = sc.parallelize(standardInputList, 1)
    val inputPath = s"$tempPath/input.csv"
    val outputPath = s"$tempPath/output.csv"
    val cachePath = s"$tempPath/cache"
    inputRDD.saveAsTextFile(inputPath)
    val dataFetch = new DataFetch(sc)
    dataFetch.fetch(inputPath, outputPath, Some(cachePath))
    dataFetch.fetch(inputPath, outputPath, Some(cachePath))
    val result = session.read.format("parquet").load(outputPath)
    result.count() should be (1)
    result.as[ResultData].collect()(0).patch should include ("Subject: [PATCH")
    result.as[ResultData].collect()(0).diff should include ("@@ -")
    result.as[ResultData].collect()(0).diff should not include ("Subject: [PATCH")
  }

}
