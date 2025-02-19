/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.glutenproject.execution

import java.nio.file.Files

import org.apache.spark.SparkConf
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.optimizer.{ConstantFolding, NullPropagation}
import org.apache.spark.sql.types._

import scala.collection.JavaConverters._

class VeloxFunctionsValidateSuite extends WholeStageTransformerSuite {

  override protected val resourcePath: String = "/tpch-data-parquet-velox"
  override protected val fileFormat: String = "parquet"
  override protected val backend: String = "velox"

  private var parquetPath: String = _

  override protected def sparkConf: SparkConf = {
    super.sparkConf
      .set("spark.shuffle.manager", "org.apache.spark.shuffle.sort.ColumnarShuffleManager")
      .set("spark.sql.files.maxPartitionBytes", "1g")
      .set("spark.sql.shuffle.partitions", "1")
      .set("spark.memory.offHeap.size", "2g")
      .set("spark.unsafe.exceptionOnMemoryLeak", "false")
      .set("spark.sql.autoBroadcastJoinThreshold", "-1")
      .set("spark.sql.sources.useV1SourceList", "avro")
      .set("spark.sql.optimizer.excludedRules", ConstantFolding.ruleName + "," +
        NullPropagation.ruleName)
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    createTPCHNotNullTables()

    val lfile = Files.createTempFile("", ".parquet").toFile
    lfile.deleteOnExit()
    parquetPath = lfile.getAbsolutePath

    val schema = StructType(Array(
      StructField("double_field1", DoubleType, true),
      StructField("int_field1", IntegerType, true),
      StructField("string_field1", StringType, true)
    ))
    val rowData = Seq(
      Row(1.025, 1, "{\"a\":\"b\"}"),
      Row(1.035, 2, null),
      Row(1.045, 3, null)
    )

    var dfParquet = spark.createDataFrame(rowData.asJava, schema)
    dfParquet.coalesce(1)
      .write
      .format("parquet")
      .mode("overwrite")
      .parquet(parquetPath)

    spark.catalog.createTable("datatab", parquetPath, fileFormat)
  }

  test("Test chr function") {
    runQueryAndCompare("SELECT chr(l_orderkey + 64) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test abs function") {
    runQueryAndCompare("SELECT abs(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test ceil function") {
    runQueryAndCompare("SELECT ceil(cast(l_orderkey as long)) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test floor function") {
    runQueryAndCompare("SELECT floor(cast(l_orderkey as long)) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test Exp function") {
    runQueryAndCompare("SELECT exp(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test Power function") {
    runQueryAndCompare("SELECT power(l_orderkey, 2) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test Pmod function") {
    runQueryAndCompare("SELECT pmod(cast(l_orderkey as int), 3) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  ignore("Test round function") {
    runQueryAndCompare("SELECT round(cast(l_orderkey as int), 2)" +
      "from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test greatest function") {
    runQueryAndCompare("SELECT greatest(l_orderkey, l_orderkey)" +
      "from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test least function") {
    runQueryAndCompare("SELECT least(l_orderkey, l_orderkey)" +
      "from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test hash function") {
    runQueryAndCompare("SELECT hash(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test get_json_object datatab function") {
    runQueryAndCompare("SELECT get_json_object(string_field1, '$.a') " +
      "from datatab limit 1;") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test get_json_object lineitem function") {
    runQueryAndCompare("SELECT l_orderkey, get_json_object('{\"a\":\"b\"}', '$.a') " +
      "from lineitem limit 1;") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  ignore("json_array_length") {
    runQueryAndCompare(s"select *, json_array_length(string_field1) " +
      s"from datatab limit 5") { checkOperatorMatch[ProjectExecTransformer] }
    runQueryAndCompare(s"select l_orderkey, json_array_length('[1,2,3,4]') " +
      s"from lineitem limit 5") { checkOperatorMatch[ProjectExecTransformer] }
    runQueryAndCompare(s"select l_orderkey, json_array_length(null) " +
      s"from lineitem limit 5") { checkOperatorMatch[ProjectExecTransformer] }
  }

  test("Test acos function") {
    runQueryAndCompare("SELECT acos(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test asin function") {
    runQueryAndCompare("SELECT asin(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test atan function") {
    runQueryAndCompare("SELECT atan(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  ignore("Test atan2 function datatab") {
    runQueryAndCompare("SELECT atan2(double_field1, 0) from datatab limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test ceiling function") {
    runQueryAndCompare("SELECT ceiling(cast(l_orderkey as long)) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test cos function") {
    runQueryAndCompare("SELECT cos(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test cosh function") {
    runQueryAndCompare("SELECT cosh(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test degrees function") {
    runQueryAndCompare("SELECT degrees(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test log10 function") {
    runQueryAndCompare("SELECT log10(l_orderkey) from lineitem limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test shiftleft function") {
    val df = runQueryAndCompare("SELECT shiftleft(int_field1, 1) from datatab limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }

  test("Test shiftright function") {
    val df = runQueryAndCompare("SELECT shiftright(int_field1, 1) from datatab limit 1") {
      checkOperatorMatch[ProjectExecTransformer]
    }
  }
}
