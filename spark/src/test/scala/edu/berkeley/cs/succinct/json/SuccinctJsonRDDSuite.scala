package edu.berkeley.cs.succinct.json

import java.io.IOException

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.google.common.io.Files
import edu.berkeley.cs.succinct.LocalSparkContext
import org.apache.spark.SparkContext
import org.apache.spark.storage.StorageLevel
import org.scalatest.FunSuite

import scala.util.Random

class SuccinctJsonRDDSuite extends FunSuite with LocalSparkContext {

  def genId(max: Int): Long = Math.abs(new Random().nextInt(max))

  @throws(classOf[IOException])
  def assertJsonEquals(json1: String, json2: String) {
    val mapper = new ObjectMapper
    val tree1: JsonNode = mapper.readTree(json1)
    val tree2: JsonNode = mapper.readTree(json2)
    assert(tree1 === tree2)
  }

  test("get") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile)

    val succinctJsonRDD = SuccinctJsonRDD(jsonRDD)
    val jsonList = jsonRDD.collect()

    // Check
    (0 to jsonList.length).foreach(i => {
      assertJsonEquals(jsonList(i), succinctJsonRDD.get(i))
    })
  }

  test("filter") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile)

    val succinctJsonRDD = SuccinctJsonRDD(jsonRDD)

    // Check
    val res1 = succinctJsonRDD.filter("name", "Cookie Monster").collect()
    assert(Array(1L) sameElements res1)

    val res2 = succinctJsonRDD.filter("name.first", "Charles").collect()
    assert(Array(4L) sameElements res2)

    val res3 = succinctJsonRDD.filter("name.last", "Baggins").collect()
    assert(Array(2L, 3L) sameElements res3)

    val res4 = succinctJsonRDD.filter("additional.professional.skills", "telekinesis").collect()
    assert(Array(4L) sameElements res4)

    val res5 = succinctJsonRDD.filter("name", "Darth Vader").collect()
    assert(res5.isEmpty)
  }

  test("search") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile)

    val succinctJsonRDD = SuccinctJsonRDD(jsonRDD)

    // Check
    val res1 = succinctJsonRDD.search("Monster").collect()
    assert(Array(1L) sameElements res1)

    val res2 = succinctJsonRDD.search("Charles").collect()
    assert(Array(4L) sameElements res2)

    val res3 = succinctJsonRDD.search("Baggins").collect()
    assert(Array(2L, 3L) sameElements res3)

    val res4 = succinctJsonRDD.search("telekinesis").collect()
    assert(Array(4L) sameElements res4)

    val res5 = succinctJsonRDD.search("Darth").collect()
    assert(res5.isEmpty)
  }

  test("multiple partitions") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile).repartition(5)

    val succinctJsonRDD = SuccinctJsonRDD(jsonRDD)
    val jsonList = jsonRDD.collect()

    // Check get
    (0 to jsonList.length).foreach(i => {
      assertJsonEquals(jsonList(i), succinctJsonRDD.get(i))
    })

    // Check filter
    val f1 = succinctJsonRDD.filter("name", "Cookie Monster").collect()
    assert(Array(1L) sameElements f1)

    val f2 = succinctJsonRDD.filter("name.first", "Charles").collect()
    assert(Array(4L) sameElements f2)

    val f3 = succinctJsonRDD.filter("name.last", "Baggins").collect()
    assert(Array(2L, 3L) sameElements f3)

    val f4 = succinctJsonRDD.filter("additional.professional.skills", "telekinesis").collect()
    assert(Array(4L) sameElements f4)

    val f5 = succinctJsonRDD.filter("name", "Darth Vader").collect()
    assert(f5.isEmpty)

    // Check search
    val s1 = succinctJsonRDD.search("Monster").collect()
    assert(Array(1L) sameElements s1)

    val s2 = succinctJsonRDD.search("Charles").collect()
    assert(Array(4L) sameElements s2)

    val s3 = succinctJsonRDD.search("Baggins").collect()
    assert(Array(2L, 3L) sameElements s3)

    val s4 = succinctJsonRDD.search("telekinesis").collect()
    assert(Array(4L) sameElements s4)

    val s5 = succinctJsonRDD.search("Darth").collect()
    assert(s5.isEmpty)

  }

  test("save and load in memory") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile).repartition(5)
    val succinctJsonRDD = SuccinctJsonRDD(jsonRDD)

    val tmpDir = Files.createTempDir()
    val succinctDir = tmpDir + "/succinct"
    succinctJsonRDD.save(succinctDir)

    val reloadedRDD = SuccinctJsonRDD(sc, succinctDir, StorageLevel.MEMORY_ONLY)

    val originalJson = succinctJsonRDD.collect()
    val newJson = reloadedRDD.collect()

    assert(originalJson === newJson)
  }

  test("save and load in memory 2") {
    sc = new SparkContext("local", "test")

    val jsonRDD = sc.textFile(getClass.getResource("/people.json").getFile).repartition(5)
    val succinctJsonRDD = jsonRDD.succinctJson

    val tmpDir = Files.createTempDir()
    val succinctDir = tmpDir + "/succinct"
    succinctJsonRDD.save(succinctDir)

    val reloadedRDD = sc.succinctJson(succinctDir)

    val originalJson = succinctJsonRDD.collect()
    val newJson = reloadedRDD.collect()

    assert(originalJson === newJson)
  }
}
