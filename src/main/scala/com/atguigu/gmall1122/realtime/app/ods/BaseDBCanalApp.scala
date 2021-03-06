package com.atguigu.gmall1122.realtime.app.ods

import com.alibaba.fastjson.{JSON, JSONArray, JSONObject}
import com.atguigu.gmall1122.realtime.util.{MyKafkaSink, MyKafkaUtil, OffsetManager}
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object BaseDBCanalApp {

  def main(args: Array[String]): Unit = {

    val sparkConf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("ods_base_db_canal_app")

    val ssc = new StreamingContext(sparkConf,Seconds(5))
    val topic ="ODS_DB_GMALL1122_C";
    val groupId="base_db_canal_group"

    val offset: Map[TopicPartition, Long] = OffsetManager.getOffset(groupId,topic)

    var inputDstream: InputDStream[ConsumerRecord[String, String]]=null
    // 判断如果从redis中读取当前最新偏移量 则用该偏移量加载kafka中的数据  否则直接用kafka读出默认最新的数据
    if(offset!=null&&offset.size>0){
      inputDstream = MyKafkaUtil.getKafkaStream(topic,ssc,offset,groupId)
      //startInputDstream.map(_.value).print(1000)
    }else{
      inputDstream  = MyKafkaUtil.getKafkaStream(topic,ssc,groupId)
    }

    //取得偏移量步长
    var offsetRanges: Array[OffsetRange] =null
    val inputGetOffsetDstream: DStream[ConsumerRecord[String, String]] = inputDstream.transform { rdd =>
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    }

    val dbJsonObjDstream: DStream[JSONObject] = inputGetOffsetDstream.map { record =>
      val jsonString: String = record.value()
      val jsonObj: JSONObject = JSON.parseObject(jsonString)
      jsonObj

    }
    
    dbJsonObjDstream.foreachRDD{rdd=>
      rdd.foreachPartition{jsonObjItr=>

        for (jsonObj <- jsonObjItr ) {

          val dataArr: JSONArray = jsonObj.getJSONArray("data")

          for (i <- 0 to dataArr.size()-1 ) {
            val dataJsonObj: JSONObject = dataArr.getJSONObject(i)
            val topic="ODS_T_"+jsonObj.getString("table").toUpperCase
            val id: String = dataJsonObj.getString("id")
             //println(topic+":"+dataJsonObj.toJSONString)
             MyKafkaSink.send(topic,id,dataJsonObj.toJSONString)
          }
        }
      }
      OffsetManager.saveOffset(groupId,topic,offsetRanges)
      
    }
    ssc.start()
    ssc.awaitTermination()


  }

}
