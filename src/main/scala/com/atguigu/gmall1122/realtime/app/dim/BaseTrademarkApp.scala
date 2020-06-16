package com.atguigu.gmall1122.realtime.app.dim

import com.alibaba.fastjson.JSON
import com.atguigu.gmall1122.realtime.bean.dim._
import com.atguigu.gmall1122.realtime.util.{MyKafkaUtil, OffsetManager}
import org.apache.hadoop.conf.Configuration
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.spark.SparkConf
import org.apache.spark.streaming.dstream.{DStream, InputDStream}
import org.apache.spark.streaming.kafka010.{HasOffsetRanges, OffsetRange}
import org.apache.spark.streaming.{Seconds, StreamingContext}

object BaseTrademarkApp {

  def main(args: Array[String]): Unit = {

    val sparkConf: SparkConf = new SparkConf().setMaster("local[*]").setAppName("dim_base_trademark_app")

    val ssc = new StreamingContext(sparkConf, Seconds(5))
    val topic = "ODS_T_BASE_TRADEMARK";
    val groupId = "dim_base_trademark_group"


    /////////////////////  偏移量处理///////////////////////////
    val offset: Map[TopicPartition, Long] = OffsetManager.getOffset(groupId, topic)

    var inputDstream: InputDStream[ConsumerRecord[String, String]] = null
    // 判断如果从redis中读取当前最新偏移量 则用该偏移量加载kafka中的数据  否则直接用kafka读出默认最新的数据
    if (offset != null && offset.size > 0) {
      inputDstream = MyKafkaUtil.getKafkaStream(topic, ssc, offset, groupId)
    } else {
      inputDstream = MyKafkaUtil.getKafkaStream(topic, ssc, groupId)
    }

    //取得偏移量步长
    var offsetRanges: Array[OffsetRange] = null
    val inputGetOffsetDstream: DStream[ConsumerRecord[String, String]] = inputDstream.transform { rdd =>
      offsetRanges = rdd.asInstanceOf[HasOffsetRanges].offsetRanges
      rdd
    }

    val objectDstream: DStream[BaseTrademark] = inputGetOffsetDstream.map { record =>
      val jsonStr: String = record.value()
      val obj: BaseTrademark = JSON.parseObject(jsonStr, classOf[BaseTrademark])
      obj
    }

    objectDstream.foreachRDD{rdd=>
      import org.apache.phoenix.spark._
      rdd.saveToPhoenix("GMALL1122_BASE_TRADEMARK",Seq("ID", "TM_NAME"  )
        ,new Configuration,Some("hadoop1,hadoop2,hadoop3:2181"))

      OffsetManager.saveOffset(groupId, topic, offsetRanges)
    }

    ssc.start()
    ssc.awaitTermination()

  }

}
