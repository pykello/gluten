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
package io.glutenproject.expression

import io.glutenproject.expression.ConverterUtils.FunctionConfig
import io.glutenproject.substrait.expression.{ExpressionBuilder, ExpressionNode}

import org.apache.spark.internal.Logging
import org.apache.spark.sql.catalyst.expressions.GetStructField
import org.apache.spark.sql.types.{IntegerType, StringType, StructType}

import com.google.common.collect.Lists

class GetStructFieldTransformer(
    substraitExprName: String,
    childTransformer: ExpressionTransformer,
    ordinal: Int,
    original: GetStructField)
  extends ExpressionTransformer
  with Logging {

  override def doTransform(args: Object): ExpressionNode = {
    val childNode = childTransformer.doTransform(args)
    // FIXME: Not sure which argument type velox support. ClickHouse supports both field name
    // and field ordinal. Since ClickHouse require the ordinal argument must be an unsigned
    // integer, but substrait doesn't support unsigned types, so we choose to use field name
    // here.
    val fieldName = original.child.dataType.asInstanceOf[StructType].names(ordinal)
    if (fieldName.isEmpty()) {
      throw new IllegalArgumentException(s"Field name should not be null.")
    }
    val ordinalNode = ExpressionBuilder.makeLiteral(fieldName, StringType, false)
    val exprNodes = Lists.newArrayList(childNode, ordinalNode)
    val functionMap = args.asInstanceOf[java.util.HashMap[String, java.lang.Long]]
    val fieldDataType = original.dataType
    val functionName = ConverterUtils.makeFuncName(
      substraitExprName,
      Seq(original.child.dataType, fieldDataType),
      FunctionConfig.OPT)
    val functionId = ExpressionBuilder.newScalarFunction(functionMap, functionName)
    val typeNode = ConverterUtils.getTypeNode(original.dataType, original.nullable)
    ExpressionBuilder.makeScalarFunction(functionId, exprNodes, typeNode)
  }
}
